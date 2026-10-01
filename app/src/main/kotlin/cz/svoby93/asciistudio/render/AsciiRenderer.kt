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
import cz.svoby93.asciistudio.engine.PhotoColors
import kotlin.math.ceil
import kotlin.math.floor

/** Where the glyphs of art take their colour from. */
enum class GlyphColors {
    /** Every glyph in the foreground colour. */
    INK,

    /** The hue of the photo, readable on the background, see [PhotoColors]. */
    PHOTO,

    /** Exactly the colours stored in the art, for art that paints its own, like the donut. */
    ART,
}

/** How to colour art. [tiles] puts a muted colour of the photo behind every glyph, see [PhotoColors]. */
data class ArtStyle(
    @ColorInt val background: Int,
    @ColorInt val foreground: Int,
    val glyphColors: GlyphColors = GlyphColors.INK,
    val tiles: Boolean = false,
) {
    /** The colour of every glyph of [art], or `null` when all of them take the [foreground]. */
    fun glyphColorsOf(art: AsciiArt): IntArray? = when (glyphColors) {
        GlyphColors.INK -> null
        GlyphColors.PHOTO -> PhotoColors(background).glyphs(art)
        GlyphColors.ART -> art.colors
    }

    /** The colour of the tile behind every glyph of [art], or `null` without [tiles]. */
    fun tileColorsOf(art: AsciiArt): IntArray? = if (tiles) PhotoColors(background).tiles(art) else null
}

/**
 * Draws [AsciiArt] on an Android [Canvas] with the bundled monospace font.
 *
 * - Text rows are drawn with a single `drawText` call each.
 * - Glyph colours come from a [BitmapShader] that holds one pixel per cell and is stretched over
 *   the grid with nearest-neighbour sampling, so every glyph gets its own colour without drawing
 *   glyphs one by one. Colour tiles are a single rectangle with a shader of the same kind.
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
    private val tilePaint = Paint().apply { isFilterBitmap = false }
    private val plainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint()
    private val shaderMatrix = Matrix()
    private val clipBounds = Rect()
    private val glyphShader = CellShader()
    private val tileShader = CellShader()

    private val fontMetrics = textPaint.fontMetrics
    private val fontHeight = fontMetrics.descent - fontMetrics.ascent

    /** Width of one glyph cell in renderer units. */
    val cellWidth: Float = textPaint.measureText("M")

    /** Width / height of a text cell of the bundled font; the converter must use the same. */
    val textCellAspect: Float = cellWidth / fontHeight

    private var preparedArt: AsciiArt? = null
    private var dots: FloatArray = FloatArray(0)

    fun cellHeight(art: AsciiArt): Float = cellWidth / art.cellAspect

    fun width(art: AsciiArt): Float = art.columns * cellWidth

    fun height(art: AsciiArt): Float = art.rows * cellHeight(art)

    /** Draws [art] with its top-left corner at the canvas origin, in renderer units. */
    fun draw(canvas: Canvas, art: AsciiArt, style: ArtStyle, drawBackground: Boolean = false) {
        prepare(art)
        val cellHeight = cellHeight(art)
        shaderMatrix.setScale(cellWidth, cellHeight)
        if (drawBackground) {
            backgroundPaint.color = style.background
            canvas.drawRect(0f, 0f, width(art), height(art), backgroundPaint)
        }
        if (style.tiles) {
            tilePaint.shader = tileShader.of(art, style, shaderMatrix) { style.tileColorsOf(it) }
            canvas.drawRect(0f, 0f, width(art), height(art), tilePaint)
        }
        val paint = if (art.isBraille) dotPaint else textPaint
        if (style.glyphColors == GlyphColors.INK) {
            paint.shader = null
            paint.color = style.foreground
        } else {
            paint.shader = glyphShader.of(art, style, shaderMatrix) { style.glyphColorsOf(it) }
            paint.color = Color.WHITE
        }
        if (art.isBraille) {
            dotPaint.strokeWidth = cellWidth / Braille.DOTS_X * Braille.DOT_SIZE
            canvas.drawPoints(dots, dotPaint)
        } else {
            drawRows(canvas, art, cellHeight)
        }
    }

    /**
     * Draws [art] like [draw], but with plain colours instead of shaders: runs of glyphs of one
     * colour, tiles as rectangles and Braille dots as circles. A PDF keeps all of it as text and
     * vector shapes, where a shader would turn into an image.
     */
    fun drawPlain(canvas: Canvas, art: AsciiArt, style: ArtStyle) {
        val cellHeight = cellHeight(art)
        style.tileColorsOf(art)?.let { tiles ->
            plainPaint.style = Paint.Style.FILL
            // Every tile reaches a little under the next ones, which are drawn later, so that viewers
            // that smooth the edges of shapes show no seams between them.
            val overlap = cellWidth * TILE_OVERLAP
            forEachRun(art, tiles) { row, start, end, color ->
                plainPaint.color = color
                val right = end * cellWidth + if (end < art.columns) overlap else 0f
                val bottom = (row + 1) * cellHeight + if (row < art.rows - 1) overlap else 0f
                canvas.drawRect(start * cellWidth, row * cellHeight, right, bottom, plainPaint)
            }
        }
        val colors = style.glyphColorsOf(art)
        if (art.isBraille) {
            val pitchX = cellWidth / Braille.DOTS_X
            val pitchY = cellHeight / Braille.DOTS_Y
            val radius = pitchX * Braille.DOT_SIZE / 2f
            plainPaint.style = Paint.Style.FILL
            for (row in 0 until art.rows) {
                for (column in 0 until art.columns) {
                    val glyph = art[column, row]
                    if (glyph == Braille.BLANK) continue
                    plainPaint.color = colors?.get(row * art.columns + column) ?: style.foreground
                    for (dotY in 0 until Braille.DOTS_Y) {
                        for (dotX in 0 until Braille.DOTS_X) {
                            if (!Braille.isRaised(glyph, dotX, dotY)) continue
                            val x = column * cellWidth + (dotX + 0.5f) * pitchX
                            canvas.drawCircle(x, row * cellHeight + (dotY + 0.5f) * pitchY, radius, plainPaint)
                        }
                    }
                }
            }
            return
        }
        textPaint.shader = null
        val baseline = (cellHeight - fontHeight) / 2f - fontMetrics.ascent
        forEachRun(art, colors ?: IntArray(art.chars.size) { style.foreground }) { row, start, end, color ->
            textPaint.color = color
            val y = row * cellHeight + baseline
            canvas.drawText(art.chars, row * art.columns + start, end - start, start * cellWidth, y, textPaint)
        }
    }

    /** Calls [action] for every run of cells with the same colour in a row, as columns from start until end. */
    private inline fun forEachRun(
        art: AsciiArt,
        colors: IntArray,
        action: (row: Int, start: Int, end: Int, color: Int) -> Unit,
    ) {
        for (row in 0 until art.rows) {
            val offset = row * art.columns
            var start = 0
            while (start < art.columns) {
                var end = start + 1
                while (end < art.columns && colors[offset + end] == colors[offset + start]) end++
                action(row, start, end, colors[offset + start])
                start = end
            }
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
        dots = if (art.isBraille) dotCentres(art) else FloatArray(0)
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

        /** How far plain tiles reach under their neighbours, relative to the cell width. */
        private const val TILE_OVERLAP = 0.02f
    }
}

/**
 * A shader with one pixel per cell of the art, stretched over the grid with nearest-neighbour
 * sampling. It is rebuilt only for new art or when the colours of the style change, so a still
 * picture does not create a bitmap on every frame.
 */
private class CellShader {
    private var art: AsciiArt? = null
    private var background = 0
    private var glyphColors: GlyphColors? = null
    private var shader: BitmapShader? = null

    /** The shader for [art] in [style]; [colors] gives one colour per cell when it has to be built. */
    fun of(art: AsciiArt, style: ArtStyle, matrix: Matrix, colors: (AsciiArt) -> IntArray?): Shader {
        val cached = shader
        val current = if (
            cached != null &&
            art === this.art &&
            style.background == background &&
            style.glyphColors == glyphColors
        ) {
            cached
        } else {
            create(art, colors(art) ?: art.colors).also {
                this.art = art
                background = style.background
                glyphColors = style.glyphColors
                shader = it
            }
        }
        current.setLocalMatrix(matrix)
        return current
    }

    private fun create(art: AsciiArt, colors: IntArray): BitmapShader = BitmapShader(
        Bitmap.createBitmap(colors, art.columns, art.rows, Bitmap.Config.ARGB_8888),
        Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_NEAREST
    }
}
