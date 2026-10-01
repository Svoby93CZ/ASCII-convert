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
import cz.svoby93.asciistudio.data.TextFormat
import cz.svoby93.asciistudio.ui.components.rememberArtViewportState
import cz.svoby93.asciistudio.ui.studio.HistoryActions
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
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyStates.collectAsStateWithLifecycle()
    val userPresets by viewModel.userPresets.collectAsStateWithLifecycle()
    val thumbnail by viewModel.thumbnail.collectAsStateWithLifecycle()
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

    // One "create document" launcher per format, because the MIME type is fixed per launcher.
    val saveDocument = TextFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            if (uri != null) viewModel.saveDocument(uri, format)
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveToPictures() else viewModel.showMessage(R.string.message_storage_permission)
    }

    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }

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
        ),
        history = HistoryActions(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
        ),
        viewport = rememberArtViewportState(),
        snackbarHostState = snackbarHostState,
    )

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onAction = { action ->
                showExport = false
                when (action) {
                    ExportAction.COPY -> viewModel.copyText()
                    ExportAction.SHARE_TEXT -> viewModel.shareText()
                    ExportAction.SHARE_IMAGE -> viewModel.shareImage()
                    ExportAction.SAVE_PICTURES -> {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.saveToPictures()
                        }
                    }
                    ExportAction.SAVE_TXT -> saveDocument.getValue(TextFormat.PLAIN)
                        .launch(viewModel.suggestedFileName(TextFormat.PLAIN))
                    ExportAction.SAVE_HTML -> saveDocument.getValue(TextFormat.HTML)
                        .launch(viewModel.suggestedFileName(TextFormat.HTML))
                    ExportAction.SAVE_ANSI -> saveDocument.getValue(TextFormat.ANSI)
                        .launch(viewModel.suggestedFileName(TextFormat.ANSI))
                }
            },
        )
    }
}
