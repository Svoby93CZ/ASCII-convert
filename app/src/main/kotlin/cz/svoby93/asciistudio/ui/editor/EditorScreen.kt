package cz.svoby93.asciistudio.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.FileFormat
import cz.svoby93.asciistudio.data.ImageFormat
import cz.svoby93.asciistudio.data.offeredLooks
import cz.svoby93.asciistudio.data.offeredPalettes
import cz.svoby93.asciistudio.ui.components.rememberArtViewportState
import cz.svoby93.asciistudio.ui.studio.HistoryActions
import cz.svoby93.asciistudio.ui.studio.statsLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: EditorViewModel = viewModel {
        EditorViewModel(
            savedStateHandle = createSavedStateHandle(),
            images = container.imageRepository,
            settingsRepository = container.settingsRepository,
            presetRepository = container.presetRepository,
            optionsFactory = container.optionsFactory,
            exporter = container.exporter,
            gallery = container.galleryRepository,
            hiddenFeatures = container.hiddenFeatures,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyStates.collectAsStateWithLifecycle()
    val userPresets by viewModel.userPresets.collectAsStateWithLifecycle()
    val thumbnail by viewModel.thumbnail.collectAsStateWithLifecycle()
    val signatureLook by container.hiddenFeatures.signatureLook.collectAsStateWithLifecycle()
    val developerMode by container.hiddenFeatures.developerMode.collectAsStateWithLifecycle()
    val conversionMillis by viewModel.conversionMillis.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // Effects wait in the channel while the app is in the background.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is EditorEffect.Launch -> try {
                        context.startActivity(effect.intent)
                    } catch (_: ActivityNotFoundException) {
                        launch { snackbarHostState.showSnackbar(resources.getString(R.string.message_export_failed)) }
                    }
                    is EditorEffect.Message -> launch { snackbarHostState.showSnackbar(resources.getString(effect.text)) }
                    is EditorEffect.Undoable -> launch {
                        val offer = launch {
                            val result = snackbarHostState.showSnackbar(
                                message = resources.getString(effect.text),
                                actionLabel = resources.getString(R.string.action_undo),
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                        }
                        // Any later change, undo or redo withdraws the offer, so that it never
                        // undoes something else. Cancelling showSnackbar removes the message.
                        val withdrawal = launch {
                            viewModel.historyStates.first { it.version != effect.version }
                            offer.cancel()
                        }
                        offer.join()
                        withdrawal.cancel()
                    }
                }
            }
        }
    }

    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var imageFormat by rememberSaveable { mutableStateOf(ImageFormat.ORIGINAL) }
    // The width of the art while the chat dialog offers a narrower copy.
    var chatColumns by rememberSaveable { mutableStateOf<Int?>(null) }

    // One "create document" launcher per format, because the MIME type is fixed per launcher.
    val saveDocument = FileFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            if (uri != null) viewModel.saveFile(uri, format)
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.saveToPictures(imageFormat)
        } else {
            viewModel.showMessage(R.string.message_storage_permission)
        }
    }
    val saveFile: (FileFormat) -> Unit = { format ->
        saveDocument.getValue(format).launch(viewModel.suggestedFileName(format))
    }

    EditorContent(
        state = state,
        showOriginal = showOriginal,
        onShowOriginalChange = { showOriginal = it },
        onBack = onBack,
        onSaveToGallery = viewModel::saveToGallery,
        onExport = { showExport = true },
        onCopy = viewModel::copyText,
        onReset = viewModel::resetSettings,
        onChange = viewModel::updateSettings,
        presets = PresetShelf(
            userPresets = userPresets,
            thumbnail = thumbnail,
            onSave = viewModel::savePreset,
            onDelete = viewModel::deletePreset,
            looks = offeredLooks(signatureLook),
        ),
        history = HistoryActions(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
        ),
        viewport = rememberArtViewportState(),
        snackbarHostState = snackbarHostState,
        palettes = offeredPalettes(signatureLook),
        developerStats = if (developerMode) editorStats(state, conversionMillis) else emptyList(),
    )

    if (showExport) {
        val art = state.art
        ExportSheet(
            imageFormat = imageFormat,
            onImageFormatChange = { imageFormat = it },
            imageSize = remember(art, imageFormat) { viewModel.imageSize(imageFormat) },
            onDismiss = { showExport = false },
            onAction = { action ->
                showExport = false
                when (action) {
                    ExportAction.SHARE_IMAGE -> viewModel.shareImage(imageFormat)
                    ExportAction.SAVE_PICTURES -> {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.saveToPictures(imageFormat)
                        }
                    }
                    ExportAction.COPY -> viewModel.copyText()
                    ExportAction.COPY_CHAT -> if (art != null && art.columns > CHAT_COLUMNS) {
                        chatColumns = art.columns
                    } else {
                        viewModel.copyForChat(CHAT_COLUMNS)
                    }
                    ExportAction.SHARE_TEXT -> viewModel.shareText()
                    ExportAction.SAVE_PDF -> saveFile(FileFormat.PDF)
                    ExportAction.SAVE_SVG -> saveFile(FileFormat.SVG)
                    ExportAction.SAVE_HTML -> saveFile(FileFormat.HTML)
                    ExportAction.SAVE_TXT -> saveFile(FileFormat.PLAIN)
                    ExportAction.SAVE_ANSI -> saveFile(FileFormat.ANSI)
                }
            },
        )
    }

    chatColumns?.let { columns ->
        ChatWidthDialog(
            columns = columns,
            onCopy = { width ->
                chatColumns = null
                viewModel.copyForChat(width)
            },
            onDismiss = { chatColumns = null },
        )
    }
}

/** For the developer mode: how long the last conversion took, from how many pixels to how many cells. */
private fun editorStats(state: EditorUiState, conversionMillis: Float?): List<String> {
    val art = state.art ?: return emptyList()
    val photo = (state.load as? EditorLoadState.Ready)?.preview ?: return emptyList()
    val millis = conversionMillis ?: return emptyList()
    return listOf(statsLine("%.1f ms · %d×%d px → %d×%d", millis, photo.width, photo.height, art.columns, art.rows))
}
