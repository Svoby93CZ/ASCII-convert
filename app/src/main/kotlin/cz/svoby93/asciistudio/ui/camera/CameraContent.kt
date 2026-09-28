package cz.svoby93.asciistudio.ui.camera

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.ColorMode
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.editor.SettingsChange
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily

/**
 * The live camera screen apart from the camera itself: a window with the live art and a window
 * with its settings over a background in the colours of the palette, plus the shutter.
 * Expects [CameraTheme] around it.
 */
@Composable
fun CameraContent(
    art: AsciiArt?,
    settings: StudioSettings,
    frontCamera: Boolean,
    cameraUnavailable: Boolean,
    capturing: Boolean,
    animate: Boolean,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onChange: SettingsChange,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var tab by rememberSaveable { mutableStateOf(CameraTab.STYLE) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val onPhotoColorsChange = { photo: Boolean ->
        onChange { it.copy(colorMode = if (photo) ColorMode.PHOTO else ColorMode.PALETTE) }
    }
    Box(modifier.fillMaxSize()) {
        CameraBackdrop(settings.cameraBackground, animate, Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val width = maxWidth
            val height = maxHeight
            if (width > height && width >= 480.dp) {
                // Landscape: the camera on the left, settings and shutter in a column on the right.
                Row(Modifier.fillMaxSize()) {
                    CameraWindow(
                        art = art,
                        settings = settings,
                        frontCamera = frontCamera,
                        unavailable = cameraUnavailable,
                        animate = animate,
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
                        CameraTopBar(settings.colorMode == ColorMode.PHOTO, onPhotoColorsChange, onBack)
                        CameraSettingsWindow(
                            settings = settings,
                            onChange = onChange,
                            tab = tab,
                            onTabChange = { tab = it },
                            expanded = true,
                            onExpandedChange = null,
                            contentHeight = null,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, end = 16.dp),
                        )
                        ShutterBar(!capturing && !cameraUnavailable, onCapture, onSwitchCamera, buttonSize = 48.dp)
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = 720.dp),
                ) {
                    CameraTopBar(settings.colorMode == ColorMode.PHOTO, onPhotoColorsChange, onBack)
                    CameraWindow(
                        art = art,
                        settings = settings,
                        frontCamera = frontCamera,
                        unavailable = cameraUnavailable,
                        animate = animate,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                    CameraSettingsWindow(
                        settings = settings,
                        onChange = onChange,
                        tab = tab,
                        onTabChange = { tab = it },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        contentHeight = (height * 0.24f).coerceIn(150.dp, 260.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                    ShutterBar(!capturing && !cameraUnavailable, onCapture, onSwitchCamera)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun CameraTopBar(photoColors: Boolean, onPhotoColorsChange: (Boolean) -> Unit, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        RoundButton(icon = R.drawable.ic_arrow_back, description = R.string.action_back, onClick = onBack)
        Text(
            stringResource(R.string.action_live_camera),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RoundButton(
            icon = R.drawable.ic_palette,
            description = R.string.camera_photo_colors,
            checked = photoColors,
            onClick = { onPhotoColorsChange(!photoColors) },
        )
    }
}

@Composable
private fun CameraWindow(
    art: AsciiArt?,
    settings: StudioSettings,
    frontCamera: Boolean,
    unavailable: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCameraColors.current
    val live = stringResource(R.string.camera_live)
    val charset = stringResource(settings.charset.label).uppercase()
    val status: (@Composable RowScope.() -> Unit)? = if (art != null && !unavailable) {
        { BlinkingDot(live, animate) }
    } else {
        null
    }
    val footer: (@Composable RowScope.() -> Unit)? = if (art != null) {
        { Text("$charset · ${art.columns}×${art.rows}", maxLines = 1) }
    } else {
        null
    }
    TerminalFrame(
        title = stringResource(if (frontCamera) R.string.camera_front_lens else R.string.camera_back_lens),
        status = status,
        footer = footer,
        modifier = modifier,
    ) {
        AsciiArtView(
            art = art,
            style = settings.artStyle(),
            modifier = Modifier.fillMaxSize(),
            interactive = false,
            contentPadding = 16.dp,
        )
        ViewfinderCorners(
            color = colors.ink.copy(alpha = 0.75f),
            modifier = Modifier
                .matchParentSize()
                .padding(10.dp),
        )
        if (unavailable) {
            Text(
                stringResource(R.string.camera_unavailable),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        } else if (art == null) {
            TerminalLine(stringResource(R.string.camera_starting), animate, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ShutterBar(
    enabled: Boolean,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    buttonSize: Dp = 52.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Spacer(Modifier.size(buttonSize))
        ShutterButton(enabled = enabled, onClick = onCapture)
        RoundButton(
            icon = R.drawable.ic_cameraswitch,
            description = R.string.camera_switch,
            size = buttonSize,
            onClick = onSwitchCamera,
        )
    }
}

/** The shutter as a glowing ring of ink that shrinks a little while pressed. */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalCameraColors.current
    val label = stringResource(R.string.camera_capture)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val glow = colors.ink.copy(alpha = if (enabled) 0.35f else 0.1f)
                val radius = size.minDimension * 0.8f
                drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), radius = radius), radius = radius)
            }
            .clip(CircleShape)
            .border(3.dp, colors.ink.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(colors.ink.copy(alpha = if (enabled) 1f else 0.35f)),
        )
    }
}

/** A round outlined button; with [checked] it is a toggle that turns to inverse video when on. */
@Composable
private fun RoundButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    checked: Boolean? = null,
) {
    val colors = LocalCameraColors.current
    val label = stringResource(description)
    val on = checked == true
    val interaction = if (checked == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) colors.ink else colors.paper.copy(alpha = 0.85f))
            .border(1.5.dp, colors.ink.copy(alpha = if (on) 1f else 0.6f), CircleShape)
            .then(interaction)
            .semantics { contentDescription = label },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (on) colors.paper else colors.ink,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
