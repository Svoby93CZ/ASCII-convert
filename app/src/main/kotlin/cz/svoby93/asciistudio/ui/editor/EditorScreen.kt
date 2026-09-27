package cz.svoby93.asciistudio.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.data.TextFormat
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.components.ArtViewportState
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.components.rememberArtViewportState
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: EditorViewModel = viewModel {
        EditorViewModel(
            savedStateHandle = createSavedStateHandle(),
            images = container.imageRepository,
            settingsRepository = container.settingsRepository,
            optionsFactory = container.optionsFactory,
            exporter = container.exporter,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                        launch { snackbarHostState.showSnackbar(context.getString(R.string.message_export_failed)) }
                    }
                    is EditorEffect.Message -> launch { snackbarHostState.showSnackbar(context.getString(effect.text)) }
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
        if (granted) viewModel.saveToGallery() else viewModel.showMessage(R.string.message_storage_permission)
    }

    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(EditorTab.STYLE) }
    val viewport = rememberArtViewportState()

    Scaffold(
        topBar = {
            EditorTopBar(
                state = state,
                showOriginal = showOriginal,
                onShowOriginalChange = { showOriginal = it },
                onBack = onBack,
                onCopy = viewModel::copyText,
                onExport = { showExport = true },
                onReset = viewModel::resetSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            // In portrait the controls take a bit over a third of the screen, the preview the rest.
            val panelHeight = (maxHeight * 0.38f).coerceIn(200.dp, 300.dp)
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                PreviewArea(
                    state = state,
                    showOriginal = showOriginal,
                    viewport = viewport,
                    onBack = onBack,
                    modifier = modifier,
                )
            }
            val panel: @Composable (Modifier) -> Unit = { modifier ->
                val settings = state.settings
                if (settings != null) {
                    ControlPanel(
                        settings = settings,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onChange = viewModel::updateSettings,
                        wide = wide,
                        contentHeight = panelHeight,
                        modifier = modifier,
                    )
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    preview(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)),
                    )
                    panel(
                        Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    panel(Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onAction = { action ->
                showExport = false
                when (action) {
                    ExportAction.COPY -> viewModel.copyText()
                    ExportAction.SHARE_TEXT -> viewModel.shareText()
                    ExportAction.SHARE_IMAGE -> viewModel.shareImage()
                    ExportAction.SAVE_GALLERY -> {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.saveToGallery()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state: EditorUiState,
    showOriginal: Boolean,
    onShowOriginalChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val art = state.art
    Column {
        TopAppBar(
            title = {
                Text(
                    if (art != null) stringResource(R.string.editor_size, art.columns, art.rows) else "",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconToggleButton(
                    checked = showOriginal,
                    onCheckedChange = onShowOriginalChange,
                    enabled = state.load is EditorLoadState.Ready,
                ) {
                    Icon(painterResource(R.drawable.ic_compare), contentDescription = stringResource(R.string.action_show_original))
                }
                IconButton(onClick = onCopy, enabled = art != null) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = stringResource(R.string.action_copy))
                }
                IconButton(onClick = onExport, enabled = art != null) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_export))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_reset)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_restart_alt), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onReset()
                            },
                        )
                    }
                }
            },
        )
        // Rendering a large PNG can take a moment; show that something is happening.
        AnimatedVisibility(visible = state.isExporting, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PreviewArea(
    state: EditorUiState,
    showOriginal: Boolean,
    viewport: ArtViewportState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = state.settings?.artStyle()
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.background(style?.let { Color(it.background) } ?: MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        when (val load = state.load) {
            EditorLoadState.Loading -> LoadingIndicator()
            is EditorLoadState.Failed -> LoadError(load.message, onBack)
            is EditorLoadState.Ready -> {
                Crossfade(targetState = showOriginal, label = "original") { original ->
                    if (original) {
                        Image(
                            bitmap = load.preview,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                    } else if (style != null) {
                        AsciiArtView(
                            art = state.art,
                            style = style,
                            viewport = viewport,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (state.art == null && !showOriginal) CircularProgressIndicator()
                AnimatedVisibility(
                    visible = viewport.isZoomed && !showOriginal,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    SmallFloatingActionButton(onClick = { scope.launch { viewport.reset() } }) {
                        Icon(painterResource(R.drawable.ic_fit_screen), contentDescription = stringResource(R.string.action_fit))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_image), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadError(message: Int, onBack: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp, modifier = Modifier.padding(24.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(painterResource(R.drawable.ic_no_photography), contentDescription = null)
            Text(stringResource(message), textAlign = TextAlign.Center)
            Button(onClick = onBack) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun ControlPanel(
    settings: StudioSettings,
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit,
    onChange: SettingsChange,
    wide: Boolean,
    contentHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = if (wide) RoundedCornerShape(topStart = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val insets = if (wide) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
    } else {
        WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.windowInsetsPadding(insets)) {
            EditorTabs(selectedTab, onTabSelected)
            val scroll = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
            Box(
                if (wide) {
                    Modifier.weight(1f)
                } else {
                    Modifier.height(contentHeight)
                },
            ) {
                Box(scroll) {
                    when (selectedTab) {
                        EditorTab.STYLE -> StyleControls(settings, onChange)
                        EditorTab.TONE -> ToneControls(settings, onChange)
                        EditorTab.COLORS -> ColorControls(settings, onChange)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTabs(selectedTab: EditorTab, onTabSelected: (EditorTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal, containerColor = Color.Transparent) {
        EditorTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = { Text(stringResource(tab.label)) },
            )
        }
    }
}
