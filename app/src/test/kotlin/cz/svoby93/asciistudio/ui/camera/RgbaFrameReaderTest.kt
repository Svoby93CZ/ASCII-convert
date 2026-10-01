package cz.svoby93.asciistudio.ui.camera

import cz.svoby93.asciistudio.engine.PixelImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RgbaFrameReaderTest {

    /**
     * The frame
     * ```
     * a b c
     * d e f
     * ```
     * where every letter is a pixel whose red channel is its letter.
     */
    private val frame = frameOf(width = 3, height = 2) { x, y -> pixel(red = 'a'.code + y * 3 + x) }

    @Test
    fun `upright frames keep their pixels`() {
        assertLetters(listOf("abc", "def"), read(frame, rotationDegrees = 0))
    }

    @Test
    fun `frames turn clockwise by their rotation`() {
        assertLetters(listOf("da", "eb", "fc"), read(frame, rotationDegrees = 90))
        assertLetters(listOf("fed", "cba"), read(frame, rotationDegrees = 180))
        assertLetters(listOf("cf", "be", "ad"), read(frame, rotationDegrees = 270))
    }

    @Test
    fun `selfies are mirrored after turning upright`() {
        assertLetters(listOf("cba", "fed"), read(frame, rotationDegrees = 0, mirror = true))
        assertLetters(listOf("ad", "be", "cf"), read(frame, rotationDegrees = 90, mirror = true))
        assertLetters(listOf("fc", "eb", "da"), read(frame, rotationDegrees = 270, mirror = true))
    }

    @Test
    fun `RGBA bytes become ARGB pixels`() {
        val bytes = ByteBuffer.wrap(byteArrayOf(0x12, 0x34, 0x56, 0x78))

        val image = RgbaFrameReader().read(bytes, rowStride = 4, pixelStride = 4, width = 1, height = 1)

        assertEquals(0x78123456, image[0, 0])
    }

    @Test
    fun `padding after rows and pixels and outside the crop rectangle is skipped`() {
        // 3 × 2 pixels of 8 bytes each, rows of 40 bytes, of which the reader takes 2 × 1 at (1, 1).
        val bytes = ByteBuffer.allocate(80)
        for (i in 0 until 80) bytes.put(i, 0x7F)
        for (y in 0 until 2) {
            for (x in 0 until 3) {
                bytes.putInt(y * 40 + x * 8, pixel(red = 10 * y + x))
            }
        }

        val reader = RgbaFrameReader()
        val image = reader.read(bytes, rowStride = 40, pixelStride = 8, left = 1, top = 1, width = 2, height = 1)

        assertEquals(listOf(11, 12), redOf(image))
    }

    @Test
    fun `large frames are averaged by blocks`() {
        val large = frameOf(width = 4, height = 2) { x, _ -> pixel(red = if (x < 2) 10 else 31, green = x * 10) }

        val image = read(large, rotationDegrees = 0, maxSize = 2)

        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertArrayEquals(intArrayOf(argb(10, 5), argb(31, 25)), image.pixels.copyOf(2))
    }

    @Test
    fun `a stream of frames reuses one array`() {
        val reader = RgbaFrameReader()
        val first = reader.read(frame, rotationDegrees = 90)
        val second = reader.read(frame, rotationDegrees = 0)

        assertSame(first.pixels, second.pixels)
        assertLetters(listOf("abc", "def"), second)
    }

    private fun read(frame: Frame, rotationDegrees: Int, mirror: Boolean = false, maxSize: Int = 100): PixelImage =
        RgbaFrameReader().read(frame, rotationDegrees, mirror, maxSize)

    private fun RgbaFrameReader.read(
        frame: Frame,
        rotationDegrees: Int,
        mirror: Boolean = false,
        maxSize: Int = 100,
    ): PixelImage = read(
        frame.bytes,
        rowStride = frame.width * 4,
        pixelStride = 4,
        left = 0,
        top = 0,
        width = frame.width,
        height = frame.height,
        rotationDegrees = rotationDegrees,
        mirror = mirror,
        maxSize = maxSize,
    )

    /** Reads an upright frame of the back camera. */
    private fun RgbaFrameReader.read(
        bytes: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        left: Int = 0,
        top: Int = 0,
        width: Int,
        height: Int,
    ): PixelImage = read(bytes, rowStride, pixelStride, left, top, width, height, 0, mirror = false, maxSize = 100)

    private class Frame(val bytes: ByteBuffer, val width: Int, val height: Int)

    /** A tightly packed RGBA frame. */
    private fun frameOf(width: Int, height: Int, rgba: (x: Int, y: Int) -> Int): Frame {
        val bytes = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.BIG_ENDIAN)
        for (y in 0 until height) {
            for (x in 0 until width) bytes.putInt((y * width + x) * 4, rgba(x, y))
        }
        // The camera's buffer may come in any byte order; the reader must not depend on it.
        return Frame(bytes.order(ByteOrder.LITTLE_ENDIAN), width, height)
    }

    /** The RGBA bytes of an opaque pixel, read as a big-endian integer. */
    private fun pixel(red: Int, green: Int = 0, blue: Int = 0): Int =
        (red shl 24) or (green shl 16) or (blue shl 8) or 0xFF

    private fun argb(red: Int, green: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8)

    private fun redOf(image: PixelImage): List<Int> =
        (0 until image.height).flatMap { y -> (0 until image.width).map { x -> (image[x, y] shr 16) and 0xFF } }

    private fun assertLetters(expected: List<String>, image: PixelImage) {
        val rows = (0 until image.height).map { y ->
            (0 until image.width).joinToString("") { x -> ((image[x, y] shr 16) and 0xFF).toChar().toString() }
        }
        assertEquals(expected, rows)
    }
}
