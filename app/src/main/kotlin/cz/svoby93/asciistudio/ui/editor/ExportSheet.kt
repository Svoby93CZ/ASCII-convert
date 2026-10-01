package cz.svoby93.asciistudio.ui.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.ImageFormat
import cz.svoby93.asciistudio.ui.studio.TerminalDialog
import kotlinx.coroutines.launch

enum class ExportAction(@StringRes val title: Int, @StringRes val supporting: Int?, @DrawableRes val icon: Int) {
    SHARE_IMAGE(R.string.export_share_image, null, R.drawable.ic_image),
    SAVE_PICTURES(R.string.export_save_pictures, null, R.drawable.ic_download),
    COPY(R.string.action_copy, R.string.export_copy_supporting, R.drawable.ic_content_copy),
    COPY_CHAT(R.string.export_copy_chat, R.string.export_copy_chat_supporting, R.drawable.ic_chat),
    SHARE_TEXT(R.string.export_share_text, null, R.drawable.ic_share),
    SAVE_PDF(R.string.export_save_pdf, R.string.export_pdf_supporting, R.drawable.ic_picture_as_pdf),
    SAVE_SVG(R.string.export_save_svg, R.string.export_svg_supporting, R.drawable.ic_polyline),
    SAVE_HTML(R.string.export_save_html, R.string.export_html_supporting, R.drawable.ic_code),
    SAVE_TXT(R.string.export_save_txt, null, R.drawable.ic_description),
    SAVE_ANSI(R.string.export_save_ansi, R.string.export_ansi_supporting, R.drawable.ic_terminal),
}

/** About as many characters as a chat bubble on a phone shows on a line. */
const val CHAT_COLUMNS = 32

/**
 * The ways out of the editor: pictures in a [imageFormat] of the user's choice, text, and files.
 *
 * @param imageSize the size in pixels of the picture in [imageFormat], when there is art.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    imageFormat: ImageFormat,
    onImageFormatChange: (ImageFormat) -> Unit,
    imageSize: Pair<Int, Int>?,
    onDismiss: () -> Unit,
    onAction: (ExportAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val act: (ExportAction) -> Unit = { action ->
        // Let the sheet slide away before handing over to another screen.
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.export_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Section(R.string.export_section_image)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                ImageFormat.entries.forEach { format ->
                    FilterChip(
                        selected = format == imageFormat,
                        onClick = { onImageFormatChange(format) },
                        label = { Text(stringResource(format.label)) },
                    )
                }
            }
            if (imageSize != null) {
                Text(
                    stringResource(R.string.export_image_size, imageSize.first, imageSize.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            ActionItem(ExportAction.SHARE_IMAGE, act)
            ActionItem(ExportAction.SAVE_PICTURES, act)
            Section(R.string.export_section_text)
            ActionItem(ExportAction.COPY, act)
            ActionItem(ExportAction.COPY_CHAT, act)
            ActionItem(ExportAction.SHARE_TEXT, act)
            Section(R.string.export_section_files)
            ActionItem(ExportAction.SAVE_PDF, act)
            ActionItem(ExportAction.SAVE_SVG, act)
            ActionItem(ExportAction.SAVE_HTML, act)
            ActionItem(ExportAction.SAVE_TXT, act)
            ActionItem(ExportAction.SAVE_ANSI, act)
        }
    }
}

@Composable
private fun Section(@StringRes title: Int) {
    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
        SectionLabel(title)
    }
}

@Composable
private fun ActionItem(action: ExportAction, onAction: (ExportAction) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(action.title)) },
        supportingContent = action.supporting?.let { supporting -> { Text(stringResource(supporting)) } },
        leadingContent = { Icon(painterResource(action.icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onAction(action) },
    )
}

/** Offers a narrower copy, because art wider than a chat bubble breaks into wrapped lines. */
@Composable
fun ChatWidthDialog(columns: Int, onCopy: (columns: Int) -> Unit, onDismiss: () -> Unit) {
    TerminalDialog(
        title = stringResource(R.string.chat_width_title),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = { onCopy(columns) }) { Text(stringResource(R.string.chat_width_keep)) }
            Button(onClick = { onCopy(CHAT_COLUMNS) }) {
                Text(stringResource(R.string.chat_width_narrow, CHAT_COLUMNS))
            }
        },
    ) {
        Text(stringResource(R.string.chat_width_text, columns))
    }
}
