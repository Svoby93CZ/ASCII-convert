package cz.svoby93.asciistudio.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.Donut
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.GlyphColors
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import cz.svoby93.asciistudio.ui.studio.rememberDecorationClock
import kotlin.math.pow

/**
 * The classic spinning ASCII donut in the colours of the palette, rendered by the same renderer as
 * the user's art. Frames are computed while drawing, so the donut turns without composing again.
 * Stands still when decorations may not move.
 */
@Composable
fun DonutHero(modifier: Modifier = Modifier) {
    val colors = LocalStudioColors.current
    val frames = remember(colors) { DonutFrames(shader(dark = colors.tint(0.3f), bright = colors.ink)) }
    val clock = rememberDecorationClock(start = START_TIME)

    AsciiArtView(
        art = { frames.at(clock.floatValue) },
        style = ArtStyle(colors.paper.toArgb(), colors.ink.toArgb(), GlyphColors.ART),
        modifier = modifier,
        interactive = false,
        contentPadding = 16.dp,
    )
}

/** The frames of a donut; drawing again at the same time, e.g. while it stands still, reuses one. */
private class DonutFrames(private val shade: (Float) -> Int) {
    private val donut = Donut()
    private var time = Float.NaN
    private var frame: AsciiArt? = null

    fun at(time: Float): AsciiArt = frame?.takeIf { time == this.time } ?: donut.frame(time, shade).also {
        this.time = time
        frame = it
    }
}

/** Dark sides of the donut fade into the paper, lit ones get the full ink. */
private fun shader(dark: Color, bright: Color): (Float) -> Int =
    { light -> lerp(dark, bright, light.pow(0.8f)).toArgb() }

private const val START_TIME = 0.8f
