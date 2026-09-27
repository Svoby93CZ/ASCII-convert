package cz.svoby93.asciistudio.engine

/** Helpers for the Unicode Braille Patterns block (U+2800..U+28FF): 2 × 4 dots per glyph. */
object Braille {
    const val BLANK: Char = '⠀'
    const val DOTS_X: Int = 2
    const val DOTS_Y: Int = 4

    /** Dot bit masks indexed by `[row][column]`, as defined by the Unicode standard. */
    private val BITS = arrayOf(
        intArrayOf(0x01, 0x08),
        intArrayOf(0x02, 0x10),
        intArrayOf(0x04, 0x20),
        intArrayOf(0x40, 0x80),
    )

    fun bit(dotX: Int, dotY: Int): Int = BITS[dotY][dotX]

    fun isRaised(glyph: Char, dotX: Int, dotY: Int): Boolean =
        ((glyph.code - BLANK.code) and bit(dotX, dotY)) != 0
}
