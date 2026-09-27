package cz.svoby93.asciistudio.engine

/** Which glyphs the art is built from. */
sealed interface GlyphSet {
    /** Classic ASCII art: one glyph per cell, chosen by tone from [ramp]. */
    data class Ramp(val ramp: CharRamp) : GlyphSet

    /** Unicode Braille patterns: every cell holds a 2×4 grid of dots, doubling the resolution. */
    data object Braille : GlyphSet
}

enum class Dithering {
    NONE,
    FLOYD_STEINBERG,
    ATKINSON,
    BAYER,
}

enum class EdgeMode {
    OFF,

    /** Outline glyphs (`| / - \`) replace tone glyphs where the image has strong edges. */
    MIXED,

    /** Only the outlines are drawn, everything else stays blank. */
    ONLY,
}

/**
 * All parameters of a conversion.
 *
 * Tone semantics: by default bright pixels become dense glyphs, which suits light ink on a dark
 * background. Set [invert] for dark ink on light paper.
 */
data class AsciiOptions(
    val columns: Int = 100,
    val glyphs: GlyphSet = GlyphSet.Ramp(CharRamps.STANDARD),
    /** Width / height of one glyph cell of the target font; keeps the image proportions. */
    val cellAspect: Float = DEFAULT_CELL_ASPECT,
    /** -1..1, applied as a gamma curve so that black and white stay put. */
    val brightness: Float = 0f,
    /** -1..1, a linear contrast stretch around middle grey. */
    val contrast: Float = 0f,
    /** 0..1, strength of an unsharp mask that crisps up details before quantisation. */
    val sharpness: Float = 0f,
    /** Stretches the tonal range of the image to use the whole ramp. */
    val autoLevels: Boolean = true,
    val invert: Boolean = false,
    val dithering: Dithering = Dithering.NONE,
    val edgeMode: EdgeMode = EdgeMode.OFF,
    /** 0..1, higher values find fainter edges. */
    val edgeSensitivity: Float = 0.5f,
) {
    init {
        require(columns in 1..MAX_COLUMNS) { "columns must be in 1..$MAX_COLUMNS, was $columns" }
        require(cellAspect > 0f) { "cellAspect must be positive" }
    }

    companion object {
        const val MAX_COLUMNS = 1000

        /** JetBrains Mono: 600 units wide, 1320 units tall (ascent + descent). */
        const val DEFAULT_CELL_ASPECT = 600f / 1320f

        /** Braille cells are drawn twice as tall as wide so that the dot grid stays square. */
        const val BRAILLE_CELL_ASPECT = 0.5f
    }
}
