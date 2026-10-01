package cz.svoby93.asciistudio.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiExport
import cz.svoby93.asciistudio.engine.font.TrueTypeFont
import cz.svoby93.asciistudio.render.ArtRecorder
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.AsciiRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files the editor saves where the user picks. */
enum class FileFormat(val mimeType: String, val extension: String) {
    PLAIN("text/plain", "txt"),
    HTML("text/html", "html"),
    ANSI("text/plain", "ans"),
    SVG("image/svg+xml", "svg"),
    PDF("application/pdf", "pdf"),
}

/** Renders, saves and shares finished art. */
class ArtExporter(
    private val context: Context,
    private val typeface: Typeface,
) {
    /** The app's font, for files that carry the glyphs they use; read when first needed. */
    private val font: TrueTypeFont by lazy { readFont() }

    // A font resource is a file like a raw one, and openRawResource reads any such file.
    @SuppressLint("ResourceType")
    private fun readFont(): TrueTypeFont =
        context.resources.openRawResource(R.font.jetbrains_mono_regular).use { TrueTypeFont(it.readBytes()) }

    /** The content of a text [format]; a PDF is written by [writeFile] instead. */
    fun text(art: AsciiArt, style: ArtStyle, format: FileFormat): String = when (format) {
        FileFormat.PLAIN -> art.toText()
        FileFormat.HTML -> AsciiExport.toHtml(
            art,
            background = style.background,
            foreground = style.foreground,
            glyphColors = style.glyphColorsOf(art),
            tileColors = style.tileColorsOf(art),
            title = context.getString(R.string.app_name),
            font = font,
        )
        FileFormat.ANSI -> AsciiExport.toAnsi(
            art,
            foreground = style.foreground,
            glyphColors = style.glyphColorsOf(art),
            tileColors = style.tileColorsOf(art),
        )
        FileFormat.SVG -> AsciiExport.toSvg(
            art,
            background = style.background,
            foreground = style.foreground,
            font = font,
            glyphColors = style.glyphColorsOf(art),
            tileColors = style.tileColorsOf(art),
            title = context.getString(R.string.app_name),
            pixelsPerCell = TARGET_CELL_WIDTH_PX,
        )
        FileFormat.PDF -> throw IllegalArgumentException("A PDF is no text")
    }

    fun suggestedFileName(format: FileFormat): String = "${baseFileName()}.${format.extension}"

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    }

    fun shareTextIntent(art: AsciiArt): Intent = chooser(
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, art.toText()),
    )

    suspend fun shareImageIntent(art: AsciiArt, style: ArtStyle, format: ImageFormat): Intent =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
            // Only the latest shared image is kept around.
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, "${baseFileName()}.png")
            val bitmap = renderBitmap(art, style, format)
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
    suspend fun saveToPictures(art: AsciiArt, style: ArtStyle, format: ImageFormat) = withContext(Dispatchers.IO) {
        val bitmap = renderBitmap(art, style, format)
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

    /** Writes [art] as [format] to [uri], which the user picked. */
    suspend fun writeFile(uri: Uri, art: AsciiArt, style: ArtStyle, format: FileFormat) {
        val content: ByteArray = withContext(Dispatchers.Default) {
            if (format == FileFormat.PDF) pdf(art, style) else text(art, style, format).toByteArray(Charsets.UTF_8)
        }
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
            stream.use { it.write(content) }
        }
    }

    /**
     * One A4 page with margins of 10 mm, on its side for wide art. The glyphs stay text in the
     * app's font and the dots and tiles vector shapes, so the art prints sharp at any size.
     */
    private fun pdf(art: AsciiArt, style: ArtStyle): ByteArray {
        val renderer = AsciiRenderer(typeface)
        val width = renderer.width(art)
        val height = renderer.height(art)
        val (pageWidth, pageHeight) =
            if (width > height) A4_LONG_POINTS to A4_SHORT_POINTS else A4_SHORT_POINTS to A4_LONG_POINTS
        val placement = Frame(pageWidth, pageHeight, A4_MARGIN_POINTS, A4_MARGIN_POINTS).place(width, height)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            page.canvas.apply {
                drawColor(style.background)
                translate(placement.left, placement.top)
                scale(placement.scale, placement.scale)
                renderer.drawPlain(this, art, style)
            }
            document.finishPage(page)
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
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

    /** The size in pixels of the picture of [art] in [format], as [renderBitmap] makes it. */
    fun imageSize(art: AsciiArt, format: ImageFormat): Pair<Int, Int> {
        val renderer = AsciiRenderer(typeface)
        val frame = frameOf(renderer, art, format)
        if (frame != null) return frame.width to frame.height
        val (width, height, _) = originalSize(renderer, art, Float.MAX_VALUE)
        return width to height
    }

    /**
     * Renders the art into a bitmap: in the frame of [format], or for [ImageFormat.ORIGINAL] with
     * a small margin, capped at [MAX_PIXELS] and at [maxSide] pixels on the longer side.
     */
    fun renderBitmap(
        art: AsciiArt,
        style: ArtStyle,
        format: ImageFormat = ImageFormat.ORIGINAL,
        maxSide: Float = Float.MAX_VALUE,
    ): Bitmap {
        val renderer = AsciiRenderer(typeface)
        val frame = frameOf(renderer, art, format)
        val (width, height, placement) = if (frame != null) {
            Triple(frame.width, frame.height, frame.place(renderer.width(art), renderer.height(art)))
        } else {
            val (width, height, scale) = originalSize(renderer, art, maxSide)
            val margin = renderer.cellWidth * MARGIN_CELLS * scale
            Triple(width, height, Placement(scale, margin, margin))
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(style.background)
        canvas.translate(placement.left, placement.top)
        canvas.scale(placement.scale, placement.scale)
        renderer.draw(canvas, art, style)
        return bitmap
    }

    private fun frameOf(renderer: AsciiRenderer, art: AsciiArt, format: ImageFormat): Frame? {
        if (format == ImageFormat.ORIGINAL) return null
        val (screenWidth, screenHeight) = screenSize()
        return format.frame(renderer.width(art), renderer.height(art), screenWidth, screenHeight)
    }

    /** Width, height and scale of the art at [TARGET_CELL_WIDTH_PX] a cell, within the limits. */
    private fun originalSize(renderer: AsciiRenderer, art: AsciiArt, maxSide: Float): Triple<Int, Int, Float> {
        val margin = renderer.cellWidth * MARGIN_CELLS
        val contentWidth = renderer.width(art) + 2 * margin
        val contentHeight = renderer.height(art) + 2 * margin
        var scale = TARGET_CELL_WIDTH_PX / renderer.cellWidth
        val pixels = contentWidth * contentHeight * scale * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        scale = min(scale, maxSide / max(contentWidth, contentHeight))
        return Triple(
            floor(contentWidth * scale).toInt().coerceAtLeast(1),
            floor(contentHeight * scale).toInt().coerceAtLeast(1),
            scale,
        )
    }

    /** The full screen of the phone in pixels, with the system bars, in its natural orientation. */
    private fun screenSize(): Pair<Int, Int> {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.mode
        if (mode != null) return mode.physicalWidth to mode.physicalHeight
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * A new video in Movies/ASCII Studio for a recording, hidden from galleries until it is
     * published. Before Android 10 the caller must hold the storage permission.
     */
    fun createVideo(): VideoFile {
        val name = "${baseFileName()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the video")
            val descriptor = try {
                resolver.openFileDescriptor(uri, "rw") ?: throw IOException("Cannot open $uri")
            } catch (error: IOException) {
                resolver.delete(uri, null, null)
                throw error
            }
            return VideoFile(
                descriptor,
                onPublish = {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                },
                onDiscard = { resolver.delete(uri, null, null) },
            )
        }
        val directory = File(publicDirectory(Environment.DIRECTORY_MOVIES), ALBUM)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val file = File(directory, name)
        val mode = ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE
        return VideoFile(
            ParcelFileDescriptor.open(file, mode),
            onPublish = { scan(file, VIDEO_MIME) },
            onDiscard = { file.delete() },
        )
    }

    /** A recorder that writes into [video]. */
    fun recorder(video: VideoFile): ArtRecorder = ArtRecorder(typeface, video.descriptor.fileDescriptor)

    fun shareVideoIntent(uri: Uri): Intent = chooser(
        Intent(Intent.ACTION_SEND)
            .setType(VIDEO_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri(null, uri) },
    )

    /** Adds [file] to the media library and returns its content URI, or `null` if that takes too long. */
    private fun scan(file: File, mimeType: String): Uri? {
        val scanned = ArrayBlockingQueue<Uri>(1)
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mimeType)) { _, uri ->
            if (uri != null) scanned.offer(uri)
        }
        return scanned.poll(SCAN_TIMEOUT_S, TimeUnit.SECONDS)
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

    private fun saveToPublicDirectory(bitmap: Bitmap, name: String) {
        val directory = File(publicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val file = File(directory, name)
        file.outputStream().use { bitmap.writePng(it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/png"), null)
    }

    @Suppress("DEPRECATION") // The only way to reach the shared folders before Android 10.
    private fun publicDirectory(type: String): File = Environment.getExternalStoragePublicDirectory(type)

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
        const val VIDEO_MIME = "video/mp4"
        const val SCAN_TIMEOUT_S = 5L

        /** A4 in PostScript points of 1/72 inch, with margins of 10 mm. */
        const val A4_SHORT_POINTS = 595
        const val A4_LONG_POINTS = 842
        const val A4_MARGIN_POINTS = 28
    }
}

/** A video being written; galleries show it once it is published. */
class VideoFile(
    val descriptor: ParcelFileDescriptor,
    private val onPublish: () -> Uri?,
    private val onDiscard: () -> Unit,
) {
    /** Shows the finished video in galleries; returns its URI for sharing, when there is one. */
    fun publish(): Uri? {
        descriptor.close()
        return onPublish()
    }

    /** Deletes the unfinished video. */
    fun discard() {
        runCatching { descriptor.close() }
        onDiscard()
    }
}
