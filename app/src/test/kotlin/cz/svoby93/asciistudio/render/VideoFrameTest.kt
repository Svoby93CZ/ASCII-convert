package cz.svoby93.asciistudio.render

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameTest {

    @Test
    fun `upright art keeps its shape with the short side across`() {
        assertEquals(1080 to 1440, videoFrameSize(75f, 100f, shortSide = 1080, maxLongSide = 1920, alignment = 2))
    }

    @Test
    fun `wide art turns the frame on its side`() {
        assertEquals(1440 to 1080, videoFrameSize(100f, 75f, shortSide = 1080, maxLongSide = 1920, alignment = 2))
    }

    @Test
    fun `sides are rounded down to what the encoder aligns to`() {
        assertEquals(1072 to 1440, videoFrameSize(75f, 100f, shortSide = 1080, maxLongSide = 1920, alignment = 16))
    }

    @Test
    fun `very tall art is limited by the long side`() {
        assertEquals(640 to 1920, videoFrameSize(100f, 300f, shortSide = 1080, maxLongSide = 1920, alignment = 2))
    }
}
