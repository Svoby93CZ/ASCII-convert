package cz.svoby93.asciistudio.data

import androidx.annotation.StringRes
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import kotlinx.serialization.Serializable

/**
 * Looks that change the art in one tap. A look is a whole set of [StudioSettings], but applying it
 * keeps the width and the background, see [withLookOf]. Compared side by side on photos with
 * people, fruit and buildings, so that every look holds up on most pictures.
 */
enum class StylePreset(@StringRes val label: Int, val look: StudioSettings) {
    CLASSIC(R.string.preset_classic, StudioSettings()),
    MATRIX(
        R.string.preset_matrix,
        StudioSettings(charset = CharsetPreset.BINARY, contrast = 0.3f, sharpness = 0.5f),
    ),
    NEWSPAPER(
        R.string.preset_newspaper,
        StudioSettings(contrast = 0.3f, sharpness = 0.4f, dithering = Dithering.ATKINSON, palette = ArtPalette.PAPER),
    ),
    GAME_BOY(
        R.string.preset_game_boy,
        StudioSettings(
            charset = CharsetPreset.BLOCKS,
            contrast = 0.25f,
            dithering = Dithering.BAYER,
            palette = ArtPalette.LCD,
        ),
    ),
    BLUEPRINT(
        R.string.preset_blueprint,
        StudioSettings(edgeMode = EdgeMode.ONLY, edgeSensitivity = 0.85f, palette = ArtPalette.BLUEPRINT),
    ),
    NEON(
        R.string.preset_neon,
        StudioSettings(
            charset = CharsetPreset.DETAILED,
            contrast = 0.3f,
            edgeMode = EdgeMode.MIXED,
            edgeSensitivity = 0.65f,
            palette = ArtPalette.SYNTHWAVE,
        ),
    ),
    MOSAIC(
        R.string.preset_mosaic,
        StudioSettings(
            charset = CharsetPreset.DETAILED,
            colorMode = ColorMode.PHOTO,
            colorTiles = true,
            palette = ArtPalette.NIGHT,
        ),
    ),
    INK(
        R.string.preset_ink,
        StudioSettings(
            charset = CharsetPreset.BRAILLE,
            contrast = 0.2f,
            dithering = Dithering.ATKINSON,
            palette = ArtPalette.INK,
        ),
    ),
}

/** A look the user saved under a name of their own. */
@Serializable
data class UserPreset(val id: String, val name: String, val look: StudioSettings)

/**
 * These settings with the look of [style]: its characters, tones and colours. The width and the
 * background stay, and so do the custom characters unless [style] uses its own.
 */
fun StudioSettings.withLookOf(style: StudioSettings): StudioSettings = style.copy(
    columns = columns,
    backdrop = backdrop,
    customChars = if (style.charset == CharsetPreset.CUSTOM) style.customChars else customChars,
)

/** Whether these settings have the look of [style], whatever their width and background. */
fun StudioSettings.hasLookOf(style: StudioSettings): Boolean = withLookOf(style) == this
