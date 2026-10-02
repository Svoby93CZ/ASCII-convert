package cz.svoby93.asciistudio.ui.home

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureShowTest {

    private val duration = SignatureShow.DURATION

    @Test
    fun `the donut dissolves into the name and comes back at the end`() {
        assertEquals(0f, SignatureShow.dissolve(0f), 0f)
        assertEquals(1f, SignatureShow.dissolve(1f), 0f)
        assertEquals(1f, SignatureShow.dissolve(duration / 2), 0f)
        assertEquals(0f, SignatureShow.dissolve(duration), 0f)
        assertTrue(SignatureShow.dissolve(0.3f) in 0.1f..0.9f)
    }

    @Test
    fun `the name swings in edge on and away edge on`() {
        assertEquals(-PI / 2, SignatureShow.yaw(0f).toDouble(), 1e-4)
        assertEquals(PI / 2, SignatureShow.yaw(duration).toDouble(), 1e-4)
        assertEquals(PI / 2, SignatureShow.yaw(duration + 5f).toDouble(), 1e-4)
    }

    @Test
    fun `the name turns around twice and faces the viewer in between`() {
        val samples = (0..2000).map { it * duration / 2000 }
        // Face on means a yaw of a whole number of turns; the back faces the viewer half way.
        val facing = samples.map { cos(SignatureShow.yaw(it).toDouble()) }
        val backwards = facing.zipWithNext().count { (a, b) -> a > -0.99 && b <= -0.99 }
        assertEquals(2, backwards)
        assertTrue(facing.count { it > 0.97 } > samples.size / 3)
    }

    @Test
    fun `the turn moves smoothly from frame to frame`() {
        val frame = 1f / 30
        var t = 0f
        while (t < duration) {
            val step = SignatureShow.yaw(t + frame) - SignatureShow.yaw(t)
            // A whole turn ends where the next rest begins, at the same angle.
            val wrapped = abs(step) % (2 * PI).toFloat()
            assertTrue("Jump of $step at $t", wrapped < 0.35f || wrapped > 2 * PI - 0.35f)
            t += frame
        }
    }
}
