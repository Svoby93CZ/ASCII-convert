package cz.svoby93.asciistudio.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Sobel based outline detection.
 *
 * For glyph ramps every cell looks at the structure tensor of the gradients inside it, which gives
 * a stable edge orientation that is then drawn with one of `| / - \`. Non-maximum suppression
 * keeps the outlines one cell thin, like hand-made ASCII line art.
 */
internal object EdgeDetector {

    class Gradients(val width: Int, val height: Int, val gx: FloatArray, val gy: FloatArray)

    /** Sobel gradients normalised so that a full black → white step yields 1. */
    fun sobel(luma: FloatArray, width: Int, height: Int): Gradients {
        val gx = FloatArray(luma.size)
        val gy = FloatArray(luma.size)
        for (y in 0 until height) {
            val up = maxOf(y - 1, 0) * width
            val row = y * width
            val down = minOf(y + 1, height - 1) * width
            for (x in 0 until width) {
                val left = maxOf(x - 1, 0)
                val right = minOf(x + 1, width - 1)
                val topLeft = luma[up + left]
                val top = luma[up + x]
                val topRight = luma[up + right]
                val bottomLeft = luma[down + left]
                val bottom = luma[down + x]
                val bottomRight = luma[down + right]
                gx[row + x] = (topRight + 2 * luma[row + right] + bottomRight -
                    topLeft - 2 * luma[row + left] - bottomLeft) / 4f
                gy[row + x] = (bottomLeft + 2 * bottom + bottomRight - topLeft - 2 * top - topRight) / 4f
            }
        }
        return Gradients(width, height, gx, gy)
    }

    /** Minimum gradient magnitude for an edge; higher sensitivity lowers the bar. */
    fun threshold(sensitivity: Float): Float {
        val s = sensitivity.coerceIn(0f, 1f)
        return MAX_THRESHOLD + (MIN_THRESHOLD - MAX_THRESHOLD) * s
    }

    /**
     * One outline glyph per cell (or `'\u0000'` where there is no edge). Each cell covers
     * [blockWidth] × [blockHeight] gradient samples. [yScale] converts vertical gradient samples
     * into the horizontal unit so that orientations are measured in image space.
     */
    fun cellOutlines(
        gradients: Gradients,
        columns: Int,
        rows: Int,
        blockWidth: Int,
        blockHeight: Int,
        yScale: Float,
        sensitivity: Float,
    ): CharArray {
        val cells = columns * rows
        val magnitude = FloatArray(cells)
        val direction = IntArray(cells)
        val samples = blockWidth * blockHeight
        val gx = gradients.gx
        val gy = gradients.gy
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                var sxx = 0f
                var syy = 0f
                var sxy = 0f
                var energy = 0f
                for (dy in 0 until blockHeight) {
                    val start = (row * blockHeight + dy) * gradients.width + column * blockWidth
                    for (dx in 0 until blockWidth) {
                        val x = gx[start + dx]
                        val y = gy[start + dx]
                        val ys = y * yScale
                        sxx += x * x
                        syy += ys * ys
                        sxy += x * ys
                        energy += x * x + y * y
                    }
                }
                val cell = row * columns + column
                val trace = sxx + syy
                if (trace <= EPSILON) continue
                val coherence = sqrt((sxx - syy) * (sxx - syy) + 4 * sxy * sxy) / trace
                if (coherence < MIN_COHERENCE) continue
                magnitude[cell] = sqrt(energy / samples)
                direction[cell] = classify(0.5f * atan2(2 * sxy, sxx - syy))
            }
        }

        val limit = threshold(sensitivity)
        val result = CharArray(cells)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cell = row * columns + column
                val m = magnitude[cell]
                if (m < limit) continue
                val dir = direction[cell]
                if (!isLocalMaximum(magnitude, columns, rows, column, row, dir)) continue
                result[cell] = OUTLINE_GLYPHS[dir]
            }
        }
        return result
    }

    /** Marks the samples (Braille dots) that lie on a thin edge line. */
    fun dotOutlines(gradients: Gradients, sensitivity: Float): BooleanArray {
        val width = gradients.width
        val height = gradients.height
        val magnitude = FloatArray(width * height)
        val direction = IntArray(width * height)
        for (i in magnitude.indices) {
            val x = gradients.gx[i]
            val y = gradients.gy[i]
            magnitude[i] = sqrt(x * x + y * y)
            direction[i] = classify(foldToHalfTurn(atan2(y, x)))
        }
        val limit = threshold(sensitivity)
        val result = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (magnitude[i] < limit) continue
                if (!isLocalMaximum(magnitude, width, height, x, y, direction[i])) continue
                result[i] = true
            }
        }
        return result
    }

    /**
     * Buckets a gradient orientation (radians, -π/2..π/2, y pointing down) into four directions:
     * 0 = horizontal gradient (vertical edge), 1 = down-right, 2 = vertical, 3 = up-right.
     */
    private fun classify(angle: Float): Int {
        val degrees = Math.toDegrees(angle.toDouble())
        return when {
            abs(degrees) < 22.5 -> 0
            abs(degrees) >= 67.5 -> 2
            degrees > 0 -> 1
            else -> 3
        }
    }

    /** Folds a vector angle (-π..π) into an orientation (-π/2..π/2); opposite vectors are equal. */
    private fun foldToHalfTurn(angle: Float): Float = when {
        angle > HALF_PI -> angle - PI
        angle <= -HALF_PI -> angle + PI
        else -> angle
    }

    /**
     * Non-maximum suppression along the gradient. On a plateau of equal values only the first
     * sample survives, so a sharp step (which lights up two samples equally) stays one line thin.
     */
    private fun isLocalMaximum(magnitude: FloatArray, width: Int, height: Int, x: Int, y: Int, direction: Int): Boolean {
        val (dx, dy) = NEIGHBOUR_OFFSETS[direction]
        val m = magnitude[y * width + x]
        return m > magnitudeAt(magnitude, width, height, x - dx, y - dy) &&
            m >= magnitudeAt(magnitude, width, height, x + dx, y + dy)
    }

    private fun magnitudeAt(magnitude: FloatArray, width: Int, height: Int, x: Int, y: Int): Float =
        if (x in 0 until width && y in 0 until height) magnitude[y * width + x] else 0f

    /** Edge glyph for each gradient direction: the edge runs perpendicular to the gradient. */
    private val OUTLINE_GLYPHS = charArrayOf('|', '/', '-', '\\')

    /** Neighbour offsets along each gradient direction, used for non-maximum suppression. */
    private val NEIGHBOUR_OFFSETS = arrayOf(1 to 0, 1 to 1, 0 to 1, 1 to -1)

    private const val PI = Math.PI.toFloat()
    private const val HALF_PI = PI / 2f
    private const val EPSILON = 1e-6f
    private const val MIN_COHERENCE = 0.3f
    private const val MAX_THRESHOLD = 0.45f
    private const val MIN_THRESHOLD = 0.06f
}
