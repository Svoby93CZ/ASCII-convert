package cz.svoby93.asciistudio.render

import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import cz.svoby93.asciistudio.data.Frame
import cz.svoby93.asciistudio.engine.AsciiArt
import java.io.FileDescriptor
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Records art as an H.264 video without sound. Every frame is drawn by an [AsciiRenderer] on the
 * input surface of the encoder, with the hardware canvas, and a [MediaMuxer] writes the encoded
 * frames into an MP4 file. Frames take the time they are drawn at, so the video keeps the pace of
 * the camera. Not thread-safe: all calls come from the camera's analysis thread.
 */
class ArtRecorder(typeface: Typeface, private val output: FileDescriptor) {

    private val renderer = AsciiRenderer(typeface)
    private val buffer = MediaCodec.BufferInfo()
    private lateinit var encoder: MediaCodec
    private lateinit var surface: Surface
    private lateinit var muxer: MediaMuxer
    private lateinit var frame: Frame
    private var track = -1

    /** What the encoder makes, once [start] has prepared it; shown in the developer mode. */
    lateinit var info: Info
        private set

    /** Prepares a video shaped like [art] with a margin, as large as the encoder allows. */
    fun start(art: AsciiArt) {
        val margin = renderer.cellWidth * MARGIN_CELLS
        val contentWidth = renderer.width(art) + 2 * margin
        val contentHeight = renderer.height(art) + 2 * margin
        try {
            encoder = MediaCodec.createEncoderByType(MIME)
            val capabilities = encoder.codecInfo.getCapabilitiesForType(MIME).videoCapabilities
                ?: throw IOException("The encoder makes no video")
            val alignment = maxOf(2, capabilities.widthAlignment, capabilities.heightAlignment)
            val (width, height) = SHORT_SIDES
                .map { side -> videoFrameSize(contentWidth, contentHeight, side, MAX_LONG_SIDE, alignment) }
                .firstOrNull { (width, height) -> capabilities.isSizeSupported(width, height) }
                ?: throw IOException("The encoder takes no size for this art")
            val bitRate = capabilities.bitrateRange.clamp((width * height * FRAME_RATE * BITS_PER_PIXEL).toInt())
            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_SECONDS)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val pixelMargin = (margin * width / contentWidth).roundToInt()
            frame = Frame(width, height, pixelMargin, pixelMargin)
            val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                encoder.codecInfo.isHardwareAccelerated
            } else {
                null
            }
            info = Info(encoder.name, hardware, width, height, bitRate)
        } catch (error: Exception) {
            release()
            throw error
        }
    }

    /** Adds [art] as the next frame; art of another shape than the first is fitted into the frame. */
    fun write(art: AsciiArt, style: ArtStyle) {
        val canvas = surface.lockHardwareCanvas()
        try {
            canvas.drawColor(style.background)
            val placement = frame.place(renderer.width(art), renderer.height(art))
            canvas.translate(placement.left, placement.top)
            canvas.scale(placement.scale, placement.scale)
            renderer.draw(canvas, art, style)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        drain(endOfStream = false)
    }

    /** Ends the video; `false` when not a single frame came out of the encoder, so there is no video. */
    fun finish(): Boolean = try {
        encoder.signalEndOfInputStream()
        drain(endOfStream = true)
        if (track >= 0) muxer.stop()
        track >= 0
    } finally {
        release()
    }

    /** Gives up the video; the caller deletes the file. */
    fun abort() = release()

    /** Moves what the encoder has finished into the file; at the end of the stream, waits for all of it. */
    private fun drain(endOfStream: Boolean) {
        val deadline = SystemClock.uptimeMillis() + END_TIMEOUT_MS
        while (true) {
            val index = encoder.dequeueOutputBuffer(buffer, if (endOfStream) DEQUEUE_TIMEOUT_US else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || SystemClock.uptimeMillis() > deadline) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val data = encoder.getOutputBuffer(index)
                    // The codec configuration reaches the file with the output format instead.
                    val config = buffer.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (data != null && !config && buffer.size > 0 && track >= 0) {
                        data.position(buffer.offset)
                        data.limit(buffer.offset + buffer.size)
                        muxer.writeSampleData(track, data, buffer)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun release() {
        // Parts that start() did not get to are not initialised; their release fails harmlessly.
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { surface.release() }
        runCatching { muxer.release() }
    }

    /**
     * The encoder by its [name], whether it runs in [hardware] (unknown before Android 10), and
     * the size and bit rate of the video it makes.
     */
    class Info(val name: String, val hardware: Boolean?, val width: Int, val height: Int, val bitRate: Int)

    private companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val MARGIN_CELLS = 2f

        /** Full HD across, or smaller sizes for encoders that cannot do that. */
        val SHORT_SIDES = listOf(1080, 720, 480)
        const val MAX_LONG_SIDE = 1920
        const val FRAME_RATE = 30
        const val KEY_FRAME_SECONDS = 1

        /** Enough for sharp glyphs; still pictures cost little with H.264. */
        const val BITS_PER_PIXEL = 0.12f
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val END_TIMEOUT_MS = 3_000L
    }
}
