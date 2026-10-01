package cz.svoby93.asciistudio.engine

/**
 * An image averaged into the grids that a conversion needs, made by [AsciiConverter.sample]: the
 * cells, or the dots of Braille, and for outlines on glyph ramps a grid twice as fine. Options that
 * only change tones, dithering or the sensitivity of outlines [fit] the same samples.
 */
class Samples internal constructor(
    /** Size of the image the samples were taken from. */
    internal val imageWidth: Int,
    internal val imageHeight: Int,
    val columns: Int,
    val rows: Int,
    internal val cellAspect: Float,
    internal val braille: Boolean,
    /** The cells, or the dots of Braille. */
    internal val grid: SampleGrid,
    /** Twice as fine as the cells, for outlines on glyph ramps. */
    internal val fine: SampleGrid?,
) {
    /** Whether a conversion with [options] can use these samples. */
    fun fit(options: AsciiOptions): Boolean {
        if (options.columns != columns || (options.glyphs == GlyphSet.Braille) != braille) return false
        // Braille always uses square dots and samples nothing extra for outlines.
        return braille || options.cellAspect == cellAspect && (options.edgeMode != EdgeMode.OFF) == (fine != null)
    }
}
