package cz.svoby93.asciistudio.engine.font

import kotlin.test.Test
import kotlin.test.assertEquals

class SvgPathTest {

    private fun contour(vararg points: Triple<Int, Int, Boolean>) = Contour(
        x = IntArray(points.size) { points[it].first },
        y = IntArray(points.size) { points[it].second },
        onCurve = BooleanArray(points.size) { points[it].third },
    )

    @Test
    fun `on-curve points are joined by lines and y points down`() {
        val square = contour(Triple(0, 0, true), Triple(100, 0, true), Triple(100, 100, true), Triple(0, 100, true))

        assertEquals("M0 0L100 0L100 -100L0 -100Z", listOf(square).toSvgPath())
    }

    @Test
    fun `controls in a row imply an on-curve point between them`() {
        val arc = contour(Triple(0, 0, true), Triple(100, 0, false), Triple(100, 100, false), Triple(0, 100, true))

        assertEquals("M0 0Q100 0 100 -50Q100 -100 0 -100Z", listOf(arc).toSvgPath())
    }

    @Test
    fun `a contour of controls only starts between its last and first point`() {
        val round = contour(Triple(0, 0, false), Triple(10, 0, false), Triple(10, 10, false), Triple(0, 10, false))

        assertEquals("M0 -5Q0 0 5 0Q10 0 10 -5Q10 -10 5 -10Q0 -10 0 -5Z", listOf(round).toSvgPath())
    }

    @Test
    fun `a curve back to the start is kept`() {
        val drop = contour(Triple(0, 0, true), Triple(50, 50, true), Triple(0, 100, false))

        assertEquals("M0 0L50 -50Q0 -100 0 0Z", listOf(drop).toSvgPath())
    }
}
