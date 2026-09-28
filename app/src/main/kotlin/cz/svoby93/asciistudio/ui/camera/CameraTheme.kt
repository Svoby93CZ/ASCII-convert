package cz.svoby93.asciistudio.ui.camera

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.svoby93.asciistudio.data.ArtPalette
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily

/**
 * Colours of the camera screen. All of them come from the art palette, so the windows, controls
 * and background change together with the art: green for Terminal, orange for Amber and so on.
 */
@Immutable
data class CameraColors(
    /** Glyph colour of the palette: borders, texts and accents. */
    val ink: Color,
    /** Background colour of the palette, used inside the windows. */
    val paper: Color,
    /** Behind the windows, a shade darker than [paper] so that the windows stand out. */
    val desk: Color,
    val isLight: Boolean,
) {
    /** Mixes [ink] into [paper]: 0 is pure paper, 1 is pure ink. */
    fun tint(amount: Float): Color = lerp(paper, ink, amount)
}

fun ArtPalette.cameraColors(): CameraColors {
    val ink = Color(foreground)
    val paper = Color(background)
    val desk = if (isLight) lerp(paper, ink, 0.12f) else lerp(paper, Color.Black, 0.5f)
    return CameraColors(ink = ink, paper = paper, desk = desk, isLight = isLight)
}

val LocalCameraColors = staticCompositionLocalOf { ArtPalette.TERMINAL.cameraColors() }

/** Small capitals of window titles and tabs. */
internal val TerminalLabelStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.sp,
)

/**
 * Material theme made of the palette colours, so that sliders, chips and switches of the camera
 * settings match the art as well.
 */
@Composable
fun CameraTheme(colors: CameraColors, content: @Composable () -> Unit) {
    val colorScheme = remember(colors) { colors.colorScheme() }
    val base = MaterialTheme.typography
    val typography = remember(base) {
        base.copy(titleSmall = base.titleSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold))
    }
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        CompositionLocalProvider(
            LocalCameraColors provides colors,
            LocalContentColor provides colors.ink,
            content = content,
        )
    }
}

private fun CameraColors.colorScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = paper,
        primaryContainer = tint(0.24f),
        onPrimaryContainer = ink,
        inversePrimary = paper,
        secondary = tint(0.8f),
        onSecondary = paper,
        secondaryContainer = tint(0.2f),
        onSecondaryContainer = ink,
        tertiary = tint(0.8f),
        onTertiary = paper,
        tertiaryContainer = tint(0.2f),
        onTertiaryContainer = ink,
        background = desk,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = tint(0.12f),
        onSurfaceVariant = tint(0.72f),
        surfaceTint = ink,
        inverseSurface = ink,
        inverseOnSurface = paper,
        outline = tint(0.5f),
        outlineVariant = tint(0.24f),
        surfaceBright = tint(0.1f),
        surfaceDim = paper,
        surfaceContainerLowest = paper,
        surfaceContainerLow = tint(0.04f),
        surfaceContainer = tint(0.07f),
        surfaceContainerHigh = tint(0.1f),
        surfaceContainerHighest = tint(0.14f),
    )
}
