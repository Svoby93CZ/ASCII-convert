package cz.svoby93.asciistudio.engine

/**
 * Keeps the glyphs (or Braille dots) of the previous frame while their tone stays within [margin]
 * of a value that gives the same glyph, so that sensor noise does not flip glyphs whose tone lies
 * near the boundary of two glyphs. It needs a quantiser that treats every cell on its own and
 * gives a higher index for a higher value: no dithering or the Bayer matrix.
 */
internal class GlyphHold(private val margin: Float) {
    private var previous = IntArray(0)
    private var remembered = false
    private var width = 0
    private var height = 0
    private var levels: FloatArray? = null
    private var dithering: Dithering? = null

    /** Lets the next frame choose its glyphs freely, e.g. because the whole picture moves. */
    fun forget() {
        remembered = false
    }

    /** Replaces the new [indices] by the previous ones where the noise margin allows it. */
    fun apply(ink: FloatArray, indices: IntArray, width: Int, height: Int, levels: FloatArray, dithering: Dithering) {
        require(dithering == Dithering.NONE || dithering == Dithering.BAYER) { "Error diffusion cannot hold glyphs" }
        if (remembered && width == this.width && height == this.height && dithering == this.dithering &&
            levels.contentEquals(this.levels)
        ) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val last = previous[i]
                    if (last == indices[i]) continue
                    val lowest = pick(levels, dithering, ink[i] - margin, x, y)
                    val highest = pick(levels, dithering, ink[i] + margin, x, y)
                    if (last in lowest..highest) indices[i] = last
                }
            }
        }
        if (previous.size != indices.size) previous = IntArray(indices.size)
        indices.copyInto(previous)
        remembered = true
        this.width = width
        this.height = height
        this.levels = levels
        this.dithering = dithering
    }

    /** The glyph that the quantiser picks for [value] in the cell at ([x], [y]). */
    private fun pick(levels: FloatArray, dithering: Dithering, value: Float, x: Int, y: Int): Int =
        if (dithering == Dithering.BAYER) Quantizer.bayer(levels, value, x, y) else Quantizer.nearest(levels, value)
}
