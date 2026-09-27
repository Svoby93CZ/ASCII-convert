package cz.svoby93.asciistudio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cz.svoby93.asciistudio.R

/** JetBrains Mono gives headings the same character as the art itself. */
val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006D43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF93F7BD),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D6),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF5FBF4),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FBF4),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C0),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2EB),
    inversePrimary = Color(0xFF77DAA2),
    surfaceDim = Color(0xFFD6DCD5),
    surfaceBright = Color(0xFFF5FBF4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EE),
    surfaceContainer = Color(0xFFE9EFE8),
    surfaceContainerHigh = Color(0xFFE3EAE3),
    surfaceContainerHighest = Color(0xFFDEE4DD),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF77DAA2),
    onPrimary = Color(0xFF003921),
    primaryContainer = Color(0xFF005232),
    onPrimaryContainer = Color(0xFF93F7BD),
    secondary = Color(0xFFB4CCBA),
    onSecondary = Color(0xFF203528),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFD0E8D6),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBFE9F8),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DD),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DD),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DD),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF006D43),
    surfaceDim = Color(0xFF0F1511),
    surfaceBright = Color(0xFF353B37),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632),
)

private val AppTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
    )
}

/** Material 3 theme; follows the wallpaper colours (Material You) on Android 12 and newer. */
@Composable
fun AsciiStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
