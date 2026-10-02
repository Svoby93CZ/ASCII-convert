package cz.svoby93.asciistudio.ui.home

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * What the donut's easter egg does, by the seconds since it began: the author's nickname swings
 * in from edge on while the donut dissolves into it, rests face on, turns around twice, rests again
 * and swings away while the donut comes back.
 */
internal object SignatureShow {
    private const val SWING = 1f
    private const val DISSOLVE = 0.6f
    private const val REST = 1.3f
    private const val TURN = 2.2f
    private const val TURNS = 2
    const val DURATION = 2 * SWING + (TURNS + 1) * REST + TURNS * TURN

    /** Tipped a little towards the viewer, so that the tops of the blocks show. */
    const val PITCH = -0.25f

    /** Turned a little, so that a still frame shows the depth of the letters too. */
    const val STILL_YAW = 0.3f

    /** While the name rests, it turns this far and back. */
    private const val NOD = 0.2f
    private const val HALF_TURN = (PI / 2).toFloat()
    private const val FULL_TURN = (2 * PI).toFloat()

    /** How much of the frame shows the name instead of the donut, from 0 to 1. */
    fun dissolve(seconds: Float): Float = (min(seconds, DURATION - seconds) / DISSOLVE).coerceIn(0f, 1f)

    /** The turn of the name around its upright axis: edge on at the start and the end, face on at rest. */
    fun yaw(seconds: Float): Float {
        if (seconds < SWING) return -HALF_TURN * (1f - easeOut(seconds / SWING))
        var t = seconds - SWING
        repeat(TURNS) {
            if (t < REST) return nod(t)
            t -= REST
            if (t < TURN) return FULL_TURN * easeInOut(t / TURN)
            t -= TURN
        }
        if (t < REST) return nod(t)
        t -= REST
        return HALF_TURN * easeIn((t / SWING).coerceAtMost(1f))
    }

    /** Starts and ends at rest, so that the name moves on smoothly. */
    private fun nod(t: Float): Float {
        val wave = sin(PI * t / REST).toFloat()
        return NOD * wave * wave
    }

    private fun easeIn(x: Float) = x * x * x

    private fun easeOut(x: Float) = 1f - easeIn(1f - x)

    private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - easeIn(2f - 2f * x) / 2f
}
