package cz.svoby93.asciistudio.engine

import kotlin.math.roundToInt

/**
 * A downsampled view of an image: luma, coverage (alpha) and average colour per grid cell.
 * All arrays are row-major with [width] × [height] entries.
 */
internal class SampleGrid(
    val width: Int,
    val height: Int,
    /** Rec. 601 luma of the average colour, 0..1. */
    val luma: FloatArray,
    /** Average opacity, 0..1. Transparent areas never receive ink. */
    val alpha: FloatArray,
    /** Alpha-weighted average colour, with the average opacity in its alpha channel. */
    val colors: IntArray,
) {
    /** Averages blocks of [blockWidth] × [blockHeight] cells; the size must divide evenly. */
    fun downsample(blockWidth: Int, blockHeight: Int): SampleGrid {
        require(width % blockWidth == 0 && height % blockHeight == 0)
        val outWidth = width / blockWidth
        val outHeight = height / blockHeight
        val count = outWidth * outHeight
        val outLuma = FloatArray(count)
        val outAlpha = FloatArray(count)
        val outColors = IntArray(count)
        val cellsPerBlock = blockWidth * blockHeight
        for (oy in 0 until outHeight) {
            for (ox in 0 until outWidth) {
                var sumA = 0f
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                for (dy in 0 until blockHeight) {
                    val rowStart = (oy * blockHeight + dy) * width + ox * blockWidth
                    for (dx in 0 until blockWidth) {
                        val i = rowStart + dx
                        val a = alpha[i]
                        val c = colors[i]
                        sumA += a
                        sumR += a * ((c shr 16) and 0xFF)
                        sumG += a * ((c shr 8) and 0xFF)
                        sumB += a * (c and 0xFF)
                    }
                }
                val o = oy * outWidth + ox
                outAlpha[o] = sumA / cellsPerBlock
                if (sumA > 0f) {
                    val r = (sumR / sumA).toInt()
                    val g = (sumG / sumA).toInt()
                    val b = (sumB / sumA).toInt()
                    outColors[o] = pack((outAlpha[o] * 255f).roundToInt(), r, g, b)
                    outLuma[o] = luma(r, g, b)
                }
            }
        }
        return SampleGrid(outWidth, outHeight, outLuma, outAlpha, outColors)
    }
}

internal object Sampler {

    /**
     * Area-averages [image] onto a [gridWidth] × [gridHeight] grid. When downsampling, every
     * source pixel is read exactly once, so the cost is proportional to the image size regardless
     * of the grid size. When the grid is finer than the image, cells fall back to the nearest pixel.
     */
    fun sample(image: PixelImage, gridWidth: Int, gridHeight: Int): SampleGrid {
        val imageWidth = image.width
        val imageHeight = image.height
        val pixels = image.pixels
        val xStart = IntArray(gridWidth)
        val xEnd = IntArray(gridWidth)
        for (gx in 0 until gridWidth) {
            val (start, end) = span(gx, gridWidth, imageWidth)
            xStart[gx] = start
            xEnd[gx] = end
        }

        val count = gridWidth * gridHeight
        val luma = FloatArray(count)
        val alpha = FloatArray(count)
        val colors = IntArray(count)
        val sumA = LongArray(gridWidth)
        val sumR = LongArray(gridWidth)
        val sumG = LongArray(gridWidth)
        val sumB = LongArray(gridWidth)

        for (gy in 0 until gridHeight) {
            val (yStart, yEnd) = span(gy, gridHeight, imageHeight)
            sumA.fill(0)
            sumR.fill(0)
            sumG.fill(0)
            sumB.fill(0)
            for (y in yStart until yEnd) {
                val rowOffset = y * imageWidth
                for (gx in 0 until gridWidth) {
                    var a = 0L
                    var r = 0L
                    var g = 0L
                    var b = 0L
                    for (x in xStart[gx] until xEnd[gx]) {
                        val p = pixels[rowOffset + x]
                        val pa = (p ushr 24).toLong()
                        a += pa
                        r += pa * ((p shr 16) and 0xFF)
                        g += pa * ((p shr 8) and 0xFF)
                        b += pa * (p and 0xFF)
                    }
                    sumA[gx] += a
                    sumR[gx] += r
                    sumG[gx] += g
                    sumB[gx] += b
                }
            }
            val blockHeight = yEnd - yStart
            for (gx in 0 until gridWidth) {
                val index = gy * gridWidth + gx
                val pixelCount = (xEnd[gx] - xStart[gx]) * blockHeight
                val a = sumA[gx]
                alpha[index] = a / (pixelCount * 255f)
                if (a > 0) {
                    val r = (sumR[gx] / a).toInt()
                    val g = (sumG[gx] / a).toInt()
                    val b = (sumB[gx] / a).toInt()
                    colors[index] = pack(((a + pixelCount / 2) / pixelCount).toInt(), r, g, b)
                    luma[index] = luma(r, g, b)
                }
            }
        }
        return SampleGrid(gridWidth, gridHeight, luma, alpha, colors)
    }

    /** Pixel range `[start, end)` covered by cell [index]; never empty. */
    private fun span(index: Int, cells: Int, pixels: Int): Pair<Int, Int> {
        val start = (index.toLong() * pixels / cells).toInt().coerceAtMost(pixels - 1)
        val end = ((index + 1).toLong() * pixels / cells).toInt().coerceIn(start + 1, pixels)
        return start to end
    }
}

internal const val OPAQUE_BLACK: Int = 0xFF000000.toInt()

internal fun pack(alpha: Int, r: Int, g: Int, b: Int): Int = (alpha shl 24) or (r shl 16) or (g shl 8) or b

internal fun luma(r: Int, g: Int, b: Int): Float = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
