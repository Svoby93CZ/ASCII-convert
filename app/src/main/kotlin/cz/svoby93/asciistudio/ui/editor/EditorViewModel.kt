package cz.svoby93.asciistudio.ui.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.ArtExporter
import cz.svoby93.asciistudio.data.GalleryItem
import cz.svoby93.asciistudio.data.GalleryRepository
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.PresetRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.SourceImage
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.data.FileFormat
import cz.svoby93.asciistudio.data.ImageFormat
import cz.svoby93.asciistudio.data.UserPreset
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiConverter
import cz.svoby93.asciistudio.engine.AsciiExport
import cz.svoby93.asciistudio.engine.AsciiOptions
import cz.svoby93.asciistudio.engine.CachingConverter
import cz.svoby93.asciistudio.engine.PixelImage
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
import cz.svoby93.asciistudio.render.LookPreviewer
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.EditorRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EditorEffect {
    /** Start an activity, e.g. the share sheet. */
    data class Launch(val intent: Intent) : EditorEffect

    data class Message(@StringRes val text: Int) : EditorEffect

    /** A message with an action that undoes what just happened, while the history is still at [version]. */
    data class Undoable(@StringRes val text: Int, val version: Long) : EditorEffect
}

/**
 * Whether the settings of the editor can be undone or redone. [version] counts the changes, undos
 * and redos, so that an offer to undo one of them can tell when something else came after it.
 */
data class HistoryState(val canUndo: Boolean = false, val canRedo: Boolean = false, val version: Long = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val images: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val presetRepository: PresetRepository,
    private val optionsFactory: AsciiOptionsFactory,
    private val exporter: ArtExporter,
    private val gallery: GalleryRepository,
) : ViewModel() {

    private val loadState = MutableStateFlow<EditorLoadState>(EditorLoadState.Loading)
    private val source = MutableStateFlow<SourceImage?>(null)
    private val busy = MutableStateFlow(false)
    private val effectChannel = Channel<EditorEffect>(Channel.BUFFERED)

    /** Keeps the samples of the photo, so that tone sliders do not read all of its pixels again. */
    private val converter = CachingConverter()

    private val history = SettingsHistory()
    private val historyState = MutableStateFlow(HistoryState())

    /** Undo and redo of the settings while this editor is open. */
    val historyStates: StateFlow<HistoryState> = historyState.asStateFlow()

    /** The presets the user saved; empty while they are being read. */
    val userPresets: StateFlow<List<UserPreset>> = presetRepository.presets
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** A small copy of the photo for the previews of the presets. */
    val thumbnail: StateFlow<PixelImage?> = source
        .map { image -> image?.pixels?.thumbnail(LookPreviewer.THUMBNAIL_SIZE) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The gallery item last saved or opened for this photo; it survives a process restart. */
    private val savedItemId: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_SAVED_ITEM, null)

    val effects: Flow<EditorEffect> = effectChannel.receiveAsFlow()

    /** Re-converts whenever the image or a setting that affects the glyphs changes. */
    private val art: StateFlow<AsciiArt?> = combine(
        source.filterNotNull(),
        settingsRepository.settings.filterNotNull(),
    ) { image, settings -> ConversionRequest(image, optionsFactory.create(settings)) }
        // Palette tweaks only change colours, so they do not trigger a new conversion.
        .distinctUntilChanged()
        .mapLatest { request -> converter.convert(request.image.pixels, request.options) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val saved: Flow<Boolean> = combine(
        savedItemId,
        gallery.items,
        settingsRepository.settings,
    ) { id, items, settings -> isSaved(items?.firstOrNull { it.id == id }, settings) }

    val uiState: StateFlow<EditorUiState> = combine(
        loadState,
        settingsRepository.settings,
        art,
        busy,
        saved,
    ) { load, settings, art, busy, saved -> EditorUiState(load, settings, art, busy, saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EditorUiState())

    init {
        viewModelScope.launch {
            loadState.value = load()
            gallery.load()
        }
    }

    private suspend fun load(): EditorLoadState {
        val route = savedStateHandle.toRoute<EditorRoute>()
        // After a process restart the image is restored from the saved copy instead, because the
        // permission to read the original URI is gone by then.
        val importPending = savedStateHandle.get<Boolean>(KEY_IMPORTED) != true
        return try {
            val image = when {
                importPending && route.galleryId != null -> openFromGallery(route.galleryId)
                    ?: return EditorLoadState.Failed(R.string.error_gallery_item)
                importPending && route.imageUri != null -> images.importImage(Uri.parse(route.imageUri))
                else -> images.restore()
            }
            savedStateHandle[KEY_IMPORTED] = true
            if (image == null) return EditorLoadState.Failed(R.string.error_no_image)
            source.value = image
            EditorLoadState.Ready(image.bitmap.asImageBitmap())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Cannot open image ${route.imageUri ?: route.galleryId}", error)
            EditorLoadState.Failed(R.string.error_load_image)
        }
    }

    /** Makes the photo of a gallery item the current image and brings back its settings. */
    private suspend fun openFromGallery(id: String): SourceImage? {
        val item = gallery.find(id) ?: return null
        val image = images.importImage(Uri.fromFile(item.source))
        // Updates are ignored until the stored settings have been read.
        settingsRepository.settings.filterNotNull().first()
        // The background decorates the app, it is not part of the art.
        settingsRepository.update { current -> item.settings.copy(backdrop = current.backdrop) }
        savedStateHandle[KEY_SAVED_ITEM] = item.id
        return image
    }

    /** Changes the settings; changes in quick succession, like one drag of a slider, undo together. */
    fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        val before = settingsRepository.settings.value ?: return
        val after = transform(before)
        if (after == before) return
        history.changed(before, SystemClock.uptimeMillis())
        settingsRepository.update { after }
        publishHistory()
    }

    /** Brings back the default settings as a step of its own, which the message can undo. */
    fun resetSettings() {
        val before = settingsRepository.settings.value ?: return
        val after = StudioSettings(backdrop = before.backdrop)
        if (after == before) return
        history.close()
        history.changed(before, SystemClock.uptimeMillis())
        history.close()
        settingsRepository.update { after }
        publishHistory()
        send(EditorEffect.Undoable(R.string.message_settings_reset, historyState.value.version))
    }

    fun undo() = travel(history::undo)

    fun redo() = travel(history::redo)

    private fun travel(step: (StudioSettings) -> StudioSettings?) {
        val current = settingsRepository.settings.value ?: return
        step(current)?.let { target -> settingsRepository.update { target } }
        // Even without a step the history may have dropped steps that changed nothing.
        publishHistory()
    }

    private fun publishHistory() {
        historyState.update { HistoryState(history.canUndo, history.canRedo, it.version + 1) }
    }

    fun savePreset(name: String) {
        val settings = settingsRepository.settings.value ?: return
        presetRepository.add(name, settings)
    }

    fun deletePreset(preset: UserPreset) = presetRepository.delete(preset.id)

    fun copyText() {
        val art = art.value ?: return
        exporter.copyToClipboard(art.toText())
        confirmCopy()
    }

    /**
     * Copies the art in a code block for chat apps. Narrower [columns] than the art has convert
     * the photo again at that width in the same look, so that the lines fit a chat bubble.
     */
    fun copyForChat(columns: Int) {
        val image = source.value ?: return
        export { art, settings ->
            val chatArt = if (columns < art.columns) {
                withContext(Dispatchers.Default) {
                    AsciiConverter.convert(image.pixels, optionsFactory.create(settings, columns))
                }
            } else {
                art
            }
            exporter.copyToClipboard(AsciiExport.toChat(chatArt))
            confirmCopy()
        }
    }

    private fun confirmCopy() {
        // Android 13+ confirms clipboard copies itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) send(EditorEffect.Message(R.string.message_copied))
    }

    fun shareText() {
        val art = art.value ?: return
        send(EditorEffect.Launch(exporter.shareTextIntent(art)))
    }

    fun shareImage(format: ImageFormat) = export { art, settings ->
        send(EditorEffect.Launch(exporter.shareImageIntent(art, settings.artStyle(), format)))
    }

    fun saveToPictures(format: ImageFormat) = export { art, settings ->
        exporter.saveToPictures(art, settings.artStyle(), format)
        send(EditorEffect.Message(R.string.message_saved_pictures))
    }

    /** The size in pixels of the picture of the current art in [format], or `null` without art. */
    fun imageSize(format: ImageFormat): Pair<Int, Int>? = art.value?.let { exporter.imageSize(it, format) }

    /** Keeps the photo and the current settings in the gallery, unless they are there already. */
    fun saveToGallery() {
        // A second tap while the first save runs would add the same art twice.
        if (busy.value) return
        val image = source.value ?: return
        val savedItem = gallery.items.value?.firstOrNull { it.id == savedItemId.value }
        if (isSaved(savedItem, settingsRepository.settings.value)) {
            send(EditorEffect.Message(R.string.message_already_in_gallery))
            return
        }
        export(failure = R.string.message_gallery_failed) { art, settings ->
            val item = gallery.add(
                settings = settings,
                columns = art.columns,
                rows = art.rows,
                writeSource = image::writeTo,
                writePreview = { stream -> exporter.writePreview(art, settings.artStyle(), stream) },
            )
            savedStateHandle[KEY_SAVED_ITEM] = item.id
            send(EditorEffect.Message(R.string.message_saved_to_gallery))
        }
    }

    fun suggestedFileName(format: FileFormat): String = exporter.suggestedFileName(format)

    fun saveFile(uri: Uri, format: FileFormat) = export { art, settings ->
        exporter.writeFile(uri, art, settings.artStyle(), format)
        send(EditorEffect.Message(R.string.message_saved_file))
    }

    fun showMessage(@StringRes text: Int) = send(EditorEffect.Message(text))

    private fun export(
        @StringRes failure: Int = R.string.message_export_failed,
        block: suspend (AsciiArt, StudioSettings) -> Unit,
    ) {
        val art = art.value ?: return
        val settings = settingsRepository.settings.value ?: return
        viewModelScope.launch {
            busy.update { true }
            try {
                block(art, settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Export failed", error)
                send(EditorEffect.Message(failure))
            } finally {
                busy.update { false }
            }
        }
    }

    private fun isSaved(item: GalleryItem?, settings: StudioSettings?): Boolean =
        item != null && settings != null && item.settings.sameArtAs(settings)

    private fun send(effect: EditorEffect) {
        effectChannel.trySend(effect)
    }

    private data class ConversionRequest(val image: SourceImage, val options: AsciiOptions)

    private companion object {
        const val TAG = "AsciiStudio"
        const val KEY_IMPORTED = "imported"
        const val KEY_SAVED_ITEM = "savedItem"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
