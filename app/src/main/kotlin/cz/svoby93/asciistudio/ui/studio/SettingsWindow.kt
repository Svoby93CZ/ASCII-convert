package cz.svoby93.asciistudio.ui.studio

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.Backdrop
import cz.svoby93.asciistudio.data.CharsetPreset
import cz.svoby93.asciistudio.data.StudioSettings
import cz.svoby93.asciistudio.ui.editor.ColorControls
import cz.svoby93.asciistudio.ui.editor.PresetControls
import cz.svoby93.asciistudio.ui.editor.PresetShelf
import cz.svoby93.asciistudio.ui.editor.SectionLabel
import cz.svoby93.asciistudio.ui.editor.SettingsChange
import cz.svoby93.asciistudio.ui.editor.StyleControls
import cz.svoby93.asciistudio.ui.editor.ToneControls

enum class SettingsTab(@StringRes val label: Int) {
    PRESETS(R.string.tab_presets),
    STYLE(R.string.tab_style),
    COLORS(R.string.tab_colors),
    TONE(R.string.tab_tone),
    BACKGROUND(R.string.tab_background),
}

/** Undo and redo of the settings, offered in the border of the [SettingsWindow]. */
class HistoryActions(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
)

/**
 * The settings of the art in a [TerminalFrame], with tabs, plus the background of the screens.
 * The editor and the live camera share them, so changes carry over from one to the other.
 *
 * @param presets the presets of the user and a picture for their previews, see [PresetControls].
 * @param onExpandedChange lets the user fold the controls away to enlarge the art; without it the
 *   controls are always shown.
 * @param contentHeight height of the controls, or null to fill the height of the window.
 * @param history undo and redo, or null where the screen keeps no history.
 * @param charsets the character sets to offer, see [StyleControls].
 * @param columnsRange the widths to offer, see [StyleControls].
 */
@Composable
fun SettingsWindow(
    settings: StudioSettings,
    onChange: SettingsChange,
    presets: PresetShelf,
    tab: SettingsTab,
    onTabChange: (SettingsTab) -> Unit,
    expanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit)?,
    contentHeight: Dp?,
    modifier: Modifier = Modifier,
    history: HistoryActions? = null,
    charsets: List<CharsetPreset> = CharsetPreset.entries,
    columnsRange: IntRange = StudioSettings.MIN_COLUMNS..StudioSettings.MAX_COLUMNS,
) {
    val status: (@Composable RowScope.() -> Unit)? = if (onExpandedChange != null || history != null) {
        {
            if (history != null) {
                BorderButton("[↩]", R.string.action_undo, enabled = history.canUndo, onClick = history.onUndo)
                BorderButton("[↪]", R.string.action_redo, enabled = history.canRedo, onClick = history.onRedo)
            }
            if (onExpandedChange != null) {
                val description = if (expanded) R.string.settings_hide else R.string.settings_show
                BorderButton(if (expanded) "[-]" else "[+]", description, onClick = { onExpandedChange(!expanded) })
            }
        }
    } else {
        null
    }
    TerminalFrame(
        title = stringResource(R.string.settings_title),
        status = status,
        modifier = modifier,
    ) {
        val fill = contentHeight == null
        Column(if (fill) Modifier.fillMaxHeight() else Modifier) {
            TabStrip(
                selected = tab,
                onSelect = { selected ->
                    onTabChange(selected)
                    onExpandedChange?.invoke(true)
                },
            )
            AnimatedVisibility(
                visible = expanded || onExpandedChange == null,
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            ) {
                // Every tab starts at its top.
                val scroll = key(tab) { rememberScrollState() }
                val paper = LocalStudioColors.current.paper
                Box(
                    (if (contentHeight == null) Modifier.fillMaxHeight() else Modifier.height(contentHeight))
                        .fillMaxWidth()
                        .drawWithContent {
                            drawContent()
                            // The bottom edge fades out while there is more to scroll to.
                            if (scroll.canScrollForward) {
                                val fade = ScrollFade.toPx()
                                drawRect(
                                    Brush.verticalGradient(listOf(Color.Transparent, paper), startY = size.height - fade, endY = size.height),
                                    topLeft = Offset(0f, size.height - fade),
                                )
                            }
                        }
                        .verticalScroll(scroll)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    when (tab) {
                        SettingsTab.PRESETS -> PresetControls(settings, onChange, presets)
                        SettingsTab.STYLE -> StyleControls(settings, onChange, charsets, columnsRange)
                        SettingsTab.COLORS -> ColorControls(settings, onChange)
                        SettingsTab.TONE -> ToneControls(settings, onChange)
                        SettingsTab.BACKGROUND -> BackgroundControls(settings, onChange)
                    }
                }
            }
        }
    }
}

/** Tabs in inverse video, like the selection of a terminal program. */
@Composable
private fun TabStrip(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    val colors = LocalStudioColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        SettingsTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .clip(TabShape)
                    .background(if (isSelected) colors.ink else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = 7.dp),
            ) {
                Text(
                    stringResource(tab.label).uppercase(),
                    style = TerminalLabelStyle.copy(fontSize = 13.sp, letterSpacing = 0.3.sp),
                    color = if (isSelected) colors.paper else colors.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A `[-]` box in the border, like the buttons of old text windows. */
@Composable
private fun BorderButton(text: String, @StringRes description: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val label = stringResource(description)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(TabShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                if (!enabled) disabled()
            }
            .padding(horizontal = 2.dp),
    ) {
        Text(text, color = if (enabled) Color.Unspecified else LocalStudioColors.current.ink.copy(alpha = DISABLED_ALPHA))
    }
}

@Composable
private fun BackgroundControls(settings: StudioSettings, onChange: SettingsChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(R.string.background_label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Backdrop.entries.forEach { backdrop ->
                BackgroundTile(
                    backdrop = backdrop,
                    selected = settings.backdrop == backdrop,
                    onClick = { onChange { it.copy(backdrop = backdrop) } },
                )
            }
        }
        Text(
            stringResource(R.string.background_supporting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small still preview of a background, in the style of the palette swatches. */
@Composable
private fun BackgroundTile(backdrop: Backdrop, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalStudioColors.current
    val name = stringResource(backdrop.label)
    val shape = RoundedCornerShape(12.dp)
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
                .size(width = 56.dp, height = 72.dp)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.ink else colors.tint(0.3f), shape),
        ) {
            StudioBackdrop(backdrop, Modifier.matchParentSize(), animate = false, scale = 0.5f)
            // A tiny window shows that the pattern goes behind the windows.
            Box(
                Modifier
                    .size(width = 30.dp, height = 36.dp)
                    .background(colors.paper, MiniWindowShape)
                    .border(1.dp, colors.ink.copy(alpha = 0.8f), MiniWindowShape),
            )
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

private val TabShape = RoundedCornerShape(8.dp)
private const val DISABLED_ALPHA = 0.38f
private val MiniWindowShape = RoundedCornerShape(4.dp)
private val ScrollFade = 24.dp
