package cz.svoby93.asciistudio.ui.gallery

import android.text.format.DateUtils
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.GalleryItem
import cz.svoby93.asciistudio.ui.studio.TerminalDialog
import java.text.DateFormat

@Composable
fun GalleryScreen(onBack: () -> Unit, onOpen: (GalleryItem) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(container.galleryRepository) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    GalleryContent(
        items = items,
        preview = { item ->
            val preview by produceState(viewModel.cachedPreview(item), item.id) {
                if (value == null) value = viewModel.loadPreview(item)
            }
            preview
        },
        // The time for art saved today, the date for older art.
        dateText = { item ->
            DateUtils.formatSameDayTime(item.createdAt, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT)
                .toString()
        },
        onBack = onBack,
        onOpen = onOpen,
        onDelete = { item -> pendingDelete = item.id },
    )

    val deleting = items?.firstOrNull { it.id == pendingDelete }
    if (deleting != null) {
        TerminalDialog(
            title = stringResource(R.string.gallery_delete_title),
            onDismiss = { pendingDelete = null },
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(deleting)
                    },
                ) {
                    Text(stringResource(R.string.gallery_delete))
                }
            },
        ) {
            Text(stringResource(R.string.gallery_delete_text))
        }
    }
}
