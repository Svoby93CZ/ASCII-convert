package cz.svoby93.asciistudio.render

import kotlin.math.max
import kotlin.math.min

/**
 * The size of video frames for content of [contentWidth] × [contentHeight] in any unit: [shortSide]
 * pixels on the shorter side, at most [maxLongSide] on the longer one, and both multiples of
 * [alignment], which encoders need. Rounding down keeps the size within what the encoder was asked.
 */
fun videoFrameSize(
    contentWidth: Float,
    contentHeight: Float,
    shortSide: Int,
    maxLongSide: Int,
    alignment: Int,
): Pair<Int, Int> {
    val ratio = max(contentWidth, contentHeight) / min(contentWidth, contentHeight)
    var short = shortSide.toFloat()
    var long = short * ratio
    if (long > maxLongSide) {
        short = maxLongSide / ratio
        long = maxLongSide.toFloat()
    }
    val alignedShort = max(alignment, short.toInt() / alignment * alignment)
    val alignedLong = max(alignment, long.toInt() / alignment * alignment)
    return if (contentHeight >= contentWidth) alignedShort to alignedLong else alignedLong to alignedShort
}
