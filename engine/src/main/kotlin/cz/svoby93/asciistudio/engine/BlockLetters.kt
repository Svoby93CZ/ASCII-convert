package cz.svoby93.asciistudio.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A word in pixel letters made of solid blocks, turned in 3D and lit like the [Donut]: points on
 * the faces of the blocks are projected with perspective, the nearest point wins every cell, and
 * the glyph and the colour (from [shade] in [frame]) say how much its face looks at the light.
 *
 * The letters have 3 × 5 pixels that are taller than wide, so that a word of five letters fills
 * the width of a donut frame and every pixel row still covers about two rows of cells. Only the
 * letters of the author's nickname are drawn so far.
 */
class BlockLetters(
    word: String,
    val columns: Int = 60,
    val rows: Int = 26,
    private val cellAspect: Float = AsciiOptions.DEFAULT_CELL_ASPECT,
) {
    /** Position and normal of every point on the faces, six floats each, in widths of the word. */
    private val points: FloatArray
    private val depth = FloatArray(columns * rows)

    init {
        require(word.isNotEmpty() && word.all { it in GLYPHS }) { "No pixel letters for $word" }
        val pixels = HashSet<Long>()
        var x = 0
        for (letter in word) {
            GLYPHS.getValue(letter).forEachIndexed { y, line ->
                line.forEachIndexed { dx, mark -> if (mark == '#') pixels += key(x + dx, y) }
            }
            x += LETTER_WIDTH + LETTER_GAP
        }
        points = faces(pixels, width = x - LETTER_GAP)
    }

    /**
     * The word turned by [yaw] around the upright axis, then tipped by [pitch] around the
     * horizontal one. A positive yaw turns the end of the word towards the viewer, a negative
     * pitch its top, which shows the tops of the blocks.
     */
    fun frame(yaw: Float, pitch: Float, shade: (Float) -> Int): AsciiArt {
        val chars = CharArray(columns * rows) { ' ' }
        val colors = IntArray(columns * rows) { OPAQUE_BLACK }
        depth.fill(0f)
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val scaleX = FIT * columns * DISTANCE
        val scaleY = scaleX * cellAspect
        var i = 0
        while (i < points.size) {
            val x = points[i] * cosYaw + points[i + 2] * sinYaw
            val z = -points[i] * sinYaw + points[i + 2] * cosYaw
            val y = points[i + 1] * cosPitch - z * sinPitch
            val oneOverZ = 1f / (points[i + 1] * sinPitch + z * cosPitch + DISTANCE)
            val column = (columns / 2f + scaleX * oneOverZ * x).toInt()
            val row = (rows / 2f - scaleY * oneOverZ * y).toInt()
            if (column in 0 until columns && row in 0 until rows) {
                val cell = row * columns + column
                if (oneOverZ > depth[cell]) {
                    depth[cell] = oneOverZ
                    val normalX = points[i + 3] * cosYaw + points[i + 5] * sinYaw
                    val normalZ = -points[i + 3] * sinYaw + points[i + 5] * cosYaw
                    val normalY = points[i + 4] * cosPitch - normalZ * sinPitch
                    val normalDepth = points[i + 4] * sinPitch + normalZ * cosPitch
                    // Faces turned away from the light stay as the faintest glyph, so letters stay solid.
                    val light = (normalX * LIGHT_X + normalY * LIGHT_Y + normalDepth * LIGHT_Z).coerceIn(0f, 1f)
                    chars[cell] = SHADES[(light * SHADES.length).toInt().coerceAtMost(SHADES.length - 1)]
                    colors[cell] = shade(light)
                }
            }
            i += FLOATS_PER_POINT
        }
        return AsciiArt(columns, rows, chars, colors, cellAspect, isBraille = false)
    }

    private companion object {
        val GLYPHS = mapOf(
            'S' to listOf(".##", "#..", ".#.", "..#", "##."),
            'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
            'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."),
            'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
            'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
        )
        const val LETTER_WIDTH = 3
        const val LETTER_HEIGHT = 5
        const val LETTER_GAP = 1

        /** Height of a pixel and depth of the blocks, in widths of a pixel. */
        const val PIXEL_HEIGHT = 1.6f
        const val BLOCK_DEPTH = 1f

        /** Points per width of a pixel along every face: enough to leave no holes in the cells. */
        const val SAMPLES = 10

        /** Face on, the word fills this much of the width. */
        const val FIT = 0.9f
        const val DISTANCE = 5f
        const val FLOATS_PER_POINT = 6
        const val SHADES = ".,-~:;=!*#\$@"

        /** From the upper left, mostly from the front, so that the letters stay bright as they turn. */
        val LIGHT_X: Float
        val LIGHT_Y: Float
        val LIGHT_Z: Float

        init {
            val x = -0.35f
            val y = 0.45f
            val z = -1f
            val length = sqrt(x * x + y * y + z * z)
            LIGHT_X = x / length
            LIGHT_Y = y / length
            LIGHT_Z = z / length
        }

        fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

        /**
         * Points on the faces of the blocks that no neighbouring block covers, centred on the
         * origin and scaled to a word one unit wide. The front faces look at the viewer, at -z.
         */
        fun faces(pixels: Set<Long>, width: Int): FloatArray {
            val steps = SAMPLES
            val depthSteps = (BLOCK_DEPTH * SAMPLES).toInt().coerceAtLeast(1)
            // At most the front, the back and four sides of every block.
            val out = FloatArray(pixels.size * (2 * steps * steps + 4 * steps * depthSteps) * FLOATS_PER_POINT)
            var size = 0
            val unit = 1f / width
            val halfHeight = LETTER_HEIGHT * PIXEL_HEIGHT / 2f
            fun add(x: Float, y: Float, z: Float, normalX: Float, normalY: Float, normalZ: Float) {
                out[size++] = (x - width / 2f) * unit
                out[size++] = (halfHeight - y * PIXEL_HEIGHT) * unit
                out[size++] = (z - BLOCK_DEPTH / 2f) * unit
                out[size++] = normalX
                out[size++] = normalY
                out[size++] = normalZ
            }
            for (pixel in pixels) {
                val px = (pixel shr 32).toInt()
                val py = pixel.toInt()
                for (a in 0 until steps) for (b in 0 until steps) {
                    val u = px + (a + 0.5f) / steps
                    val v = py + (b + 0.5f) / steps
                    add(u, v, 0f, 0f, 0f, -1f)
                    add(u, v, BLOCK_DEPTH, 0f, 0f, 1f)
                }
                for (a in 0 until steps) for (b in 0 until depthSteps) {
                    val along = (a + 0.5f) / steps
                    val z = (b + 0.5f) / depthSteps * BLOCK_DEPTH
                    if (key(px - 1, py) !in pixels) add(px.toFloat(), py + along, z, -1f, 0f, 0f)
                    if (key(px + 1, py) !in pixels) add(px + 1f, py + along, z, 1f, 0f, 0f)
                    if (key(px, py - 1) !in pixels) add(px + along, py.toFloat(), z, 0f, 1f, 0f)
                    if (key(px, py + 1) !in pixels) add(px + along, py + 1f, z, 0f, -1f, 0f)
                }
            }
            return out.copyOf(size)
        }
    }
}
