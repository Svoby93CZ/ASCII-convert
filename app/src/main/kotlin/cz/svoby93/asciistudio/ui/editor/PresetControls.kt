package cz.svoby93.asciistudio.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.PresetRepository
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.data.StylePreset
import cz.svoby93.asciistudio.data.UserPreset
import cz.svoby93.asciistudio.data.hasLookOf
import cz.svoby93.asciistudio.data.withLookOf
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.engine.PixelImage
import cz.svoby93.asciistudio.render.artStyle
import cz.svoby93.asciistudio.ui.components.AsciiArtView
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import cz.svoby93.asciistudio.ui.studio.TerminalDialog
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the presets need besides the settings: the presets the user saved, a small copy of the
 * picture for the previews, and how to save and delete presets.
 */
@Stable
class PresetShelf(
    val userPresets: List<UserPreset>,
    /** `null` while there is no picture yet; the tiles then show only their colours. */
    val thumbnail: PixelImage?,
    val onSave: (name: String) -> Unit,
    val onDelete: (UserPreset) -> Unit,
)

/** Built-in looks and the user's own, each as a tile with the picture in that look. */
@Composable
fun PresetControls(settings: StudioSettings, onChange: SettingsChange, shelf: PresetShelf) {
    var saving by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UserPreset?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TileRow {
            StylePreset.entries.forEach { preset ->
                PresetTile(
                    name = stringResource(preset.label),
                    look = preset.look,
                    thumbnail = shelf.thumbnail,
                    selected = settings.hasLookOf(preset.look),
                    onClick = { onChange { it.withLookOf(preset.look) } },
                )
            }
        }
        SectionLabel(R.string.label_my_presets)
        TileRow {
            shelf.userPresets.forEach { preset ->
                PresetTile(
                    name = preset.name,
                    look = preset.look,
                    thumbnail = shelf.thumbnail,
                    selected = settings.hasLookOf(preset.look),
                    onClick = { onChange { it.withLookOf(preset.look) } },
                    onLongClick = { deleting = preset },
                    longClickLabel = stringResource(R.string.preset_delete),
                )
            }
            NewPresetTile(onClick = { saving = true })
        }
        Text(
            stringResource(R.string.presets_supporting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (saving) {
        SavePresetDialog(
            defaultName = stringResource(R.string.preset_default_name, shelf.userPresets.size + 1),
            onSave = { name ->
                saving = false
                shelf.onSave(name)
            },
            onDismiss = { saving = false },
        )
    }
    deleting?.let { preset ->
        DeletePresetDialog(
            name = preset.name,
            onDelete = {
                deleting = null
                shelf.onDelete(preset)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TileRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetTile(
    name: String,
    look: StudioSettings,
    thumbnail: PixelImage?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val colors = LocalStudioColors.current
    val preview = rememberLookPreview(thumbnail, look)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TileSize)
            .combinedClickable(
                role = Role.RadioButton,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel,
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = name
            },
    ) {
        Box(
            Modifier
                .size(TileSize)
                .clip(TileShape)
                .background(Color(look.palette.background))
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.ink else colors.tint(0.3f), TileShape),
        ) {
            AsciiArtView(
                art = preview,
                style = look.artStyle(),
                interactive = false,
                contentPadding = 6.dp,
                modifier = Modifier.matchParentSize(),
            )
        }
        TileLabel(name)
    }
}

/** A tile that keeps the current look as a preset of the user's own. */
@Composable
private fun NewPresetTile(onClick: () -> Unit) {
    val colors = LocalStudioColors.current
    val name = stringResource(R.string.preset_new)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TileSize)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(TileSize)
                .clip(TileShape)
                .border(1.dp, colors.outline, TileShape),
        ) {
            Text("+", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
        TileLabel(name)
    }
}

@Composable
private fun TileLabel(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** [thumbnail] in [look], converted off the main thread; the last preview stays until the next is ready. */
@Composable
private fun rememberLookPreview(thumbnail: PixelImage?, look: StudioSettings): AsciiArt? {
    val previewer = LocalAppContainer.current.lookPreviewer
    val preview by produceState<AsciiArt?>(null, thumbnail, look) {
        if (thumbnail != null) value = withContext(Dispatchers.Default) { previewer.preview(thumbnail, look) }
    }
    return preview
}

@Composable
private fun SavePresetDialog(defaultName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    TerminalDialog(
        title = stringResource(R.string.preset_save_title),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.preset_save))
            }
        },
    ) {
        Text(stringResource(R.string.preset_save_text))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(PresetRepository.MAX_NAME_LENGTH) },
            label = { Text(stringResource(R.string.preset_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeletePresetDialog(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    TerminalDialog(
        title = stringResource(R.string.preset_delete_title),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onDelete) { Text(stringResource(R.string.preset_delete)) }
        },
    ) {
        Text(stringResource(R.string.preset_delete_text, name))
    }
}

private val TileSize = 72.dp
private val TileShape = RoundedCornerShape(12.dp)
