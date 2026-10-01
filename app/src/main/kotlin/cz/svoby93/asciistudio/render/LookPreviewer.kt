package cz.svoby93.asciistudio.render

import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiConverter
import cz.svoby93.asciistudio.engine.PixelImage

/** Small previews of looks for the preset tiles, converted from a thumbnail of the picture. */
class LookPreviewer(private val optionsFactory: AsciiOptionsFactory) {

    /** [thumbnail] in the look of [settings]; it takes a fraction of a millisecond. */
    fun preview(thumbnail: PixelImage, settings: StudioSettings): AsciiArt =
        AsciiConverter.convert(thumbnail, optionsFactory.create(settings, PREVIEW_COLUMNS))

    companion object {
        /** Few columns, so that the glyphs stay recognisable on a small tile. */
        const val PREVIEW_COLUMNS = 28

        /** Longer side of the thumbnail: a few pixels for every column, and every Braille dot. */
        const val THUMBNAIL_SIZE = 128
    }
}
