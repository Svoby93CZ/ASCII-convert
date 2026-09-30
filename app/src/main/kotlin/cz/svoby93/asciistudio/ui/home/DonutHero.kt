package cz.svoby93.asciistudio.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.engine.Donut
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.GlyphColors
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.studio.LocalAnimationsEnabled
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import kotlin.math.pow

/**
 * The classic spinning ASCII donut in the colours of the palette, rendered by the same renderer as
 * the user's art. Stands still when the system asks for no animations.
 */
@Composable
fun DonutHero(modifier: Modifier = Modifier) {
    val colors = LocalStudioColors.current
    val donut = remember { Donut() }
    val shade = remember(colors) { shader(dark = colors.tint(0.3f), bright = colors.ink) }
    var time by remember { mutableFloatStateOf(START_TIME) }
    val art = remember(time, shade) { donut.frame(time, shade) }

    if (LocalAnimationsEnabled.current) {
        LaunchedEffect(donut) {
            val start = withFrameNanos { it } - ((time - START_TIME) * NANOS_PER_SECOND).toLong()
            while (true) {
                withFrameNanos { now -> time = START_TIME + (now - start) / NANOS_PER_SECOND }
            }
        }
    }

    AsciiArtView(
        art = art,
        style = ArtStyle(colors.paper.toArgb(), colors.ink.toArgb(), GlyphColors.ART),
        modifier = modifier,
        interactive = false,
        contentPadding = 16.dp,
    )
}

/** Dark sides of the donut fade into the paper, lit ones get the full ink. */
private fun shader(dark: Color, bright: Color): (Float) -> Int =
    { light -> lerp(dark, bright, light.pow(0.8f)).toArgb() }

private const val START_TIME = 0.8f
private const val NANOS_PER_SECOND = 1_000_000_000f
