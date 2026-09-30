package cz.svoby93.asciistudio.render

import cz.svoby93.asciistudio.data.CharsetPreset
import cz.svoby93.asciistudio.data.ColorMode
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.AsciiOptions
import cz.svoby93.asciistudio.engine.CharRamps
import cz.svoby93.asciistudio.engine.GlyphSet

/** Translates user-facing [StudioSettings] into engine [AsciiOptions]. */
class AsciiOptionsFactory(
    private val glyphMeasurer: GlyphMeasurer,
    private val textCellAspect: Float,
) {
    fun create(settings: StudioSettings, columns: Int = settings.columns): AsciiOptions = AsciiOptions(
        columns = columns,
        glyphs = glyphsFor(settings),
        cellAspect = textCellAspect,
        brightness = settings.brightness,
        contrast = settings.contrast,
        sharpness = settings.sharpness,
        autoLevels = settings.autoLevels,
        // Ink always contrasts with the paper: light palettes put dense glyphs on dark areas.
        invert = settings.invert != settings.palette.isLight,
        dithering = settings.dithering,
        edgeMode = settings.edgeMode,
        edgeSensitivity = settings.edgeSensitivity,
    )

    private fun glyphsFor(settings: StudioSettings): GlyphSet = when (settings.charset) {
        CharsetPreset.STANDARD -> GlyphSet.Ramp(CharRamps.STANDARD)
        CharsetPreset.DETAILED -> GlyphSet.Ramp(CharRamps.DETAILED)
        CharsetPreset.BLOCKS -> GlyphSet.Ramp(CharRamps.BLOCKS)
        CharsetPreset.BINARY -> GlyphSet.Ramp(CharRamps.BINARY)
        CharsetPreset.BRAILLE -> GlyphSet.Braille
        CharsetPreset.CUSTOM -> glyphMeasurer.rampFor(settings.customChars)?.let { GlyphSet.Ramp(it) }
            ?: GlyphSet.Ramp(CharRamps.STANDARD)
    }
}

/** Colours for rendering art with the given settings. */
fun StudioSettings.artStyle(): ArtStyle = ArtStyle(
    background = palette.background,
    foreground = palette.foreground,
    glyphColors = if (colorMode == ColorMode.PHOTO) GlyphColors.PHOTO else GlyphColors.INK,
    tiles = colorTiles,
)
