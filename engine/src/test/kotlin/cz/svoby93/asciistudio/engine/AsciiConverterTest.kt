package cz.svoby93.asciistudio.engine

import cz.svoby93.asciistudio.engine.TestImages.BLACK
import cz.svoby93.asciistudio.engine.TestImages.TRANSPARENT
import cz.svoby93.asciistudio.engine.TestImages.WHITE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsciiConverterTest {

    private val standard = AsciiOptions(columns = 20, glyphs = GlyphSet.Ramp(CharRamps.STANDARD), cellAspect = 0.5f)

    @Test
    fun `rows keep the image proportions for the cell shape`() {
        val art = AsciiConverter.convert(TestImages.solid(200, 100, WHITE), standard.copy(columns = 100))

        assertEquals(100, art.columns)
        assertEquals(25, art.rows)
        assertEquals(0.5f, art.cellAspect)
        assertFalse(art.isBraille)
    }

    @Test
    fun `white becomes the densest glyph and black stays blank`() {
        val white = AsciiConverter.convert(TestImages.solid(40, 40, WHITE), standard)
        val black = AsciiConverter.convert(TestImages.solid(40, 40, BLACK), standard)

        assertTrue(white.chars.all { it == '@' })
        assertTrue(black.chars.all { it == ' ' })
    }

    @Test
    fun `invert swaps paper and ink`() {
        val art = AsciiConverter.convert(TestImages.solid(40, 40, BLACK), standard.copy(invert = true))

        assertTrue(art.chars.all { it == '@' })
    }

    @Test
    fun `transparent pixels never receive ink`() {
        val art = AsciiConverter.convert(TestImages.solid(40, 40, TRANSPARENT), standard.copy(invert = true))

        assertTrue(art.chars.all { it == ' ' })
    }

    @Test
    fun `a horizontal gradient produces monotonically denser glyphs`() {
        val image = TestImages.of(256, 64) { x, _ -> TestImages.gray(x) }
        val art = AsciiConverter.convert(image, standard.copy(columns = 64, autoLevels = false))
        val ramp = CharRamps.STANDARD.chars

        val row = art.line(art.rows / 2)
        assertEquals(' ', row.first())
        assertEquals('@', row.last())
        for (i in 1 until row.length) {
            assertTrue(ramp.indexOf(row[i]) >= ramp.indexOf(row[i - 1]), "Glyphs got lighter at column $i: $row")
        }
    }

    @Test
    fun `auto levels stretch a low contrast image over the whole ramp`() {
        val image = TestImages.of(200, 50) { x, _ -> TestImages.gray(100 + x / 4) }

        val stretched = AsciiConverter.convert(image, standard.copy(autoLevels = true)).line(0)
        val flat = AsciiConverter.convert(image, standard.copy(autoLevels = false)).line(0)

        assertEquals(' ', stretched.first())
        assertEquals('@', stretched.last())
        assertTrue(flat.toSet().size < stretched.toSet().size)
    }

    @Test
    fun `cell colours are the average source colour`() {
        val red = 0xFFFF0000.toInt()
        val art = AsciiConverter.convert(TestImages.solid(30, 30, red), standard.copy(columns = 5))

        assertTrue(art.colors.all { it == red })
    }

    @Test
    fun `floyd steinberg keeps the average tone of mid grey`() {
        val ramp = CharRamp.uniform(" #")
        val options = standard.copy(
            columns = 60,
            glyphs = GlyphSet.Ramp(ramp),
            autoLevels = false,
            dithering = Dithering.FLOYD_STEINBERG,
        )
        val art = AsciiConverter.convert(TestImages.solid(120, 240, TestImages.gray(128)), options)

        val inked = art.chars.count { it == '#' }.toFloat() / art.chars.size
        assertEquals(0.5f, inked, 0.05f)
    }

    @Test
    fun `all dithering modes produce glyphs from the ramp`() {
        val image = TestImages.of(97, 61) { x, y -> TestImages.gray((x * 7 + y * 3) % 256) }
        for (dithering in Dithering.entries) {
            val art = AsciiConverter.convert(image, standard.copy(columns = 33, dithering = dithering))
            assertTrue(art.chars.all { it in CharRamps.STANDARD.chars }, "Unexpected glyph with $dithering")
        }
    }

    @Test
    fun `braille maps every dot to its unicode bit`() {
        val options = standard.copy(columns = 1, glyphs = GlyphSet.Braille, autoLevels = false)
        val expected = mapOf(
            (0 to 0) to 0x01, (0 to 1) to 0x02, (0 to 2) to 0x04, (1 to 0) to 0x08,
            (1 to 1) to 0x10, (1 to 2) to 0x20, (0 to 3) to 0x40, (1 to 3) to 0x80,
        )
        for ((dot, bit) in expected) {
            val image = TestImages.of(2, 4) { x, y -> if (x == dot.first && y == dot.second) WHITE else BLACK }
            val art = AsciiConverter.convert(image, options)

            assertEquals(1, art.rows)
            assertTrue(art.isBraille)
            assertEquals((Braille.BLANK.code + bit).toChar(), art[0, 0], "Dot $dot")
            assertTrue(Braille.isRaised(art[0, 0], dot.first, dot.second))
        }
    }

    @Test
    fun `braille rows keep square dots`() {
        val art = AsciiConverter.convert(
            TestImages.solid(400, 200, WHITE),
            standard.copy(columns = 50, glyphs = GlyphSet.Braille),
        )

        // 50 cells = 100 dots wide, so 50 dots = 12.5 cells high.
        assertEquals(13, art.rows)
        assertTrue(art.chars.all { it == '⣿' })
    }

    @Test
    fun `vertical edges are outlined with bars`() {
        val image = TestImages.of(120, 120) { x, _ -> if (x < 60) BLACK else WHITE }
        val art = AsciiConverter.convert(image, standard.copy(columns = 30, edgeMode = EdgeMode.ONLY))

        val glyphs = art.chars.filter { it != ' ' }.toSet()
        assertEquals(setOf('|'), glyphs)
        // Non-maximum suppression keeps the outline one cell thin.
        for (row in 0 until art.rows) {
            assertEquals(1, art.line(row).count { it == '|' }, "Row $row: ${art.line(row)}")
        }
    }

    @Test
    fun `horizontal edges are outlined with dashes`() {
        val image = TestImages.of(120, 120) { _, y -> if (y < 60) BLACK else WHITE }
        val art = AsciiConverter.convert(image, standard.copy(columns = 30, edgeMode = EdgeMode.ONLY))

        assertEquals(setOf('-'), art.chars.filter { it != ' ' }.toSet())
    }

    @Test
    fun `rising diagonals are outlined with slashes`() {
        // White above the line from bottom-left to top-right.
        val image = TestImages.of(200, 200) { x, y -> if (x + y < 200) WHITE else BLACK }
        val art = AsciiConverter.convert(
            image,
            standard.copy(columns = 40, cellAspect = 1f, edgeMode = EdgeMode.ONLY),
        )

        val glyphs = art.chars.filter { it != ' ' }
        assertTrue(glyphs.isNotEmpty())
        assertTrue(glyphs.count { it == '/' } > glyphs.size * 0.8, "Expected mostly '/', got $glyphs")
    }

    @Test
    fun `falling diagonals are outlined with backslashes`() {
        val image = TestImages.of(200, 200) { x, y -> if (x > y) WHITE else BLACK }
        val art = AsciiConverter.convert(
            image,
            standard.copy(columns = 40, cellAspect = 1f, edgeMode = EdgeMode.ONLY),
        )

        val glyphs = art.chars.filter { it != ' ' }
        assertTrue(glyphs.count { it == '\\' } > glyphs.size * 0.8, "Expected mostly '\\', got $glyphs")
    }

    @Test
    fun `mixed edge mode keeps the tone glyphs away from edges`() {
        val image = TestImages.of(120, 120) { x, _ -> if (x < 60) BLACK else WHITE }
        val art = AsciiConverter.convert(image, standard.copy(columns = 30, edgeMode = EdgeMode.MIXED))

        val row = art.line(art.rows / 2)
        assertTrue('|' in row)
        assertTrue(row.endsWith("@"))
        assertTrue(row.startsWith(" "))
    }

    @Test
    fun `braille outlines draw thin lines`() {
        val image = TestImages.of(200, 200) { x, _ -> if (x < 100) BLACK else WHITE }
        val art = AsciiConverter.convert(
            image,
            standard.copy(columns = 50, glyphs = GlyphSet.Braille, edgeMode = EdgeMode.ONLY),
        )

        val raised = art.chars.sumOf { Integer.bitCount(it.code - Braille.BLANK.code) }
        val dotRows = art.rows * Braille.DOTS_Y
        // One dot per dot row, give or take the border rows.
        assertTrue(raised in dotRows - 2..dotRows * 2, "Unexpected number of raised dots: $raised")
    }

    @Test
    fun `plain text trims trailing blanks but keeps the rows`() {
        val image = TestImages.of(40, 20) { x, _ -> if (x < 20) WHITE else BLACK }
        val art = AsciiConverter.convert(image, standard.copy(columns = 4, cellAspect = 1f))

        assertEquals("@@\n@@", art.toText())
    }
}
