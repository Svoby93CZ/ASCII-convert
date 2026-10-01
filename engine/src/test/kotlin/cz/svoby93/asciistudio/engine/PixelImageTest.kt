package cz.svoby93.asciistudio.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PixelImageTest {

    @Test
    fun `a thumbnail keeps the proportions and averages the colours`() {
        // Columns two pixels wide, black and white in turn.
        val image = TestImages.of(120, 60) { x, _ -> if (x / 2 % 2 == 0) TestImages.BLACK else TestImages.WHITE }

        val thumbnail = image.thumbnail(30)

        assertEquals(30, thumbnail.width)
        assertEquals(15, thumbnail.height)
        // Every pixel of the thumbnail covers two black and two white columns.
        val gray = (thumbnail[7, 3] shr 16) and 0xFF
        assertTrue(gray in 126..129, "gray $gray")
        assertEquals(0xFF, thumbnail[7, 3] ushr 24)
    }

    @Test
    fun `a thumbnail of a small image is a copy`() {
        val image = TestImages.solid(10, 8, TestImages.WHITE)

        val thumbnail = image.thumbnail(100)

        assertEquals(10, thumbnail.width)
        assertEquals(8, thumbnail.height)
        assertNotSame(image.pixels, thumbnail.pixels)
        assertEquals(TestImages.WHITE, thumbnail[3, 3])
    }
}
