package cz.svoby93.asciistudio.engine

import kotlin.math.pow

/** Turns sampled luma into "ink" values in 0..1 (0 = paper, 1 = the densest glyph). */
internal object ToneMapper {

    fun toInk(grid: SampleGrid, options: AsciiOptions): FloatArray {
        val values = grid.luma.copyOf()
        if (options.autoLevels) stretchLevels(values, grid.alpha)
        if (options.sharpness > 0f) sharpen(values, grid.width, grid.height, options.sharpness)
        applyBrightnessContrast(values, options.brightness, options.contrast)
        val alpha = grid.alpha
        for (i in values.indices) {
            val v = values[i].coerceIn(0f, 1f)
            values[i] = (if (options.invert) 1f - v else v) * alpha[i]
        }
        return values
    }

    /** Normalised luma used for edge detection: levels only, no creative adjustments. */
    fun normalizedLuma(grid: SampleGrid, autoLevels: Boolean): FloatArray {
        val values = grid.luma.copyOf()
        if (autoLevels) stretchLevels(values, grid.alpha)
        return values
    }

    /**
     * Maps the 1st..99th percentile of the (visible) luma histogram to 0..1, which makes flat,
     * foggy photos use the whole glyph ramp.
     */
    fun stretchLevels(values: FloatArray, alpha: FloatArray) {
        val histogram = IntArray(HISTOGRAM_BINS)
        var total = 0
        for (i in values.indices) {
            if (alpha[i] < 0.5f) continue
            histogram[(values[i] * (HISTOGRAM_BINS - 1)).toInt().coerceIn(0, HISTOGRAM_BINS - 1)]++
            total++
        }
        if (total == 0) return
        val low = percentile(histogram, total, 0.01f)
        val high = percentile(histogram, total, 0.99f)
        if (high - low < MIN_LEVELS_SPAN) return
        val scale = 1f / (high - low)
        for (i in values.indices) {
            values[i] = ((values[i] - low) * scale).coerceIn(0f, 1f)
        }
    }

    private fun percentile(histogram: IntArray, total: Int, fraction: Float): Float {
        val target = fraction * total
        var seen = 0
        for (bin in histogram.indices) {
            seen += histogram[bin]
            if (seen >= target) return bin / (HISTOGRAM_BINS - 1f)
        }
        return 1f
    }

    /** Unsharp mask with a 3×3 binomial blur. */
    fun sharpen(values: FloatArray, width: Int, height: Int, amount: Float) {
        val source = values.copyOf()
        val strength = amount * 2f
        for (y in 0 until height) {
            val up = maxOf(y - 1, 0) * width
            val row = y * width
            val down = minOf(y + 1, height - 1) * width
            for (x in 0 until width) {
                val left = maxOf(x - 1, 0)
                val right = minOf(x + 1, width - 1)
                val blurred = (
                    source[up + left] + 2 * source[up + x] + source[up + right] +
                        2 * source[row + left] + 4 * source[row + x] + 2 * source[row + right] +
                        source[down + left] + 2 * source[down + x] + source[down + right]
                    ) / 16f
                val v = source[row + x]
                values[row + x] = v + strength * (v - blurred)
            }
        }
    }

    fun applyBrightnessContrast(values: FloatArray, brightness: Float, contrast: Float) {
        if (brightness == 0f && contrast == 0f) return
        // Brightness is a gamma curve: +1 lifts mid-tones strongly, -1 darkens them.
        val gamma = 3f.pow(-brightness.coerceIn(-1f, 1f))
        // Contrast scales around middle grey: 0.25× at -1 up to 4× at +1.
        val factor = 4f.pow(contrast.coerceIn(-1f, 1f))
        for (i in values.indices) {
            var v = values[i].coerceIn(0f, 1f)
            if (gamma != 1f) v = v.pow(gamma)
            if (factor != 1f) v = (v - 0.5f) * factor + 0.5f
            values[i] = v
        }
    }

    private const val HISTOGRAM_BINS = 256
    private const val MIN_LEVELS_SPAN = 0.05f
}
