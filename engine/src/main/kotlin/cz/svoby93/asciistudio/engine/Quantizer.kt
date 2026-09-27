package cz.svoby93.asciistudio.engine

/**
 * Maps continuous ink values onto the discrete tone [levels] of a ramp (sorted ascending,
 * `levels.first() == 0`, `levels.last() == 1`), optionally dithering the quantisation error.
 */
internal object Quantizer {

    fun quantize(
        values: FloatArray,
        width: Int,
        height: Int,
        levels: FloatArray,
        dithering: Dithering,
    ): IntArray = when (dithering) {
        Dithering.NONE -> IntArray(values.size) { nearest(levels, values[it]) }
        Dithering.FLOYD_STEINBERG -> floydSteinberg(values, width, height, levels)
        Dithering.ATKINSON -> atkinson(values, width, height, levels)
        Dithering.BAYER -> bayer(values, width, height, levels)
    }

    /** Index of the level closest to [value]. */
    fun nearest(levels: FloatArray, value: Float): Int {
        if (value <= levels[0]) return 0
        val last = levels.size - 1
        if (value >= levels[last]) return last
        var low = 0
        var high = last
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (levels[mid] <= value) low = mid else high = mid
        }
        return if (value - levels[low] <= levels[high] - value) low else high
    }

    /** Serpentine Floyd–Steinberg error diffusion. */
    private fun floydSteinberg(values: FloatArray, width: Int, height: Int, levels: FloatArray): IntArray {
        val buffer = values.copyOf()
        val result = IntArray(values.size)
        for (y in 0 until height) {
            val leftToRight = y % 2 == 0
            val step = if (leftToRight) 1 else -1
            var x = if (leftToRight) 0 else width - 1
            repeat(width) {
                val i = y * width + x
                val old = buffer[i].coerceIn(-0.5f, 1.5f)
                val index = nearest(levels, old)
                result[i] = index
                val error = old - levels[index]
                spread(buffer, width, height, x + step, y, error * 7f / 16f)
                spread(buffer, width, height, x - step, y + 1, error * 3f / 16f)
                spread(buffer, width, height, x, y + 1, error * 5f / 16f)
                spread(buffer, width, height, x + step, y + 1, error * 1f / 16f)
                x += step
            }
        }
        return result
    }

    /** Atkinson dithering: diffuses 6/8 of the error, which keeps highlights and shadows clean. */
    private fun atkinson(values: FloatArray, width: Int, height: Int, levels: FloatArray): IntArray {
        val buffer = values.copyOf()
        val result = IntArray(values.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = buffer[i].coerceIn(-0.5f, 1.5f)
                val index = nearest(levels, old)
                result[i] = index
                val share = (old - levels[index]) / 8f
                spread(buffer, width, height, x + 1, y, share)
                spread(buffer, width, height, x + 2, y, share)
                spread(buffer, width, height, x - 1, y + 1, share)
                spread(buffer, width, height, x, y + 1, share)
                spread(buffer, width, height, x + 1, y + 1, share)
                spread(buffer, width, height, x, y + 2, share)
            }
        }
        return result
    }

    /** Ordered dithering with an 8×8 Bayer matrix between the two neighbouring levels. */
    private fun bayer(values: FloatArray, width: Int, height: Int, levels: FloatArray): IntArray {
        val result = IntArray(values.size)
        val last = levels.size - 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val v = values[i]
                if (v <= 0f) continue
                if (v >= 1f) {
                    result[i] = last
                    continue
                }
                var lower = nearest(levels, v)
                if (levels[lower] > v) lower--
                if (lower >= last) {
                    result[i] = last
                    continue
                }
                val fraction = (v - levels[lower]) / (levels[lower + 1] - levels[lower])
                val threshold = (BAYER_8[(y and 7) * 8 + (x and 7)] + 0.5f) / 64f
                result[i] = if (fraction > threshold) lower + 1 else lower
            }
        }
        return result
    }

    private fun spread(buffer: FloatArray, width: Int, height: Int, x: Int, y: Int, amount: Float) {
        if (x in 0 until width && y < height) buffer[y * width + x] += amount
    }

    private val BAYER_8 = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )
}
