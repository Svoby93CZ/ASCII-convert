package cz.svoby93.asciistudio.data

import java.io.File

/**
 * An ASCII art the user saved to the gallery: the photo it was made from, the settings that turn
 * the photo into the art again, and a rendered preview.
 */
data class GalleryItem(
    val id: String,
    /** When the art was saved, in milliseconds since the epoch. */
    val createdAt: Long,
    val settings: StudioSettings,
    val columns: Int,
    val rows: Int,
    val source: File,
    val preview: File,
)
