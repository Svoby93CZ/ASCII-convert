package cz.svoby93.asciistudio.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import cz.svoby93.asciistudio.engine.PixelImage
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The picture being edited: the bitmap for display plus its pixels for the converter. */
class SourceImage(val bitmap: Bitmap) {
    val pixels: PixelImage = PixelImage(
        bitmap.width,
        bitmap.height,
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        },
    )

    /** Writes the picture compactly: PNG keeps transparency, JPEG keeps big photos small and quick to write. */
    fun writeTo(stream: OutputStream) {
        val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        if (!bitmap.compress(format, JPEG_QUALITY, stream)) throw IOException("Cannot encode the image")
    }

    private companion object {
        const val JPEG_QUALITY = 92
    }
}

/**
 * Loads pictures, keeps the one being edited in memory and mirrors it to private storage so that
 * editing can continue after the app process has been killed.
 */
class ImageRepository(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val workFile = File(File(context.noBackupFilesDir, "work"), "source.img")
    private val mutex = Mutex()
    private val current = MutableStateFlow<SourceImage?>(null)

    /** The image being edited, `null` until one is imported or restored. */
    val image: StateFlow<SourceImage?> = current.asStateFlow()

    /** Decodes [uri] (downscaled, upright, sRGB) and makes it the current image. */
    suspend fun importImage(uri: Uri): SourceImage = withContext(Dispatchers.IO) {
        mutex.withLock { replace(decode(uri)) }
    }

    /** Makes an already decoded bitmap (e.g. a camera frame) the current image. */
    suspend fun importBitmap(bitmap: Bitmap): SourceImage = withContext(Dispatchers.IO) {
        mutex.withLock { replace(bitmap.fitWithin(MAX_DIMENSION)) }
    }

    /** Returns the current image, reloading the saved copy after a process restart. */
    suspend fun restore(): SourceImage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            current.value ?: loadSavedCopy()?.let { SourceImage(it) }?.also { current.value = it }
        }
    }

    private fun replace(bitmap: Bitmap): SourceImage {
        val image = SourceImage(bitmap)
        current.value = image
        save(image)
        return image
    }

    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(uri) else decodeLegacy(uri)

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software pixels are required for getPixels(); sRGB keeps colours predictable.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val width = info.size.width
            val height = info.size.height
            val scale = MAX_DIMENSION.toFloat() / max(width, height)
            if (scale < 1f) {
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    /** Android 8.x: BitmapFactory does not apply EXIF orientation, so do it by hand. */
    private fun decodeLegacy(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) throw IOException("Unsupported image: $uri")

        var sampleSize = 1
        while (longest / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $uri")
        val orientation = open(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        val scale = min(1f, MAX_DIMENSION.toFloat() / max(decoded.width, decoded.height))
        matrix.postScale(scale, scale)
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private fun open(uri: Uri) = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private fun save(image: SourceImage) {
        try {
            workFile.parentFile?.mkdirs()
            val temp = File(workFile.parentFile, "source.tmp")
            temp.outputStream().use(image::writeTo)
            if (!temp.renameTo(workFile)) temp.delete()
        } catch (_: IOException) {
            // Without the copy editing still works, it just cannot survive a process restart.
        }
    }

    private fun loadSavedCopy(): Bitmap? {
        if (!workFile.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(workFile.path, options)
    }

    private fun Bitmap.fitWithin(maxDimension: Int): Bitmap {
        val scale = maxDimension.toFloat() / max(width, height)
        if (scale >= 1f) return this
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        /** Plenty for 300 columns of text and 600 Braille dots, while keeping memory in check. */
        const val MAX_DIMENSION = 1600
    }
}
