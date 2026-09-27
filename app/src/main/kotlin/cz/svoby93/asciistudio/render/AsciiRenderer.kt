package cz.svoby93.asciistudio.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.ColorInt
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.Braille
import kotlin.math.ceil
import kotlin.math.floor

/** How to colour art: [colored] paints every glyph in the colour of its source pixels. */
data class ArtStyle(
    @ColorInt val background: Int,
    @ColorInt val foreground: Int,
    val colored: Boolean,
)

/**
 * Draws [AsciiArt] on an Android [Canvas] with the bundled monospace font.
 *
 * - Text rows are drawn with a single `drawText` call each.
 * - Photo colours come from a [BitmapShader] that holds one pixel per cell and is stretched over
 *   the grid with nearest-neighbour sampling, so every glyph gets its own colour without drawing
 *   glyphs one by one.
 * - Braille is drawn as real dots rather than with a font, which keeps it crisp and aligned.
 *
 * Instances cache data for the last drawn art and are therefore not thread-safe.
 */
class AsciiRenderer(typeface: Typeface) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = TEXT_SIZE
        fontFeatureSettings = NO_LIGATURES
        isFilterBitmap = false
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isFilterBitmap = false
    }
    private val backgroundPaint = Paint()
    private val shaderMatrix = Matrix()
    private val clipBounds = Rect()

    private val fontMetrics = textPaint.fontMetrics
    private val fontHeight = fontMetrics.descent - fontMetrics.ascent

    /** Width of one glyph cell in renderer units. */
    val cellWidth: Float = textPaint.measureText("M")

    /** Width / height of a text cell of the bundled font; the converter must use the same. */
    val textCellAspect: Float = cellWidth / fontHeight

    private var preparedArt: AsciiArt? = null
    private var colorShader: BitmapShader? = null
    private var dots: FloatArray = FloatArray(0)

    fun cellHeight(art: AsciiArt): Float = cellWidth / art.cellAspect

    fun width(art: AsciiArt): Float = art.columns * cellWidth

    fun height(art: AsciiArt): Float = art.rows * cellHeight(art)

    /** Draws [art] with its top-left corner at the canvas origin, in renderer units. */
    fun draw(canvas: Canvas, art: AsciiArt, style: ArtStyle, drawBackground: Boolean = false) {
        prepare(art)
        val cellHeight = cellHeight(art)
        if (drawBackground) {
            backgroundPaint.color = style.background
            canvas.drawRect(0f, 0f, width(art), height(art), backgroundPaint)
        }
        val paint = if (art.isBraille) dotPaint else textPaint
        if (style.colored) {
            paint.shader = shaderFor(art, cellHeight)
            paint.color = Color.WHITE
        } else {
            paint.shader = null
            paint.color = style.foreground
        }
        if (art.isBraille) {
            dotPaint.strokeWidth = cellWidth / Braille.DOTS_X * DOT_SIZE
            canvas.drawPoints(dots, dotPaint)
        } else {
            drawRows(canvas, art, cellHeight)
        }
    }

    private fun drawRows(canvas: Canvas, art: AsciiArt, cellHeight: Float) {
        // Only rows inside the clip are drawn, which keeps deep zoom on huge art smooth.
        var first = 0
        var last = art.rows - 1
        if (canvas.getClipBounds(clipBounds)) {
            first = floor(clipBounds.top / cellHeight).toInt().coerceIn(0, art.rows - 1)
            last = ceil(clipBounds.bottom / cellHeight).toInt().coerceIn(first, art.rows - 1)
        }
        val baseline = (cellHeight - fontHeight) / 2f - fontMetrics.ascent
        for (row in first..last) {
            canvas.drawText(art.chars, row * art.columns, art.columns, 0f, row * cellHeight + baseline, textPaint)
        }
    }

    private fun prepare(art: AsciiArt) {
        if (art === preparedArt) return
        preparedArt = art
        colorShader = null
        dots = if (art.isBraille) dotCentres(art) else FloatArray(0)
    }

    private fun shaderFor(art: AsciiArt, cellHeight: Float): Shader {
        val shader = colorShader ?: BitmapShader(
            Bitmap.createBitmap(art.colors, art.columns, art.rows, Bitmap.Config.ARGB_8888),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).also { shader ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.filterMode = BitmapShader.FILTER_MODE_NEAREST
            }
            colorShader = shader
        }
        shaderMatrix.setScale(cellWidth, cellHeight)
        shader.setLocalMatrix(shaderMatrix)
        return shader
    }

    private fun dotCentres(art: AsciiArt): FloatArray {
        val cellHeight = cellHeight(art)
        val pitchX = cellWidth / Braille.DOTS_X
        val pitchY = cellHeight / Braille.DOTS_Y
        var count = 0
        for (glyph in art.chars) count += Integer.bitCount(glyph.code - Braille.BLANK.code)
        val points = FloatArray(count * 2)
        var index = 0
        for (row in 0 until art.rows) {
            for (column in 0 until art.columns) {
                val glyph = art[column, row]
                if (glyph == Braille.BLANK) continue
                for (dy in 0 until Braille.DOTS_Y) {
                    for (dx in 0 until Braille.DOTS_X) {
                        if (!Braille.isRaised(glyph, dx, dy)) continue
                        points[index++] = column * cellWidth + (dx + 0.5f) * pitchX
                        points[index++] = row * cellHeight + (dy + 0.5f) * pitchY
                    }
                }
            }
        }
        return points
    }

    companion object {
        /** Glyph size in renderer units; views and exports scale the canvas as needed. */
        const val TEXT_SIZE = 40f

        /** JetBrains Mono ligatures would merge glyph pairs such as `==` or `->`. */
        const val NO_LIGATURES = "'calt' 0, 'liga' 0"

        /** Dot diameter relative to the dot pitch. */
        private const val DOT_SIZE = 0.78f
    }
}
