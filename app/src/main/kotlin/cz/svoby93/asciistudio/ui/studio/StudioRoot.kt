package cz.svoby93.asciistudio.ui.studio

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cz.svoby93.asciistudio.data.ArtPalette
import cz.svoby93.asciistudio.data.StudioSettings

/**
 * The frame of every screen: the theme made of the art palette and the background behind the
 * windows. The background stays in place while the screens change on top of it.
 *
 * @param settings `null` while the stored settings are being read.
 */
@Composable
fun StudioRoot(settings: StudioSettings?, content: @Composable () -> Unit) {
    val palette = settings?.palette ?: ArtPalette.TERMINAL
    val colors = remember(palette) { palette.studioColors() }
    // Respect the system "remove animations" setting: moving backgrounds and blinking stop.
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    SystemBarsAppearance(light = colors.isLight)
    StudioTheme(colors, animate) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.desk),
        ) {
            if (settings != null) StudioBackdrop(settings.backdrop, Modifier.fillMaxSize())
            content()
        }
    }
}

/** Dark status and navigation bar icons on light palettes and light icons on dark ones. */
@Composable
private fun SystemBarsAppearance(light: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, light) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
