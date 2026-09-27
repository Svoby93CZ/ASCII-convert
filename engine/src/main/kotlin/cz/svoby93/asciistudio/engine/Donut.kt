package cz.svoby93.asciistudio.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The famous spinning ASCII torus (donut.c by Andy Sloane), ported to Kotlin as a playful hero
 * animation. Every frame is returned as [AsciiArt] so it can be drawn by the regular renderer;
 * cell colours come from [shade], which receives the surface brightness in 0..1.
 */
class Donut(
    val columns: Int = 60,
    val rows: Int = 26,
    private val cellAspect: Float = AsciiOptions.DEFAULT_CELL_ASPECT,
) {
    private val depth = FloatArray(columns * rows)

    fun frame(timeSeconds: Float, shade: (Float) -> Int): AsciiArt {
        val a = 1f + timeSeconds * 1.1f
        val b = timeSeconds * 0.55f
        val cosA = cos(a)
        val sinA = sin(a)
        val cosB = cos(b)
        val sinB = sin(b)

        val chars = CharArray(columns * rows) { ' ' }
        val colors = IntArray(columns * rows) { OPAQUE_BLACK }
        depth.fill(0f)

        val scaleX = columns * DISTANCE * 3f / (8f * (TUBE_RADIUS + RING_RADIUS))
        val scaleY = scaleX * cellAspect
        var theta = 0f
        while (theta < TWO_PI) {
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)
            val circleX = RING_RADIUS + TUBE_RADIUS * cosTheta
            val circleY = TUBE_RADIUS * sinTheta
            var phi = 0f
            while (phi < TWO_PI) {
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)
                val x = circleX * (cosB * cosPhi + sinA * sinB * sinPhi) - circleY * cosA * sinB
                val y = circleX * (sinB * cosPhi - sinA * cosB * sinPhi) + circleY * cosA * cosB
                val oneOverZ = 1f / (DISTANCE + cosA * circleX * sinPhi + circleY * sinA)
                val column = (columns / 2f + scaleX * oneOverZ * x).toInt()
                val row = (rows / 2f - scaleY * oneOverZ * y).toInt()
                val luminance = cosPhi * cosTheta * sinB - cosA * cosTheta * sinPhi - sinA * sinTheta +
                    cosB * (cosA * sinTheta - cosTheta * sinA * sinPhi)
                if (luminance > 0f && column in 0 until columns && row in 0 until rows) {
                    val cell = row * columns + column
                    if (oneOverZ > depth[cell]) {
                        depth[cell] = oneOverZ
                        val level = (luminance * 8f).toInt().coerceIn(0, SHADES.length - 1)
                        chars[cell] = SHADES[level]
                        colors[cell] = shade((luminance / SQRT_2).coerceIn(0f, 1f))
                    }
                }
                phi += PHI_STEP
            }
            theta += THETA_STEP
        }
        return AsciiArt(columns, rows, chars, colors, cellAspect, isBraille = false)
    }

    private companion object {
        const val SHADES = ".,-~:;=!*#\$@"
        const val TUBE_RADIUS = 1f
        const val RING_RADIUS = 2f
        const val DISTANCE = 5f
        const val THETA_STEP = 0.07f
        const val PHI_STEP = 0.02f
        const val TWO_PI = (2 * Math.PI).toFloat()
        val SQRT_2 = sqrt(2f)
    }
}
