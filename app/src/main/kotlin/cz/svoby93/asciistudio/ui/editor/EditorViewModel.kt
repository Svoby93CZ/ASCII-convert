package cz.svoby93.asciistudio.ui.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.ArtExporter
import cz.svoby93.asciistudio.data.ImageRepository
import cz.svoby93.asciistudio.data.SettingsRepository
import cz.svoby93.asciistudio.data.SourceImage
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.data.TextFormat
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.AsciiConverter
import cz.svoby93.asciistudio.engine.AsciiOptions
import cz.svoby93.asciistudio.render.AsciiOptionsFactory
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EditorLoadState {
    data object Loading : EditorLoadState
    class Ready(val image: SourceImage) : EditorLoadState {
        val preview: ImageBitmap = image.bitmap.asImageBitmap()
    }
    data class Failed(@StringRes val message: Int) : EditorLoadState
}

data class EditorUiState(
    val load: EditorLoadState = EditorLoadState.Loading,
    val settings: StudioSettings? = null,
    val art: AsciiArt? = null,
    val isExporting: Boolean = false,
)

sealed interface EditorEffect {
    /** Start an activity, e.g. the share sheet. */
    data class Launch(val intent: Intent) : EditorEffect

    data class Message(@StringRes val text: Int) : EditorEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val images: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val optionsFactory: AsciiOptionsFactory,
    private val exporter: ArtExporter,
) : ViewModel() {

    private val loadState = MutableStateFlow<EditorLoadState>(EditorLoadState.Loading)
    private val exporting = MutableStateFlow(false)
    private val effectChannel = Channel<EditorEffect>(Channel.BUFFERED)

    val effects: Flow<EditorEffect> = effectChannel.receiveAsFlow()

    /** Re-converts whenever the image or a setting that affects the glyphs changes. */
    private val art: StateFlow<AsciiArt?> = combine(
        loadState.mapNotNull { (it as? EditorLoadState.Ready)?.image },
        settingsRepository.settings.filterNotNull(),
    ) { image, settings -> ConversionRequest(image, optionsFactory.create(settings)) }
        // Palette tweaks only change colours, so they do not trigger a new conversion.
        .distinctUntilChanged()
        .mapLatest { request -> AsciiConverter.convert(request.image.pixels, request.options) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<EditorUiState> = combine(
        loadState,
        settingsRepository.settings,
        art,
        exporting,
    ) { load, settings, art, exporting -> EditorUiState(load, settings, art, exporting) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EditorUiState())

    init {
        viewModelScope.launch { loadState.value = load() }
    }

    private suspend fun load(): EditorLoadState {
        val uri = savedStateHandle.toRoute<EditorRoute>().imageUri
        // After a process restart the picked image is restored from the saved copy instead,
        // because the permission to read the original URI is gone by then.
        val importPending = uri != null && savedStateHandle.get<Boolean>(KEY_IMPORTED) != true
        return try {
            val image = if (importPending) {
                images.importImage(Uri.parse(uri)).also { savedStateHandle[KEY_IMPORTED] = true }
            } else {
                images.restore()
            }
            if (image != null) EditorLoadState.Ready(image) else EditorLoadState.Failed(R.string.error_no_image)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            EditorLoadState.Failed(R.string.error_load_image)
        }
    }

    fun updateSettings(transform: (StudioSettings) -> StudioSettings) = settingsRepository.update(transform)

    fun resetSettings() = settingsRepository.update { StudioSettings() }

    fun copyText() {
        val art = art.value ?: return
        exporter.copyToClipboard(art)
        // Android 13+ confirms clipboard copies itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) send(EditorEffect.Message(R.string.message_copied))
    }

    fun shareText() {
        val art = art.value ?: return
        send(EditorEffect.Launch(exporter.shareTextIntent(art)))
    }

    fun shareImage() = export { art, settings ->
        send(EditorEffect.Launch(exporter.shareImageIntent(art, settings.artStyle())))
    }

    fun saveToGallery() = export { art, settings ->
        exporter.saveToGallery(art, settings.artStyle())
        send(EditorEffect.Message(R.string.message_saved_gallery))
    }

    fun suggestedFileName(format: TextFormat): String = exporter.suggestedFileName(format)

    fun saveDocument(uri: Uri, format: TextFormat) = export { art, settings ->
        val content = withContext(Dispatchers.Default) { exporter.text(art, settings.artStyle(), format) }
        exporter.writeDocument(uri, content)
        send(EditorEffect.Message(R.string.message_saved_file))
    }

    fun showMessage(@StringRes text: Int) = send(EditorEffect.Message(text))

    private fun export(block: suspend (AsciiArt, StudioSettings) -> Unit) {
        val art = art.value ?: return
        val settings = settingsRepository.settings.value ?: return
        viewModelScope.launch {
            exporting.update { true }
            try {
                block(art, settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                send(EditorEffect.Message(R.string.message_export_failed))
            } finally {
                exporting.update { false }
            }
        }
    }

    private fun send(effect: EditorEffect) {
        effectChannel.trySend(effect)
    }

    private data class ConversionRequest(val image: SourceImage, val options: AsciiOptions)

    private companion object {
        const val KEY_IMPORTED = "imported"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
