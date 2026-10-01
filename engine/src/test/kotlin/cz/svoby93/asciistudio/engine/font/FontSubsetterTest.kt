package cz.svoby93.asciistudio.engine.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontSubsetterTest {

    private val font = TestFonts.app

    private fun subset(text: String, extra: ExtraGlyphs? = null): ByteArray =
        FontSubsetter.subset(font, text.map { it.code }.toSet(), FAMILY, extra)

    @Test
    fun `a subset has the glyphs of the text with the outlines and advances of the font`() {
        val text = "Hello █"
        val subset = TrueTypeFont(subset(text))

        assertEquals(1 + text.toSet().size, subset.glyphCount)
        for (char in text) {
            val original = font.glyphOf(char.code)!!
            val copy = subset.glyphOf(char.code)!!
            assertEquals(TestFonts.points(font.outline(original)), TestFonts.points(subset.outline(copy)), "$char")
            assertEquals(font.advanceWidth(original), subset.advanceWidth(copy))
            assertEquals(font.leftSideBearing(original), subset.leftSideBearing(copy))
        }
    }

    @Test
    fun `composite glyphs bring their parts along`() {
        val subset = TrueTypeFont(subset("Č"))

        // .notdef, C, the caron and Č itself.
        assertEquals(4, subset.glyphCount)
        assertEquals(
            TestFonts.points(font.outline(font.glyphOf('Č'.code)!!)),
            TestFonts.points(subset.outline(subset.glyphOf('Č'.code)!!)),
        )
    }

    @Test
    fun `characters the font lacks are left out`() {
        val subset = TrueTypeFont(subset("a☺"))

        assertNull(subset.glyphOf('☺'.code))
        assertEquals(2, subset.glyphCount)
    }

    @Test
    fun `the font gets a name of its own and keeps the notices of the source`() {
        val subset = TrueTypeFont(subset("a"))

        assertEquals(FAMILY, subset.name(1))
        assertEquals(font.name(0), subset.name(0))
        assertEquals(font.name(13), subset.name(13))
        assertEquals(font.name(14), subset.name(14))
    }

    @Test
    fun `hinting, ligatures and kerning are left out`() {
        val bytes = subset("fi->")
        val subset = TrueTypeFont(bytes)

        for (tag in listOf("fpgm", "prep", "cvt ", "GSUB", "GPOS", "GDEF")) assertNull(subset.table(tag), tag)
        val maxp = subset.table("maxp")!!
        assertEquals(0, maxp.u16(26), "maxSizeOfInstructions")
        // In JetBrains Mono, i is a composite of a dotless i and a dot.
        for (char in "fi->") {
            val glyph = subset.glyphData(subset.glyphOf(char.code)!!)
            val contours = glyph.i16(0)
            if (contours >= 0) {
                assertEquals(0, glyph.u16(GLYPH_HEADER_SIZE + 2 * contours), "instructions of $char")
            } else {
                val parts = components(glyph)
                assertTrue(parts.none { it.flags and Component.WE_HAVE_INSTRUCTIONS != 0 }, "instructions of $char")
                assertTrue(glyph.size - parts.last().end < 4, "bytes after the parts of $char")
            }
        }
    }

    @Test
    fun `table and font checksums are valid`() {
        val bytes = subset("ASCII Studio ░▒▓█")
        val tables = bytes.u16(4)
        var headOffset = -1
        for (index in 0 until tables) {
            val record = 12 + 16 * index
            val tag = String(bytes, record, 4, Charsets.US_ASCII)
            val offset = bytes.i32(record + 8)
            val table = bytes.copyOfRange(offset, offset + bytes.i32(record + 12))
            if (tag == "head") {
                headOffset = offset
                table.fill(0, 8, 12) // the adjustment is not part of the checksum of head
            }
            assertEquals(bytes.i32(record + 4).toLong() and 0xFFFFFFFFL, checksum(table), tag)
        }
        assertTrue(headOffset > 0)
        assertEquals(0xB1B0AFBAL, checksum(bytes))
    }

    @Test
    fun `braille glyphs fill in for a font without them`() {
        val subset = TrueTypeFont(subset("⠀⠁⣿", BrailleGlyphs.forFont(font)))

        // .notdef, the dot every pattern is made of, and the three patterns.
        assertEquals(5, subset.glyphCount)
        for (char in "⠀⠁⣿") assertEquals(600, subset.advanceWidth(subset.glyphOf(char.code)!!))
        assertEquals(0, subset.outline(subset.glyphOf('⠀'.code)!!).size)
        assertEquals(1, subset.outline(subset.glyphOf('⠁'.code)!!).size)
        assertEquals(8, subset.outline(subset.glyphOf('⣿'.code)!!).size)
    }

    private fun checksum(data: ByteArray): Long {
        var sum = 0L
        for (index in data.indices step 4) {
            var word = 0L
            for (byte in 0 until 4) word = (word shl 8) or (data.getOrNull(index + byte)?.toLong()?.and(0xFF) ?: 0L)
            sum = (sum + word) and 0xFFFFFFFFL
        }
        return sum
    }

    private companion object {
        const val FAMILY = "Test Mono"
    }
}
