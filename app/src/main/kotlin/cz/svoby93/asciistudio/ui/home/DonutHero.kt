package cz.svoby93.asciistudio.ui.home

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.Signature
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.BlockLetters
import cz.svoby93.asciistudio.engine.Donut
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.GlyphColors
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.studio.LocalAnimationsEnabled
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import cz.svoby93.asciistudio.ui.studio.rememberDecorationClock
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The classic spinning ASCII donut in the colours of the palette, rendered by the same renderer as
 * the user's art. Frames are computed while drawing, so the donut turns without composing again.
 * Stands still when decorations may not move.
 *
 * A tap spins the donut faster for a moment. Five quick taps turn it into the author's nickname
 * in 3D for a few seconds ([SignatureShow]) and call [onSignature].
 */
@Composable
fun DonutHero(onSignature: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalStudioColors.current
    val animate = LocalAnimationsEnabled.current
    val frames = remember(colors) { HeroFrames(shader(dark = colors.tint(0.3f), bright = colors.ink)) }
    val clock = rememberDecorationClock(start = START_TIME)
    val taps = remember { HeroTaps() }
    val haptics = LocalHapticFeedback.current
    val currentOnSignature by rememberUpdatedState(onSignature)

    // The show ends with its last turn; without moving decorations, the still name stays a while.
    // Ending it for good keeps a still frame from bringing it back later, e.g. in battery saver.
    val showStart = taps.showStart
    if (showStart != null) {
        LaunchedEffect(showStart, animate) {
            delay(if (animate) SHOW_MILLIS else STILL_MILLIS)
            taps.endShow()
        }
    }

    AsciiArtView(
        art = { frames.at(clock.floatValue, taps, animate) },
        style = ArtStyle(colors.paper.toArgb(), colors.ink.toArgb(), GlyphColors.ART),
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures {
                if (taps.tap(clock.floatValue, SystemClock.uptimeMillis())) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnSignature()
                }
            }
        },
        interactive = false,
        contentPadding = 16.dp,
    )
}

/** Taps on the donut: each one kicks its spin, and five quick ones start the show of the name. */
@Stable
private class HeroTaps {
    /** Times on the decoration clock of the taps that speed up the donut. */
    private val kicks = mutableStateListOf<Float>()

    /** When the name began to show, on the decoration clock, or `null`. */
    var showStart by mutableStateOf<Float?>(null)
        private set

    private var count = 0
    private var lastTapMillis = 0L

    /** Counts a tap at [time] on the decoration clock; `true` when it starts the show. */
    fun tap(time: Float, uptimeMillis: Long): Boolean {
        val start = showStart
        // While the name shows, the taps wait; a still name ends by itself.
        if (start != null && time - start < SignatureShow.DURATION) return false
        kicks += time
        count = if (uptimeMillis - lastTapMillis <= TAP_GAP_MILLIS) count + 1 else 1
        lastTapMillis = uptimeMillis
        if (count < SIGNATURE_TAPS) return false
        count = 0
        showStart = time
        return true
    }

    fun endShow() {
        showStart = null
    }

    /** How far the taps have turned the donut ahead by [time], in seconds of its usual spin. */
    fun kick(time: Float): Float {
        var ahead = 0f
        for (tap in kicks) {
            if (time > tap) ahead += KICK_SECONDS * (1f - exp(-(time - tap) / KICK_DECAY_SECONDS))
        }
        return ahead
    }
}

/**
 * The frames of the hero: the donut, the name, or the donut dissolving into the name cell by cell.
 * Drawing again at the same moment, e.g. while it stands still, reuses the last frame.
 */
private class HeroFrames(private val shade: (Float) -> Int) {
    private val donut = Donut()
    private val name = BlockLetters(Signature.NAME, donut.columns, donut.rows)

    /** Every cell turns from the donut into the name at its own moment of the dissolve. */
    private val order = Random(DISSOLVE_SEED).let { random ->
        FloatArray(donut.columns * donut.rows) { random.nextFloat() }
    }

    private var spin = Float.NaN
    private var show = Float.NaN
    private var frame: AsciiArt? = null

    fun at(time: Float, taps: HeroTaps, animate: Boolean): AsciiArt {
        val start = taps.showStart
        // Seconds into the show, or NaN for the donut; a still show is a moment of its own.
        val show = when {
            start == null -> Float.NaN
            !animate -> STILL_SHOW
            time - start < SignatureShow.DURATION -> time - start
            else -> Float.NaN
        }
        val spin = time + taps.kick(time)
        frame?.let { last -> if (spin == this.spin && show.sameAs(this.show)) return last }
        val art = when {
            show.isNaN() -> donut.frame(spin, shade)
            show == STILL_SHOW -> name.frame(SignatureShow.STILL_YAW, SignatureShow.PITCH, shade)
            else -> {
                val letters = name.frame(SignatureShow.yaw(show), SignatureShow.PITCH, shade)
                val dissolve = SignatureShow.dissolve(show)
                if (dissolve >= 1f) letters else blend(donut.frame(spin, shade), letters, dissolve)
            }
        }
        this.spin = spin
        this.show = show
        frame = art
        return art
    }

    /** [from] with the cells of [to] whose turn has come at [progress]. */
    private fun blend(from: AsciiArt, to: AsciiArt, progress: Float): AsciiArt {
        val chars = CharArray(from.chars.size) { if (order[it] < progress) to.chars[it] else from.chars[it] }
        val colors = IntArray(from.colors.size) { if (order[it] < progress) to.colors[it] else from.colors[it] }
        return AsciiArt(from.columns, from.rows, chars, colors, from.cellAspect, isBraille = false)
    }

    private fun Float.sameAs(other: Float) = this == other || (isNaN() && other.isNaN())
}

/** Dark sides of the donut fade into the paper, lit ones get the full ink. */
private fun shader(dark: Color, bright: Color): (Float) -> Int =
    { light -> lerp(dark, bright, light.pow(0.8f)).toArgb() }

private const val START_TIME = 0.8f

/** Taps that start the show, one for every letter of the name, each within this time of the last. */
private const val SIGNATURE_TAPS = 5
private const val TAP_GAP_MILLIS = 600L

/** A tap turns the donut ahead by this much of its usual spin, most of it within the decay. */
private const val KICK_SECONDS = 0.9f
private const val KICK_DECAY_SECONDS = 0.35f

/** How long the name shows: turning, or standing still when decorations may not move. */
private const val SHOW_MILLIS = (SignatureShow.DURATION * 1000).toLong()
private const val STILL_MILLIS = 6_000L
private const val STILL_SHOW = -1f
private const val DISSOLVE_SEED = 5
