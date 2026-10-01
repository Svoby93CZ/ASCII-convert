package cz.svoby93.asciistudio.engine.font

/**
 * SVG path data for a glyph outline, with y pointing down as in SVG and the baseline at y = 0.
 * Quadratic TrueType arcs become `Q` commands, and controls in a row get the on-curve point
 * between them that TrueType implies.
 */
fun List<Contour>.toSvgPath(): String = buildString { this@toSvgPath.forEach { appendContour(it) } }

private fun StringBuilder.appendContour(contour: Contour) {
    val count = contour.size
    if (count == 0) return
    val start = (0 until count).firstOrNull { contour.onCurve[it] }
    val points = if (start != null) {
        (0..count).map { offset ->
            val index = (start + offset) % count
            Point(contour.x[index].toFloat(), contour.y[index].toFloat(), contour.onCurve[index])
        }
    } else {
        // Only controls: start between the last and the first, where TrueType implies a point.
        val first = Point((contour.x[count - 1] + contour.x[0]) / 2f, (contour.y[count - 1] + contour.y[0]) / 2f, true)
        listOf(first) + (0 until count).map { Point(contour.x[it].toFloat(), contour.y[it].toFloat(), false) } + first
    }
    append('M').appendPoint(points[0])
    var control: Point? = null
    for ((index, point) in points.withIndex().drop(1)) {
        val closing = index == points.lastIndex
        if (point.onCurve) {
            when {
                control != null -> append('Q').appendPoint(control).append(' ').appendPoint(point)
                // The last line back to the start is drawn by Z.
                !closing -> append('L').appendPoint(point)
            }
            control = null
        } else {
            if (control != null) {
                val between = Point((control.x + point.x) / 2f, (control.y + point.y) / 2f, true)
                append('Q').appendPoint(control).append(' ').appendPoint(between)
            }
            control = point
        }
    }
    append('Z')
}

private class Point(val x: Float, val y: Float, val onCurve: Boolean)

private fun StringBuilder.appendPoint(point: Point): StringBuilder =
    appendNumber(point.x).append(' ').appendNumber(-point.y)

/** Whole numbers without a fraction, halves (from implied points) with one decimal. */
internal fun StringBuilder.appendNumber(value: Float): StringBuilder {
    val tenths = Math.round(value * 10)
    if (tenths == 0) return append('0')
    if (tenths % 10 == 0) return append(tenths / 10)
    if (tenths < 0) append('-')
    val magnitude = kotlin.math.abs(tenths)
    return append(magnitude / 10).append('.').append(magnitude % 10)
}
