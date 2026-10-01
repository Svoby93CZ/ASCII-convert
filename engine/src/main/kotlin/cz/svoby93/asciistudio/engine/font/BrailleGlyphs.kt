package cz.svoby93.asciistudio.engine.font

import cz.svoby93.asciistudio.engine.Braille
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TrueType glyphs for the Braille patterns, for fonts that have none (JetBrains Mono does not).
 * The dots sit where the app draws them: a 2 × 4 grid on a cell as high as two advances, which is
 * the cell shape of Braille art, with round dots of [Braille.DOT_SIZE] of the dot pitch. Every
 * pattern is a composite of one dot glyph, which keeps the font small.
 */
class BrailleGlyphs(private val advanceWidth: Int, ascender: Int, descender: Int) : ExtraGlyphs {

    private val pitch = advanceWidth / 2f
    private val radius = (pitch * Braille.DOT_SIZE / 2f).roundToInt()

    // A line of Braille art is two advances high, and the line box centres the font's ascender
    // and descender in it, like the renderer centres the text in a cell.
    private val baseline = (advanceWidth * 2f - (ascender - descender)) / 2f + ascender

    /** One dot with its bottom left corner at the origin; the patterns place it. */
    override val parts: List<GlyphRecord> = listOf(GlyphRecord(dotData(), advanceWidth, leftSideBearing = 0))

    override fun glyph(codePoint: Int, partGlyph: (Int) -> Int): GlyphRecord? {
        if (codePoint > Char.MAX_VALUE.code || !Braille.isPattern(codePoint.toChar())) return null
        val pattern = codePoint.toChar()
        // Where each dot glyph goes: its corner, one radius left of and below the dot's centre.
        val corners = (0 until Braille.DOTS_Y).flatMap { dotY ->
            (0 until Braille.DOTS_X).filter { dotX -> Braille.isRaised(pattern, dotX, dotY) }.map { dotX ->
                val centreX = ((dotX + 0.5f) * pitch).roundToInt()
                val centreY = (baseline - (dotY + 0.5f) * pitch).roundToInt()
                centreX - radius to centreY - radius
            }
        }
        if (corners.isEmpty()) return GlyphRecord(ByteArray(0), advanceWidth, leftSideBearing = 0)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(-1) // composite
            out.writeShort(corners.minOf { it.first })
            out.writeShort(corners.minOf { it.second })
            out.writeShort(corners.maxOf { it.first } + 2 * radius)
            out.writeShort(corners.maxOf { it.second } + 2 * radius)
            corners.forEachIndexed { index, (x, y) ->
                val more = if (index < corners.lastIndex) Component.MORE_COMPONENTS else 0
                out.writeShort(Component.ARG_1_AND_2_ARE_WORDS or Component.ARGS_ARE_XY_VALUES or more)
                out.writeShort(partGlyph(0))
                out.writeShort(x)
                out.writeShort(y)
            }
        }
        return GlyphRecord(bytes.toByteArray(), advanceWidth, leftSideBearing = corners.minOf { it.first })
    }

    /**
     * A circle of eight quadratic arcs, clockwise like outer TrueType contours: on-curve points
     * every 45°, controls between them where the tangents meet. It is off by 0.3 % at most.
     */
    private fun dotData(): ByteArray {
        val controlRadius = radius / cos(PI / SEGMENTS)
        val points = (0 until SEGMENTS).flatMap { segment ->
            val angle = PI / 2 - segment * 2 * PI / SEGMENTS
            val control = angle - PI / SEGMENTS
            listOf(
                Triple(radius + (radius * cos(angle)).roundToInt(), radius + (radius * sin(angle)).roundToInt(), true),
                Triple(
                    radius + (controlRadius * cos(control)).roundToInt(),
                    radius + (controlRadius * sin(control)).roundToInt(),
                    false,
                ),
            )
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(1) // one contour
            out.writeShort(points.minOf { it.first })
            out.writeShort(points.minOf { it.second })
            out.writeShort(points.maxOf { it.first })
            out.writeShort(points.maxOf { it.second })
            out.writeShort(points.size - 1)
            out.writeShort(0) // no instructions
            points.forEach { out.writeByte(if (it.third) ON_CURVE else 0) }
            var x = 0
            for (point in points) {
                out.writeShort(point.first - x)
                x = point.first
            }
            var y = 0
            for (point in points) {
                out.writeShort(point.second - y)
                y = point.second
            }
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val SEGMENTS = 8

        /** Braille glyphs that match the advance and the line of [font]. */
        fun forFont(font: TrueTypeFont): BrailleGlyphs {
            val cell = font.glyphOf('M'.code)?.let(font::advanceWidth) ?: (font.unitsPerEm * 3 / 5)
            return BrailleGlyphs(cell, font.ascender, font.descender)
        }
    }
}
