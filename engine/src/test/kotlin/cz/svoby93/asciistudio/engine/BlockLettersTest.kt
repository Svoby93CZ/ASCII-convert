package cz.svoby93.asciistudio.engine

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockLettersTest {

    private val word = BlockLetters("SVOBY")

    @Test
    fun `frames have the size of a donut frame and its shades`() {
        val frame = word.frame(yaw = 0.4f, pitch = -0.25f) { 0 }

        assertEquals(60, frame.columns)
        assertEquals(26, frame.rows)
        assertTrue(frame.chars.all { it == ' ' || it in ".,-~:;=!*#\$@" })
    }

    @Test
    fun `face on, the five letters stand apart and fill most of the width`() {
        val frame = word.frame(yaw = 0f, pitch = 0f) { 0 }
        val inked = (0 until frame.columns).map { column -> (0 until frame.rows).any { frame[column, it] != ' ' } }

        val letters = inked.joinToString("") { if (it) "#" else " " }.trim().split(Regex(" +"))
        assertEquals(5, letters.size, frame.toText())
        assertTrue(inked.count { it } > frame.columns / 2, frame.toText())
    }

    @Test
    fun `face on, the rows show the pixels of the letters`() {
        val frame = word.frame(yaw = 0f, pitch = 0f) { 0 }
        val lines = frame.toText().lines().filter { it.isNotBlank() }

        // The top row has seven strokes, two of them in V and Y; the bottom row has one per letter.
        assertEquals(7, strokes(lines.first()), frame.toText())
        assertEquals(5, strokes(lines.last()), frame.toText())
    }

    @Test
    fun `turned edge on, the word is thin`() {
        val frame = word.frame(yaw = (PI / 2).toFloat(), pitch = 0f) { 0 }
        val inkedColumns = (0 until frame.columns).count { column ->
            (0 until frame.rows).any { frame[column, it] != ' ' }
        }

        assertTrue(inkedColumns < frame.columns / 4, frame.toText())
    }

    @Test
    fun `lit faces get brighter colours than faces in the shade`() {
        val frame = word.frame(yaw = 0.5f, pitch = -0.25f) { light -> (light * 255).toInt() }
        val levels = frame.colors.filterIndexed { i, _ -> frame.chars[i] != ' ' }.toSet()

        assertTrue(levels.size > 2, "Expected several shades, got $levels")
    }

    @Test
    fun `frames are deterministic`() {
        assertEquals(word.frame(1f, -0.2f) { 0 }.toText(), BlockLetters("SVOBY").frame(1f, -0.2f) { 0 }.toText())
    }

    @Test
    fun `letters without pixels are refused`() {
        assertFailsWith<IllegalArgumentException> { BlockLetters("DONUT") }
    }

    private fun strokes(line: String): Int = line.trim().split(Regex(" +")).size
}
