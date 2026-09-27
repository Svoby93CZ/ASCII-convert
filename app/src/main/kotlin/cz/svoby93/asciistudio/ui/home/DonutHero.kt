package cz.svoby93.asciistudio.ui.home

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import kotlin.math.pow

/** The classic spinning ASCII donut, rendered by the same renderer as the user's art. */
@Composable
fun DonutHero(modifier: Modifier = Modifier) {
    val donut = remember { Donut() }
    var art by remember { mutableStateOf(donut.frame(START_TIME, ::shade)) }

    // Respect the system "remove animations" setting and show a still frame instead.
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    if (animate) {
        LaunchedEffect(donut) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> art = donut.frame(START_TIME + (now - start) / 1_000_000_000f, ::shade) }
            }
        }
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = HeroBackground) {
        AsciiArtView(
            art = art,
            style = ArtStyle(HeroBackground.toArgb(), HeroBright.toArgb(), colored = true),
            modifier = Modifier.fillMaxSize(),
            interactive = false,
            contentPadding = 16.dp,
        )
    }
}

private fun shade(light: Float): Int = lerp(HeroDark, HeroBright, light.pow(0.8f)).toArgb()

private const val START_TIME = 0.8f
private val HeroBackground = Color(0xFF0B120E)
private val HeroDark = Color(0xFF14735A)
private val HeroBright = Color(0xFFB9FFD8)
