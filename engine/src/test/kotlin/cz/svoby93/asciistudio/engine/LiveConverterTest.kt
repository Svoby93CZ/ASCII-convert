package cz.svoby93.asciistudio.engine

import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveConverterTest {

    private val options = AsciiOptions(columns = 40, sharpness = 0.3f)

    /** Soft shapes with every tone, like a photo; 4 pixels per column at 40 columns. */
    private fun scene(x: Int, y: Int): Double {
        val light = 128 + 100 * sin(x / 9.0) * cos(y / 7.0)
        return if (hypot(x - 80.0, y - 60.0) < 30) 255 - light else light
    }

    /**
     * The scene as a camera films it, [shift] pixels to the left: every frame with sensor noise
     * and a slightly different exposure.
     */
    private fun frames(count: Int, seed: Long, shift: Int = 0): List<PixelImage> {
        val random = Random(seed)
        return List(count) {
            val gain = 1 + random.nextGaussian() * 0.008
            TestImages.of(160, 120) { x, y ->
                TestImages.gray((scene(x + shift, y) * gain + random.nextGaussian() * 3).roundToInt().coerceIn(0, 255))
            }
        }
    }

    @Test
    fun `a still scene keeps its glyphs although every frame is a little different`() {
        val kinds = listOf(
            options,
            options.copy(edgeMode = EdgeMode.MIXED),
            options.copy(glyphs = GlyphSet.Braille),
            options.copy(glyphs = GlyphSet.Ramp(CharRamps.DETAILED), dithering = Dithering.BAYER),
        )
        for (kind in kinds) {
            val frames = frames(30, seed = 1)
            val live = LiveConverter()
            val calm = frames.map { live.convert(it, kind) }.drop(5)
            val single = frames.map { AsciiConverter.convert(it, kind) }.drop(5)

            val before = changesPerFrame(single)
            val after = changesPerFrame(calm)
            assertTrue(before > 0.01, "Frames converted one by one flicker: ${before * 100} % with $kind")
            assertTrue(after < before / 10, "${after * 100} % of the glyphs still change per frame with $kind")
        }
    }

    @Test
    fun `motion shows at once`() {
        val live = LiveConverter()
        frames(10, seed = 2).forEach { live.convert(it, options) }

        // The camera turned: the scene moved by 10 columns.
        val moved = frames(1, seed = 3, shift = 40).single()

        assertSameArt(AsciiConverter.convert(moved, options), live.convert(moved, options))
    }

    @Test
    fun `error diffusion becomes the Bayer matrix, which stays put from frame to frame`() {
        val frame = frames(1, seed = 4).single()

        for (dithering in listOf(Dithering.FLOYD_STEINBERG, Dithering.ATKINSON)) {
            assertSameArt(
                AsciiConverter.convert(frame, options.copy(dithering = Dithering.BAYER)),
                LiveConverter().convert(frame, options.copy(dithering = dithering)),
            )
        }
    }

    @Test
    fun `another width or glyph kind starts afresh`() {
        val frames = frames(6, seed = 5)
        for (other in listOf(options.copy(columns = 50), options.copy(glyphs = GlyphSet.Braille))) {
            val live = LiveConverter()
            frames.dropLast(1).forEach { live.convert(it, options) }

            assertSameArt(AsciiConverter.convert(frames.last(), other), live.convert(frames.last(), other))
        }
    }

    /** The share of glyphs that change from one frame to the next, on average. */
    private fun changesPerFrame(arts: List<AsciiArt>): Double =
        arts.zipWithNext { a, b -> a.chars.indices.count { a.chars[it] != b.chars[it] }.toDouble() / a.chars.size }
            .average()

    private fun assertSameArt(expected: AsciiArt, actual: AsciiArt) {
        assertEquals(expected.columns, actual.columns)
        assertEquals(expected.rows, actual.rows)
        assertContentEquals(expected.chars, actual.chars)
        assertContentEquals(expected.colors, actual.colors)
    }
}
