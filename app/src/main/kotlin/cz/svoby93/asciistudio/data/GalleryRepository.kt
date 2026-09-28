package cz.svoby93.asciistudio.data

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps the ASCII art the user saved. Every item is a folder in [directory] with the photo, a
 * preview and the settings as JSON, so the art can be opened in the editor again.
 *
 * Items are written to a temporary folder first and renamed when complete, so an interrupted save
 * never shows up as a broken item.
 */
class GalleryRepository(
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<GalleryItem>?>(null)

    /** Newest first; `null` until [load] has read the folder. */
    val items: StateFlow<List<GalleryItem>?> = state.asStateFlow()

    /** Reads the folder the first time it is called; later calls return the items in memory. */
    suspend fun load(): List<GalleryItem> = withContext(dispatcher) {
        mutex.withLock { state.value ?: readAll().also { state.value = it } }
    }

    suspend fun find(id: String): GalleryItem? = load().firstOrNull { it.id == id }

    /**
     * Saves a new item. [writeSource] writes the photo and [writePreview] the preview, in any
     * format BitmapFactory reads; both run on a background thread.
     */
    suspend fun add(
        settings: StudioSettings,
        columns: Int,
        rows: Int,
        writeSource: (OutputStream) -> Unit,
        writePreview: (OutputStream) -> Unit,
        createdAt: Long = System.currentTimeMillis(),
    ): GalleryItem {
        load()
        return withContext(dispatcher) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val temp = File(directory, id + TEMP_SUFFIX)
                val folder = File(directory, id)
                try {
                    if (!temp.mkdirs()) throw IOException("Cannot create $temp")
                    File(temp, SOURCE).outputStream().buffered().use(writeSource)
                    File(temp, PREVIEW).outputStream().buffered().use(writePreview)
                    val metadata = Metadata(createdAt, columns, rows, settings)
                    File(temp, METADATA).writeText(json.encodeToString(Metadata.serializer(), metadata))
                    if (!temp.renameTo(folder)) throw IOException("Cannot move $temp")
                } catch (error: Throwable) {
                    temp.deleteRecursively()
                    throw error
                }
                val item = GalleryItem(
                    id = id,
                    createdAt = createdAt,
                    settings = settings,
                    columns = columns,
                    rows = rows,
                    source = File(folder, SOURCE),
                    preview = File(folder, PREVIEW),
                )
                state.value = listOf(item) + state.value.orEmpty()
                item
            }
        }
    }

    suspend fun delete(id: String) {
        require(id.matches(ID_PATTERN)) { "Not a gallery id: $id" }
        withContext(dispatcher) {
            mutex.withLock {
                val folder = File(directory, id)
                if (folder.exists() && !folder.deleteRecursively()) throw IOException("Cannot delete $folder")
                state.value = state.value?.filterNot { it.id == id }
            }
        }
    }

    private fun readAll(): List<GalleryItem> = directory.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { folder ->
            if (folder.name.endsWith(TEMP_SUFFIX)) {
                // Left behind by a save that was interrupted.
                folder.deleteRecursively()
                null
            } else {
                read(folder)
            }
        }
        .sortedByDescending { it.createdAt }

    private fun read(folder: File): GalleryItem? {
        if (!folder.name.matches(ID_PATTERN)) return null
        val source = File(folder, SOURCE)
        val preview = File(folder, PREVIEW)
        if (!source.isFile || !preview.isFile) return null
        val metadata = try {
            json.decodeFromString(Metadata.serializer(), File(folder, METADATA).readText())
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            // Not valid JSON, or required fields are missing.
            return null
        }
        val settings = metadata.settings.let {
            it.copy(columns = it.columns.coerceIn(StudioSettings.MIN_COLUMNS, StudioSettings.MAX_COLUMNS))
        }
        return GalleryItem(folder.name, metadata.createdAt, settings, metadata.columns, metadata.rows, source, preview)
    }

    @Serializable
    private data class Metadata(
        val createdAt: Long,
        val columns: Int,
        val rows: Int,
        val settings: StudioSettings,
        val version: Int = VERSION,
    )

    private companion object {
        const val VERSION = 1
        const val METADATA = "item.json"
        const val SOURCE = "photo.img"
        const val PREVIEW = "preview.img"
        const val TEMP_SUFFIX = ".tmp"
        val ID_PATTERN = Regex("[0-9a-f-]{36}")

        /** Unknown properties are skipped, unknown enum values fall back to their defaults. */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}
