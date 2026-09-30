package cz.svoby93.asciistudio.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colours for art that takes its colours from the photo, drawn on [paper].
 *
 * The density of the glyphs already carries the brightness of the photo. Glyphs in the average colour
 * of their cell would carry it a second time: dark areas get sparse glyphs in dim colours, which all
 * but vanish. So a glyph takes only the hue and saturation of its cell, as bright as possible on dark
 * paper and as dark as needed on light paper, and always readable on the paper.
 *
 * Tiles work the other way round: a muted version of the cell colour behind every glyph brings back
 * the colours and the brightness of the photo, like a mosaic.
 */
class PhotoColors(paper: Int) {
    private val paper = paper or OPAQUE_BLACK
    private val lightPaper: Boolean

    /** The lowest luminance of a glyph on dark paper, or the highest one on light paper. */
    private val glyphLuminance: Float

    init {
        val luminance = luminance(LINEAR[red(this.paper)], LINEAR[green(this.paper)], LINEAR[blue(this.paper)])
        lightPaper = luminance > MID_LUMINANCE
        glyphLuminance = if (lightPaper) {
            ((luminance + 0.05f) / LIGHT_PAPER_CONTRAST - 0.05f).coerceAtLeast(MIN_LIGHT_PAPER_GLYPH)
        } else {
            DARK_PAPER_CONTRAST * (luminance + 0.05f) - 0.05f
        }
    }

    /** The colour of a glyph in a cell whose average colour is [cell]; always opaque. */
    fun glyph(cell: Int): Int {
        val linearRed = LINEAR[red(cell)]
        val linearGreen = LINEAR[green(cell)]
        val linearBlue = LINEAR[blue(cell)]
        val top = max(linearRed, max(linearGreen, linearBlue))
        // The colour at full intensity: the strongest channel becomes 1, which keeps hue and
        // saturation. The hue of dark cells is mostly noise, so they fade to neutral.
        var r = 1f
        var g = 1f
        var b = 1f
        if (top > 0f) {
            val brightest = max(red(cell), max(green(cell), blue(cell))) / 255f
            val hue = smoothStep(brightest / DARK_HUE_LIMIT)
            r += (linearRed / top - 1f) * hue
            g += (linearGreen / top - 1f) * hue
            b += (linearBlue / top - 1f) * hue
        }
        val luminance = luminance(r, g, b)
        if (lightPaper) {
            // Darker until readable; scaling linear light keeps the hue.
            val scale = min(1f, glyphLuminance / luminance)
            r *= scale
            g *= scale
            b *= scale
        } else if (luminance < glyphLuminance) {
            // Lighter until readable: deep blues and reds are too dark on dark paper otherwise.
            val lift = (glyphLuminance - luminance) / (1f - luminance)
            r += (1f - r) * lift
            g += (1f - g) * lift
            b += (1f - b) * lift
        }
        return OPAQUE_BLACK or (encode(r) shl 16) or (encode(g) shl 8) or encode(b)
    }

    /**
     * The colour of the tile behind the glyph of a cell whose average colour is [cell]; always opaque.
     * Where the picture is transparent, the tile keeps the colour of the paper.
     */
    fun tile(cell: Int): Int {
        val amount = TILE_STRENGTH * (cell ushr 24) / 255f
        return OPAQUE_BLACK or
            (mix(red(paper), red(cell), amount) shl 16) or
            (mix(green(paper), green(cell), amount) shl 8) or
            mix(blue(paper), blue(cell), amount)
    }

    /** [glyph] for every cell of [art], row-major. */
    fun glyphs(art: AsciiArt): IntArray = IntArray(art.colors.size) { glyph(art.colors[it]) }

    /** [tile] for every cell of [art], row-major. */
    fun tiles(art: AsciiArt): IntArray = IntArray(art.colors.size) { tile(art.colors[it]) }

    private companion object {
        /** Glyphs on dark paper get at least the contrast that WCAG asks of text (AA). */
        const val DARK_PAPER_CONTRAST = 4.5f

        /** Dark strokes on light paper look thinner than light strokes on dark paper, so more (AAA). */
        const val LIGHT_PAPER_CONTRAST = 7f

        /** Papers brighter than this contrast more with black than with white. */
        const val MID_LUMINANCE = 0.179f

        /** Keeps glyphs from turning black on papers too dark for [LIGHT_PAPER_CONTRAST]. */
        const val MIN_LIGHT_PAPER_GLYPH = 0.01f

        /** Cells whose strongest channel is darker than this (sRGB) lose their hue gradually. */
        const val DARK_HUE_LIMIT = 0.25f

        /** How much of the cell colour a tile mixes into the paper. */
        const val TILE_STRENGTH = 0.3f

        const val ENCODE_STEPS = 4095

        /** sRGB channel value → linear light. */
        val LINEAR = FloatArray(256) { srgbToLinear(it / 255f) }

        /** Linear light in [ENCODE_STEPS] steps → sRGB channel value. */
        val ENCODED = IntArray(ENCODE_STEPS + 1) { (linearToSrgb(it / ENCODE_STEPS.toFloat()) * 255f).roundToInt() }

        fun encode(linear: Float): Int = ENCODED[(linear.coerceIn(0f, 1f) * ENCODE_STEPS + 0.5f).toInt()]

        /** Relative luminance as defined by WCAG, from linear channels. */
        fun luminance(red: Float, green: Float, blue: Float): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

        fun srgbToLinear(value: Float): Float =
            if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

        fun linearToSrgb(value: Float): Float =
            if (value <= 0.0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - 0.055f

        fun smoothStep(value: Float): Float {
            val t = value.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        fun mix(from: Int, to: Int, amount: Float): Int = (from + (to - from) * amount).roundToInt()

        fun red(color: Int): Int = (color shr 16) and 0xFF

        fun green(color: Int): Int = (color shr 8) and 0xFF

        fun blue(color: Int): Int = color and 0xFF
    }
}
