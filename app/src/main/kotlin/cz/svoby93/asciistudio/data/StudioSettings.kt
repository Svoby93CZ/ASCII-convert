package cz.svoby93.asciistudio.data

import androidx.annotation.StringRes
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode

enum class CharsetPreset(@StringRes val label: Int) {
    STANDARD(R.string.charset_standard),
    DETAILED(R.string.charset_detailed),
    BLOCKS(R.string.charset_blocks),
    BRAILLE(R.string.charset_braille),
    BINARY(R.string.charset_binary),
    CUSTOM(R.string.charset_custom),
}

enum class ColorMode {
    /** Every glyph uses the palette's foreground colour. */
    PALETTE,

    /** Every glyph keeps the colour of the part of the photo it represents. */
    PHOTO,
}

/** Background and ink colours of the art. Light palettes print dark ink on light paper. */
enum class ArtPalette(
    @StringRes val label: Int,
    val background: Int,
    val foreground: Int,
    val isLight: Boolean,
) {
    TERMINAL(R.string.palette_terminal, 0xFF0B120E.toInt(), 0xFF5CF08F.toInt(), isLight = false),
    AMBER(R.string.palette_amber, 0xFF150E03.toInt(), 0xFFFFB547.toInt(), isLight = false),
    NIGHT(R.string.palette_night, 0xFF111318.toInt(), 0xFFE6E8EE.toInt(), isLight = false),
    SYNTHWAVE(R.string.palette_synthwave, 0xFF1B0B33.toInt(), 0xFFFF71CE.toInt(), isLight = false),
    BLUEPRINT(R.string.palette_blueprint, 0xFF0E3A73.toInt(), 0xFFDCEBFF.toInt(), isLight = false),
    PAPER(R.string.palette_paper, 0xFFF4EEE1.toInt(), 0xFF2B2620.toInt(), isLight = true),
    INK(R.string.palette_ink, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), isLight = true),
}

/** Everything the user can tweak; persisted between sessions. */
data class StudioSettings(
    val columns: Int = DEFAULT_COLUMNS,
    val charset: CharsetPreset = CharsetPreset.STANDARD,
    val customChars: String = DEFAULT_CUSTOM_CHARS,
    val brightness: Float = 0f,
    val contrast: Float = 0.15f,
    val sharpness: Float = 0.3f,
    val autoLevels: Boolean = true,
    val invert: Boolean = false,
    val dithering: Dithering = Dithering.NONE,
    val edgeMode: EdgeMode = EdgeMode.OFF,
    val edgeSensitivity: Float = 0.55f,
    val colorMode: ColorMode = ColorMode.PALETTE,
    val palette: ArtPalette = ArtPalette.TERMINAL,
) {
    companion object {
        const val MIN_COLUMNS = 20
        const val MAX_COLUMNS = 300
        const val DEFAULT_COLUMNS = 110
        const val DEFAULT_CUSTOM_CHARS = " .:oO8@"
    }
}
