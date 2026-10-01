package cz.svoby93.asciistudio.engine

import kotlin.math.abs

/**
 * Converts the frames of a live camera into a calm picture. Converted one by one, a still scene
 * flickers: noise and the camera's exposure flip glyphs whose tone lies near the boundary of two
 * glyphs, and error diffusion reshuffles whole areas from frame to frame. The live converter
 * - smooths the samples over a few frames, but lets real changes such as motion through at once;
 * - keeps a glyph until its tone has clearly moved on, see [GlyphHold];
 * - dithers with the Bayer matrix instead of error diffusion, because ordered dithering stays put;
 * - does neither of the first two while the whole picture moves, e.g. when the camera turns.
 *
 * It keeps the state of one stream of frames; conversions run one after another.
 */
class LiveConverter internal constructor(
    private val minResponse: Float,
    private val noise: Float,
    private val motion: Float,
    private val movingShare: Float,
    holdMargin: Float,
) {
    constructor() : this(MIN_RESPONSE, NOISE, MOTION, MOVING_SHARE, HOLD_MARGIN)

    private var smoothed: Samples? = null
    private val hold = GlyphHold(holdMargin)

    @Synchronized
    fun convert(frame: PixelImage, options: AsciiOptions): AsciiArt {
        val live = when (options.dithering) {
            Dithering.FLOYD_STEINBERG, Dithering.ATKINSON -> options.copy(dithering = Dithering.BAYER)
            Dithering.NONE, Dithering.BAYER -> options
        }
        val samples = AsciiConverter.sample(frame, live)
        val previous = smoothed?.takeIf { it.hasGridsOf(samples) }
        if (previous == null || moving(previous.grid, samples.grid)) {
            hold.forget()
        } else {
            smooth(previous.grid, samples.grid)
            val fine = samples.fine
            val previousFine = previous.fine
            if (fine != null && previousFine != null) smooth(previousFine, fine)
        }
        smoothed = samples
        return AsciiConverter.convert(samples, live, hold)
    }

    private fun Samples.hasGridsOf(other: Samples): Boolean =
        braille == other.braille &&
            grid.width == other.grid.width &&
            grid.height == other.grid.height &&
            fine?.width == other.fine?.width &&
            fine?.height == other.fine?.height

    /** Whether so many cells changed a lot that the whole picture moves. */
    private fun moving(previous: SampleGrid, current: SampleGrid): Boolean {
        var moved = 0
        for (i in current.luma.indices) {
            if (abs(current.luma[i] - previous.luma[i]) > motion) moved++
        }
        return moved > current.luma.size * movingShare
    }

    /**
     * Moves the fresh [current] samples only part of the way from [previous]. Noise moves the tone
     * of a cell a little, motion a lot, so small changes are averaged over a few frames and large
     * ones show at once.
     */
    private fun smooth(previous: SampleGrid, current: SampleGrid) {
        val luma = current.luma
        val colors = current.colors
        for (i in luma.indices) {
            val change = abs(luma[i] - previous.luma[i])
            val response = minResponse + (1f - minResponse) * smoothStep((change - noise) / (motion - noise))
            luma[i] = previous.luma[i] + (luma[i] - previous.luma[i]) * response
            colors[i] = mix(previous.colors[i], colors[i], response)
        }
    }

    /** [current] moved towards [previous]; it keeps the alpha of [current]. */
    private fun mix(previous: Int, current: Int, response: Float): Int {
        var mixed = current and ALPHA
        for (shift in 0..16 step 8) {
            val from = (previous shr shift) and 0xFF
            val to = (current shr shift) and 0xFF
            mixed = mixed or ((from + (to - from) * response + 0.5f).toInt() shl shift)
        }
        return mixed
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        // Tuned on simulated streams of four photos with sensor noise, exposure jitter, hand shake
        // and pans: a still scene flickers 7 to 70 times less, and pans keep up with the camera.

        /** The share of a small change that shows in the next frame. */
        const val MIN_RESPONSE = 0.35f

        /** Changes of luma up to this size are treated as noise. */
        const val NOISE = 0.015f

        /** Changes of luma from this size on are motion and show at once. */
        const val MOTION = 0.06f

        /** When this share of the cells moves, the whole picture moves. */
        const val MOVING_SHARE = 0.1f

        /** How far the tone of a cell may move before its glyph changes, in ink from 0 to 1. */
        const val HOLD_MARGIN = 0.03f

        const val ALPHA = 0xFF000000.toInt()
    }
}
