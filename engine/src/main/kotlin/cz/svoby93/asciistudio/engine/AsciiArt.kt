package cz.svoby93.asciistudio.engine

/**
 * The result of a conversion: a grid of glyphs plus the average source colour of every cell.
 *
 * @property colors opaque `0xFFRRGGBB` colour per cell, row-major, used for colour rendering.
 * @property cellAspect width / height of one cell the art was laid out for.
 */
class AsciiArt(
    val columns: Int,
    val rows: Int,
    val chars: CharArray,
    val colors: IntArray,
    val cellAspect: Float,
    val isBraille: Boolean,
) {
    init {
        require(columns > 0 && rows > 0) { "Art must not be empty" }
        require(chars.size == columns * rows && colors.size == columns * rows) {
            "Buffers must hold columns × rows cells"
        }
    }

    operator fun get(column: Int, row: Int): Char = chars[row * columns + column]

    fun colorAt(column: Int, row: Int): Int = colors[row * columns + column]

    fun line(row: Int): String = String(chars, row * columns, columns)

    /** Plain text with one line per row; trailing blanks are dropped to keep the text compact. */
    fun toText(): String = buildString(chars.size + rows) {
        for (row in 0 until rows) {
            if (row > 0) append('\n')
            append(line(row).trimEnd(' '))
        }
    }
}
