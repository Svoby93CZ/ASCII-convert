package cz.svoby93.asciistudio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.components.ArtViewportState
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.components.rememberArtViewportState
import cz.svoby93.asciistudio.ui.studio.BlinkingDot
import cz.svoby93.asciistudio.ui.studio.RoundButton
import cz.svoby93.asciistudio.ui.studio.SettingsTab
import cz.svoby93.asciistudio.ui.studio.SettingsWindow
import cz.svoby93.asciistudio.ui.studio.StudioTopBar
import cz.svoby93.asciistudio.ui.studio.TerminalFrame
import cz.svoby93.asciistudio.ui.studio.TerminalLine
import kotlinx.coroutines.launch

/**
 * The editor apart from loading and exporting: a window with the art and a window with its
 * settings, like the live camera. In landscape the settings sit next to the art.
 */
@Composable
fun EditorContent(
    state: EditorUiState,
    showOriginal: Boolean,
    onShowOriginalChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaveToGallery: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
    onChange: SettingsChange,
    modifier: Modifier = Modifier,
    viewport: ArtViewportState = rememberArtViewportState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.STYLE) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    // Without an image there is nothing to adjust.
    val settings = state.settings?.takeIf { state.load !is EditorLoadState.Failed }
    val topBar: @Composable () -> Unit = {
        EditorTopBar(state, showOriginal, onShowOriginalChange, onBack, onSaveToGallery, onExport, onCopy, onReset)
    }
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val width = maxWidth
            val height = maxHeight
            if (width > height && width >= 480.dp) {
                // Landscape: the art on the left, the settings in a column on the right.
                Row(Modifier.fillMaxSize()) {
                    ArtWindow(
                        state = state,
                        showOriginal = showOriginal,
                        viewport = viewport,
                        onBack = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, top = 8.dp, bottom = 16.dp, end = 8.dp),
                    )
                    Column(
                        Modifier
                            .width((width * 0.42f).coerceIn(300.dp, 420.dp))
                            .fillMaxHeight(),
                    ) {
                        topBar()
                        if (settings != null) {
                            SettingsWindow(
                                settings = settings,
                                onChange = onChange,
                                tab = tab,
                                onTabChange = { tab = it },
                                expanded = true,
                                onExpandedChange = null,
                                contentHeight = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = 720.dp),
                ) {
                    topBar()
                    ArtWindow(
                        state = state,
                        showOriginal = showOriginal,
                        viewport = viewport,
                        onBack = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = if (settings == null) 16.dp else 0.dp),
                    )
                    if (settings != null) {
                        SettingsWindow(
                            settings = settings,
                            onChange = onChange,
                            tab = tab,
                            onTabChange = { tab = it },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            contentHeight = (height * 0.3f).coerceIn(170.dp, 300.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun EditorTopBar(
    state: EditorUiState,
    showOriginal: Boolean,
    onShowOriginalChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaveToGallery: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit,
) {
    val hasArt = state.art != null
    StudioTopBar(title = stringResource(R.string.editor_title), onBack = onBack) {
        RoundButton(
            icon = R.drawable.ic_compare,
            description = R.string.action_show_original,
            checked = showOriginal,
            enabled = state.load is EditorLoadState.Ready,
            onClick = { onShowOriginalChange(!showOriginal) },
        )
        RoundButton(
            icon = if (state.isSaved) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark_add,
            description = if (state.isSaved) R.string.action_saved_in_gallery else R.string.action_save_to_gallery,
            checked = state.isSaved,
            enabled = hasArt && !state.isBusy,
            onClick = onSaveToGallery,
        )
        RoundButton(
            icon = R.drawable.ic_share,
            description = R.string.action_export,
            enabled = hasArt,
            onClick = onExport,
        )
        MoreMenu(hasArt = hasArt, onCopy = onCopy, onReset = onReset)
    }
}

@Composable
private fun MoreMenu(hasArt: Boolean, onCopy: () -> Unit, onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(icon = R.drawable.ic_more_vert, description = R.string.action_more, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                enabled = hasArt,
                onClick = {
                    open = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_reset)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_restart_alt), contentDescription = null) },
                onClick = {
                    open = false
                    onReset()
                },
            )
        }
    }
}

/** The art, or the photo while the user compares, in a window with the size of the art below. */
@Composable
private fun ArtWindow(
    state: EditorUiState,
    showOriginal: Boolean,
    viewport: ArtViewportState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val art = state.art
    val busy = stringResource(R.string.editor_busy)
    val status: (@Composable RowScope.() -> Unit)? = if (state.isBusy) {
        { BlinkingDot(busy) }
    } else {
        null
    }
    val charset = settings?.let { stringResource(it.charset.label).uppercase() }
    val footer: (@Composable RowScope.() -> Unit)? = if (art != null && charset != null && !showOriginal) {
        { Text("$charset · ${art.columns}×${art.rows}", maxLines = 1) }
    } else {
        null
    }
    val scope = rememberCoroutineScope()
    TerminalFrame(
        title = stringResource(if (showOriginal) R.string.editor_original else R.string.editor_art),
        status = status,
        footer = footer,
        modifier = modifier,
    ) {
        when (val load = state.load) {
            EditorLoadState.Loading -> {
                TerminalLine(stringResource(R.string.loading_image), Modifier.align(Alignment.Center))
            }
            is EditorLoadState.Failed -> LoadError(load.message, onBack, Modifier.align(Alignment.Center))
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
                    } else if (settings != null) {
                        AsciiArtView(
                            art = art,
                            style = settings.artStyle(),
                            viewport = viewport,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (art == null && !showOriginal) {
                    TerminalLine(stringResource(R.string.editor_converting), Modifier.align(Alignment.Center))
                }
                AnimatedVisibility(
                    visible = viewport.isZoomed && !showOriginal,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    RoundButton(
                        icon = R.drawable.ic_fit_screen,
                        description = R.string.action_fit,
                        size = 40.dp,
                        onClick = { scope.launch { viewport.reset() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadError(message: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_no_photography), contentDescription = null, modifier = Modifier.size(40.dp))
        Text(stringResource(message), textAlign = TextAlign.Center)
        Button(onClick = onBack) { Text(stringResource(R.string.action_retry)) }
    }
}
