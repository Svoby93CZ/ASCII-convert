package cz.svoby93.asciistudio.ui.camera

import cz.svoby93.asciistudio.engine.PixelImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Reads RGBA camera frames, as `ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888` delivers them, into
 * upright images: turned by the frame's rotation, mirrored for the front camera and, when a frame
 * is much larger than needed, averaged down by whole blocks of pixels.
 *
 * The pixels go straight from the camera buffer into one array that is reused for every frame, so
 * a stream of frames allocates nothing. The image returned by [read] is therefore only valid until
 * the next call; frames must be read one after another.
 */
class RgbaFrameReader {
    private var pixels = IntArray(0)

    /**
     * @param frame the RGBA bytes of the frame; [rowStride] and [pixelStride] are in bytes.
     * @param left the crop rectangle of the frame, together with [top], [width] and [height].
     * @param rotationDegrees how far the frame must turn clockwise to be upright: 0, 90, 180 or 270.
     * @param mirror flips the upright image horizontally, like a mirror.
     * @param maxSize the longer side needs no more pixels than this; larger frames average blocks.
     */
    fun read(
        frame: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        mirror: Boolean,
        maxSize: Int,
    ): PixelImage {
        require(rotationDegrees in ROTATIONS) { "Unsupported rotation $rotationDegrees" }
        val block = max(1, max(width, height) / maxSize)
        val blocksWide = width / block
        val blocksHigh = height / block
        val turned = rotationDegrees == 90 || rotationDegrees == 270
        val outWidth = if (turned) blocksHigh else blocksWide
        val outHeight = if (turned) blocksWide else blocksHigh
        if (pixels.size < outWidth * outHeight) pixels = IntArray(outWidth * outHeight)

        // Absolute reads in a fixed byte order, whatever the camera set on its buffer.
        val bytes = frame.duplicate().order(ByteOrder.BIG_ENDIAN)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                // Undo the mirror, then the rotation, to find the block of the frame for this pixel.
                val ux = if (mirror) outWidth - 1 - x else x
                val bx: Int
                val by: Int
                when (rotationDegrees) {
                    90 -> {
                        bx = y
                        by = blocksHigh - 1 - ux
                    }
                    180 -> {
                        bx = blocksWide - 1 - ux
                        by = blocksHigh - 1 - y
                    }
                    270 -> {
                        bx = blocksWide - 1 - y
                        by = ux
                    }
                    else -> {
                        bx = ux
                        by = y
                    }
                }
                val offset = (top + by * block) * rowStride + (left + bx * block) * pixelStride
                pixels[y * outWidth + x] = if (block == 1) {
                    argb(bytes.getInt(offset))
                } else {
                    average(bytes, offset, block, rowStride, pixelStride)
                }
            }
        }
        return PixelImage(outWidth, outHeight, pixels)
    }

    /** The average colour of a [block] × [block] square of pixels starting at [offset]. */
    private fun average(bytes: ByteBuffer, offset: Int, block: Int, rowStride: Int, pixelStride: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        var a = 0
        for (dy in 0 until block) {
            var index = offset + dy * rowStride
            repeat(block) {
                val rgba = bytes.getInt(index)
                r += rgba ushr 24
                g += (rgba shr 16) and 0xFF
                b += (rgba shr 8) and 0xFF
                a += rgba and 0xFF
                index += pixelStride
            }
        }
        // A multiplication, because an integer division per channel would cost more than the reads.
        val share = 1f / (block * block)
        return (channel(a, share) shl 24) or (channel(r, share) shl 16) or (channel(g, share) shl 8) or channel(b, share)
    }

    private fun channel(sum: Int, share: Float): Int = (sum * share + 0.5f).toInt()

    /** RGBA as read in big-endian order to the ARGB of [PixelImage]. */
    private fun argb(rgba: Int): Int = Integer.rotateRight(rgba, 8)

    private companion object {
        val ROTATIONS = setOf(0, 90, 180, 270)
    }
}
