package cz.svoby93.asciistudio.data

import androidx.annotation.StringRes
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import kotlinx.serialization.Serializable

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

    /** Every glyph takes the hue of the part of the photo it represents. */
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
    RUBY(R.string.palette_ruby, 0xFF16060A.toInt(), 0xFFFF5C77.toInt(), isLight = false),
    ICE(R.string.palette_ice, 0xFF04141B.toInt(), 0xFF7CF2FF.toInt(), isLight = false),
    NIGHT(R.string.palette_night, 0xFF111318.toInt(), 0xFFE6E8EE.toInt(), isLight = false),
    SYNTHWAVE(R.string.palette_synthwave, 0xFF1B0B33.toInt(), 0xFFFF71CE.toInt(), isLight = false),
    BLUEPRINT(R.string.palette_blueprint, 0xFF0E3A73.toInt(), 0xFFDCEBFF.toInt(), isLight = false),
    LCD(R.string.palette_lcd, 0xFF9BBC0F.toInt(), 0xFF0F380F.toInt(), isLight = true),
    PAPER(R.string.palette_paper, 0xFFF4EEE1.toInt(), 0xFF2B2620.toInt(), isLight = true),
    INK(R.string.palette_ink, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), isLight = true),
}

/** Decoration of the screens behind their windows, drawn in the colours of the palette. */
enum class Backdrop(@StringRes val label: Int) {
    /** Characters that get denser towards the edges of the screen, like a vignette. */
    ASCII(R.string.background_ascii),

    /** Falling streams of characters. */
    RAIN(R.string.background_rain),

    /** Fine grid lines with brighter crosses. */
    GRID(R.string.background_grid),

    /** Scanlines and a dark vignette of an old monitor. */
    CRT(R.string.background_crt),

    NONE(R.string.background_none),
}

/**
 * Everything the user can tweak; persisted between sessions. Gallery items keep a copy as JSON, so
 * renamed properties fall back to their defaults there.
 */
@Serializable
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
    /** A muted colour of the photo behind every glyph, like a mosaic. */
    val colorTiles: Boolean = false,
    val palette: ArtPalette = ArtPalette.TERMINAL,
    val backdrop: Backdrop = Backdrop.ASCII,
) {
    /** The same art, whatever the decoration of the screens around it. */
    fun sameArtAs(other: StudioSettings): Boolean = copy(backdrop = other.backdrop) == other

    companion object {
        const val MIN_COLUMNS = 20
        const val MAX_COLUMNS = 300
        const val DEFAULT_COLUMNS = 110

        /** The live camera converts at most this many columns to keep the preview smooth. */
        const val MAX_LIVE_COLUMNS = 160

        const val DEFAULT_CUSTOM_CHARS = " .:oO8@"
    }
}
