package cz.svoby93.asciistudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import cz.svoby93.asciistudio.engine.CharRamp
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Turns user-typed characters into a [CharRamp] by measuring how much ink every glyph really
 * puts on screen in the bundled font. Glyphs the font cannot draw at the monospace width are
 * dropped, because they would break the column alignment.
 */
class GlyphMeasurer(typeface: Typeface) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = MEASURE_TEXT_SIZE
        fontFeatureSettings = AsciiRenderer.NO_LIGATURES
    }
    private val advance = paint.measureText(" ")
    private val ascent = paint.fontMetrics.ascent
    private val cellWidth = ceil(advance).toInt()
    private val cellHeight = ceil(paint.fontMetrics.descent - ascent).toInt()
    private val cache = LruCache<String, Measured>(CACHE_SIZE)

    /** A ramp for [text], or `null` when it holds fewer than two usable glyphs. */
    @Synchronized
    fun rampFor(text: String): CharRamp? {
        cache.get(text)?.let { return it.ramp }
        val ramp = measure(usableGlyphs(text))
        cache.put(text, Measured(ramp))
        return ramp
    }

    /** Distinct glyphs of [text] that render with the monospace advance of the font. */
    @Synchronized
    fun usableGlyphs(text: String): String = text
        .filter { c ->
            !c.isISOControl() && !c.isSurrogate() && abs(paint.measureText(c.toString()) - advance) < WIDTH_TOLERANCE
        }
        .toSet()
        .joinToString("")

    private fun measure(glyphs: String): CharRamp? {
        if (glyphs.length < 2) return null
        val bitmap = Bitmap.createBitmap(cellWidth, cellHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(cellWidth * cellHeight)
        val coverage = FloatArray(glyphs.length) { index ->
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.drawText(glyphs, index, index + 1, 0f, -ascent, paint)
            bitmap.getPixels(pixels, 0, cellWidth, 0, 0, cellWidth, cellHeight)
            var ink = 0L
            for (pixel in pixels) ink += pixel ushr 24
            ink / (pixels.size * 255f)
        }
        bitmap.recycle()
        return CharRamp.of(glyphs, coverage)
    }

    private class Measured(val ramp: CharRamp?)

    private companion object {
        const val MEASURE_TEXT_SIZE = 64f
        const val WIDTH_TOLERANCE = 0.5f
        const val CACHE_SIZE = 16
    }
}
