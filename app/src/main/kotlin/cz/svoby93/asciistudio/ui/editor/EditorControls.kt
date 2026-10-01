package cz.svoby93.asciistudio.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.ArtPalette
import cz.svoby93.asciistudio.data.CharsetPreset
import cz.svoby93.asciistudio.data.ColorMode
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.engine.Dithering
import cz.svoby93.asciistudio.engine.EdgeMode
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

typealias SettingsChange = ((StudioSettings) -> StudioSettings) -> Unit

/**
 * @param charsets the character sets to offer; custom characters can only be edited when
 *   [CharsetPreset.CUSTOM] is among them.
 * @param columnsRange the widths to offer; a wider stored width is shown as the largest one.
 */
@Composable
fun StyleControls(
    settings: StudioSettings,
    onChange: SettingsChange,
    charsets: List<CharsetPreset> = CharsetPreset.entries,
    columnsRange: IntRange = StudioSettings.MIN_COLUMNS..StudioSettings.MAX_COLUMNS,
) {
    val columns = settings.columns.coerceIn(columnsRange)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(R.string.label_charset)
        ChipRow {
            charsets.forEach { preset ->
                FilterChip(
                    selected = settings.charset == preset,
                    onClick = { onChange { it.copy(charset = preset) } },
                    label = { Text(stringResource(preset.label)) },
                )
            }
        }
        if (settings.charset == CharsetPreset.CUSTOM && CharsetPreset.CUSTOM in charsets) {
            CustomCharsField(settings.customChars) { chars -> onChange { it.copy(customChars = chars) } }
        }
        LabeledSlider(
            label = stringResource(R.string.label_columns),
            valueText = stringResource(R.string.value_columns, columns),
            value = columns.toFloat(),
            valueRange = columnsRange.first.toFloat()..columnsRange.last.toFloat(),
            onValueChange = { value -> onChange { it.copy(columns = value.roundToInt()) } },
            onReset = { onChange { it.copy(columns = Defaults.columns) } },
        )
        SectionLabel(R.string.label_edges)
        SegmentedChoice(
            options = EdgeMode.entries,
            selected = settings.edgeMode,
            label = { mode ->
                when (mode) {
                    EdgeMode.OFF -> R.string.edges_off
                    EdgeMode.MIXED -> R.string.edges_mixed
                    EdgeMode.ONLY -> R.string.edges_only
                }
            },
            onSelect = { mode -> onChange { it.copy(edgeMode = mode) } },
        )
        if (settings.edgeMode != EdgeMode.OFF) {
            PercentSlider(
                label = R.string.label_edge_sensitivity,
                value = settings.edgeSensitivity,
                valueRange = 0f..1f,
                onValueChange = { value -> onChange { it.copy(edgeSensitivity = value) } },
                onReset = { onChange { it.copy(edgeSensitivity = Defaults.edgeSensitivity) } },
            )
        }
    }
}

@Composable
fun ToneControls(settings: StudioSettings, onChange: SettingsChange) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PercentSlider(
            label = R.string.label_brightness,
            value = settings.brightness,
            valueRange = -1f..1f,
            onValueChange = { value -> onChange { it.copy(brightness = value) } },
            onReset = { onChange { it.copy(brightness = Defaults.brightness) } },
        )
        PercentSlider(
            label = R.string.label_contrast,
            value = settings.contrast,
            valueRange = -1f..1f,
            onValueChange = { value -> onChange { it.copy(contrast = value) } },
            onReset = { onChange { it.copy(contrast = Defaults.contrast) } },
        )
        PercentSlider(
            label = R.string.label_sharpness,
            value = settings.sharpness,
            valueRange = 0f..1f,
            onValueChange = { value -> onChange { it.copy(sharpness = value) } },
            onReset = { onChange { it.copy(sharpness = Defaults.sharpness) } },
        )
        SwitchRow(
            title = R.string.label_auto_levels,
            supporting = R.string.auto_levels_supporting,
            checked = settings.autoLevels,
            onCheckedChange = { checked -> onChange { it.copy(autoLevels = checked) } },
        )
        SwitchRow(
            title = R.string.label_invert,
            supporting = R.string.invert_supporting,
            checked = settings.invert,
            onCheckedChange = { checked -> onChange { it.copy(invert = checked) } },
        )
        SectionLabel(R.string.label_dithering)
        ChipRow {
            Dithering.entries.forEach { dithering ->
                FilterChip(
                    selected = settings.dithering == dithering,
                    onClick = { onChange { it.copy(dithering = dithering) } },
                    label = {
                        Text(
                            stringResource(
                                when (dithering) {
                                    Dithering.NONE -> R.string.dithering_none
                                    Dithering.FLOYD_STEINBERG -> R.string.dithering_floyd_steinberg
                                    Dithering.ATKINSON -> R.string.dithering_atkinson
                                    Dithering.BAYER -> R.string.dithering_bayer
                                },
                            ),
                        )
                    },
                )
            }
        }
        Text(
            stringResource(R.string.slider_reset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ColorControls(settings: StudioSettings, onChange: SettingsChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(R.string.label_color_mode)
        SegmentedChoice(
            options = ColorMode.entries,
            selected = settings.colorMode,
            label = { mode ->
                when (mode) {
                    ColorMode.PALETTE -> R.string.color_mode_palette
                    ColorMode.PHOTO -> R.string.color_mode_photo
                }
            },
            onSelect = { mode -> onChange { it.copy(colorMode = mode) } },
        )
        SectionLabel(R.string.label_palette)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtPalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = settings.palette == palette,
                    onClick = { onChange { it.copy(palette = palette) } },
                )
            }
        }
        // Below the palettes, which are used far more often and must stay in view in the camera.
        SwitchRow(
            title = R.string.label_color_tiles,
            supporting = R.string.color_tiles_supporting,
            checked = settings.colorTiles,
            onCheckedChange = { checked -> onChange { it.copy(colorTiles = checked) } },
        )
    }
}

@Composable
private fun PaletteSwatch(palette: ArtPalette, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(palette.label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = name
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .border(
                    BorderStroke(
                        if (selected) 3.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    CircleShape,
                )
                .padding(4.dp)
                .background(Color(palette.background), CircleShape),
        ) {
            Text("@", color = Color(palette.foreground), fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CustomCharsField(value: String, onValueChange: (String) -> Unit) {
    // Local state keeps typing smooth; the settings follow every edit.
    var text by rememberSaveable { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(stringResource(R.string.custom_chars_label)) },
        supportingText = { Text(stringResource(R.string.custom_chars_supporting)) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontFamily = MonoFontFamily),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PercentSlider(
    @StringRes label: Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    LabeledSlider(
        label = stringResource(label),
        valueText = stringResource(R.string.value_percent, (value * 100).roundToInt()),
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onReset = onReset,
    )
}

/** A slider with its label and value; a double tap, or the accessibility action, brings back the default. */
@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val resetLabel = stringResource(R.string.slider_reset)
    val currentOnReset by rememberUpdatedState(onReset)
    Column(Modifier.observeDoubleTap { currentOnReset() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(resetLabel) {
                        currentOnReset()
                        true
                    },
                )
            },
        )
    }
}

/**
 * Calls [onDoubleTap] after two quick taps in about the same place. The taps still reach the
 * children, so a slider moves as usual, and the call comes after the slider has handled them.
 */
private fun Modifier.observeDoubleTap(onDoubleTap: () -> Unit): Modifier = pointerInput(Unit) {
    var lastTapMillis = 0L
    var lastTapPosition: Offset? = null
    val slop = DoubleTapSlop.toPx()
    val scope = CoroutineScope(currentCoroutineContext())
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // A tap ends close to where it began and before a long press would start.
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var change: PointerInputChange
            do {
                change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull null
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull null
                }
            } while (change.pressed)
            change
        }
        val previous = lastTapPosition
        when {
            up == null -> lastTapPosition = null
            previous != null &&
                down.uptimeMillis - lastTapMillis <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - previous).getDistance() <= slop -> {
                lastTapPosition = null
                scope.launch { onDoubleTap() }
            }
            else -> {
                lastTapMillis = up.uptimeMillis
                lastTapPosition = up.position
            }
        }
    }
}

/** The defaults that a double tap brings back. */
private val Defaults = StudioSettings()

/** How far apart the two taps of a double tap may be. */
private val DoubleTapSlop = 48.dp

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes supporting: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row toggles, the switch only mirrors the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label(option)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
internal fun SectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
