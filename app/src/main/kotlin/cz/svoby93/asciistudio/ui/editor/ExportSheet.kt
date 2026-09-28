package cz.svoby93.asciistudio.ui.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import kotlinx.coroutines.launch

enum class ExportAction(@StringRes val title: Int, @StringRes val supporting: Int?, @DrawableRes val icon: Int) {
    COPY(R.string.action_copy, R.string.export_copy_supporting, R.drawable.ic_content_copy),
    SHARE_TEXT(R.string.export_share_text, null, R.drawable.ic_share),
    SHARE_IMAGE(R.string.export_share_image, R.string.export_image_supporting, R.drawable.ic_image),
    SAVE_PICTURES(R.string.export_save_pictures, null, R.drawable.ic_download),
    SAVE_TXT(R.string.export_save_txt, null, R.drawable.ic_description),
    SAVE_HTML(R.string.export_save_html, R.string.export_html_supporting, R.drawable.ic_code),
    SAVE_ANSI(R.string.export_save_ansi, R.string.export_ansi_supporting, R.drawable.ic_terminal),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(onDismiss: () -> Unit, onAction: (ExportAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.export_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ExportAction.entries.forEach { action ->
                ListItem(
                    headlineContent = { Text(stringResource(action.title)) },
                    supportingContent = if (action.supporting != null) {
                        { Text(stringResource(action.supporting)) }
                    } else {
                        null
                    },
                    leadingContent = { Icon(painterResource(action.icon), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        // Let the sheet slide away before handing over to another screen.
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
                    },
                )
            }
        }
    }
}
