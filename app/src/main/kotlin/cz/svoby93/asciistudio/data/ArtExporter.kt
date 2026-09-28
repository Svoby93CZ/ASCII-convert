package cz.svoby93.asciistudio.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiExport
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.AsciiRenderer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TextFormat(val mimeType: String, val extension: String) {
    PLAIN("text/plain", "txt"),
    HTML("text/html", "html"),
    ANSI("text/plain", "ans"),
}

/** Renders, saves and shares finished art. */
class ArtExporter(
    private val context: Context,
    private val typeface: Typeface,
) {
    fun text(art: AsciiArt, style: ArtStyle, format: TextFormat): String = when (format) {
        TextFormat.PLAIN -> art.toText()
        TextFormat.HTML -> AsciiExport.toHtml(
            art,
            background = style.background,
            foreground = style.foreground,
            colored = style.colored,
            title = context.getString(R.string.app_name),
        )
        TextFormat.ANSI -> AsciiExport.toAnsi(art, style.foreground, style.colored)
    }

    fun suggestedFileName(format: TextFormat): String = "${baseFileName()}.${format.extension}"

    fun copyToClipboard(art: AsciiArt) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), art.toText()))
    }

    fun shareTextIntent(art: AsciiArt): Intent = chooser(
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, art.toText()),
    )

    suspend fun shareImageIntent(art: AsciiArt, style: ArtStyle): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
        // Only the latest shared image is kept around.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "${baseFileName()}.png")
        val bitmap = renderBitmap(art, style)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        chooser(
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { it.clipData = ClipData.newRawUri(null, uri) },
        )
    }

    /** Saves a PNG into Pictures/ASCII Studio. Before Android 10 the caller must hold storage permission. */
    suspend fun saveToPictures(art: AsciiArt, style: ArtStyle) = withContext(Dispatchers.IO) {
        val bitmap = renderBitmap(art, style)
        try {
            val name = "${baseFileName()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(bitmap, name)
            } else {
                saveToPublicDirectory(bitmap, name)
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun writeDocument(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    /** A small JPEG of the art for the gallery, [PREVIEW_SIZE] pixels on its longer side at most. */
    fun writePreview(art: AsciiArt, style: ArtStyle, stream: OutputStream) {
        val bitmap = renderBitmap(art, style, maxSide = PREVIEW_SIZE)
        try {
            val written = bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, stream)
            if (!written) throw IOException("JPEG encoding failed")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Renders the art into a bitmap with a small margin, capped at [MAX_PIXELS] and at [maxSide]
     * pixels on the longer side.
     */
    fun renderBitmap(art: AsciiArt, style: ArtStyle, maxSide: Float = Float.MAX_VALUE): Bitmap {
        val renderer = AsciiRenderer(typeface)
        val margin = renderer.cellWidth * MARGIN_CELLS
        val contentWidth = renderer.width(art) + 2 * margin
        val contentHeight = renderer.height(art) + 2 * margin
        var scale = TARGET_CELL_WIDTH_PX / renderer.cellWidth
        val pixels = contentWidth * contentHeight * scale * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        scale = min(scale, maxSide / max(contentWidth, contentHeight))
        val bitmap = Bitmap.createBitmap(
            floor(contentWidth * scale).toInt().coerceAtLeast(1),
            floor(contentHeight * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(style.background)
        canvas.scale(scale, scale)
        canvas.translate(margin, margin)
        renderer.draw(canvas, art, style)
        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(bitmap: Bitmap, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the image")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.writePng(it) } ?: throw IOException("Cannot open $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: IOException) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION") // The only way to reach the shared Pictures folder before Android 10.
    private fun saveToPublicDirectory(bitmap: Bitmap, name: String) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val file = File(directory, name)
        file.outputStream().use { bitmap.writePng(it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/png"), null)
    }

    private fun Bitmap.writePng(stream: OutputStream) {
        if (!compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException("PNG encoding failed")
    }

    private fun chooser(intent: Intent): Intent =
        Intent.createChooser(intent, context.getString(R.string.chooser_share))

    private fun baseFileName(): String =
        "ascii-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    private companion object {
        const val ALBUM = "ASCII Studio"
        const val SHARED_DIRECTORY = "shared"
        const val TARGET_CELL_WIDTH_PX = 14f
        const val MARGIN_CELLS = 2f
        const val MAX_PIXELS = 8_000_000f
        const val PREVIEW_SIZE = 720f
        const val PREVIEW_QUALITY = 88
    }
}
