package cz.svoby93.asciistudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageFormatTest {

    private fun frameOf(format: ImageFormat, artWidth: Float = 100f, artHeight: Float = 100f): Frame =
        format.frame(artWidth, artHeight, screenWidth = 1080, screenHeight = 2400)!!

    @Test
    fun `posts and stories have the sizes the apps show uncropped`() {
        assertEquals(1080 to 1080, frameOf(ImageFormat.SQUARE).let { it.width to it.height })
        assertEquals(1080 to 1350, frameOf(ImageFormat.PORTRAIT).let { it.width to it.height })
        assertEquals(1080 to 1920, frameOf(ImageFormat.STORY).let { it.width to it.height })
    }

    @Test
    fun `stories keep the art clear of the buttons at the top and bottom`() {
        assertEquals(250, frameOf(ImageFormat.STORY).topMargin)
    }

    @Test
    fun `a wallpaper fills the screen upright`() {
        val frame = ImageFormat.WALLPAPER.frame(100f, 100f, screenWidth = 2400, screenHeight = 1080)!!

        assertEquals(1080 to 2400, frame.width to frame.height)
        assertEquals(288, frame.topMargin)
    }

    @Test
    fun `print uses A4 at 300 dpi and turns on its side for wide art`() {
        val tall = frameOf(ImageFormat.PRINT_A4, artWidth = 100f, artHeight = 200f)
        val wide = frameOf(ImageFormat.PRINT_A4, artWidth = 200f, artHeight = 100f)

        assertEquals(2480 to 3508, tall.width to tall.height)
        assertEquals(3508 to 2480, wide.width to wide.height)
        assertEquals(118, tall.sideMargin)
    }

    @Test
    fun `the original size has no frame`() {
        assertNull(ImageFormat.ORIGINAL.frame(100f, 100f, 1080, 2400))
    }

    @Test
    fun `wide art fills the width and sits in the middle`() {
        val placement = Frame(1080, 1350, 64, 64).place(100f, 50f)

        assertEquals(9.52f, placement.scale, 0.001f)
        assertEquals(64f, placement.left, 0.001f)
        assertEquals(64f + (1222f - 476f) / 2f, placement.top, 0.001f)
    }

    @Test
    fun `tall art fills the height and sits in the middle`() {
        val placement = Frame(1080, 1080, 64, 64).place(50f, 100f)

        assertEquals(9.52f, placement.scale, 0.001f)
        assertEquals(64f + (952f - 476f) / 2f, placement.left, 0.001f)
        assertEquals(64f, placement.top, 0.001f)
    }
}
