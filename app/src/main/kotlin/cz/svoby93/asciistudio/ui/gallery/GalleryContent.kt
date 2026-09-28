package cz.svoby93.asciistudio.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.data.GalleryItem
import cz.svoby93.asciistudio.ui.studio.RoundButton
import cz.svoby93.asciistudio.ui.studio.StudioTopBar
import cz.svoby93.asciistudio.ui.studio.TerminalFrame
import cz.svoby93.asciistudio.ui.studio.TerminalLine

/**
 * The saved ASCII art as a grid of small windows, newest first. A tap opens the art in the editor.
 *
 * @param items `null` while the gallery is being read.
 * @param preview loads the preview image of an item, `null` until it is ready.
 * @param dateText when an item was saved, as shown in its title.
 */
@Composable
fun GalleryContent(
    items: List<GalleryItem>?,
    preview: @Composable (GalleryItem) -> ImageBitmap?,
    dateText: (GalleryItem) -> String,
    onBack: () -> Unit,
    onOpen: (GalleryItem) -> Unit,
    onDelete: (GalleryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        StudioTopBar(title = stringResource(R.string.gallery_title), onBack = onBack)
        when {
            items == null -> Unit
            items.isEmpty() -> EmptyGallery(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 480.dp)
                    .padding(16.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    GalleryTile(
                        item = item,
                        preview = preview(item),
                        date = dateText(item),
                        onOpen = { onOpen(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

/** One saved art: its preview in a window with the date above and the size below. */
@Composable
private fun GalleryTile(
    item: GalleryItem,
    preview: ImageBitmap?,
    date: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val description = stringResource(R.string.gallery_item, date)
    val openLabel = stringResource(R.string.gallery_open)
    TerminalFrame(
        title = date,
        footer = { Text("${item.columns}×${item.rows}", maxLines = 1) },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(TILE_ASPECT),
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = description },
        )
        RoundButton(
            icon = R.drawable.ic_delete,
            description = R.string.gallery_delete,
            size = 36.dp,
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    TerminalFrame(title = stringResource(R.string.gallery_title), modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Icon(painterResource(R.drawable.ic_grid_view), contentDescription = null, modifier = Modifier.size(40.dp))
            TerminalLine(stringResource(R.string.gallery_empty))
            Text(
                stringResource(R.string.gallery_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Slightly taller than wide, like most photos taken upright. */
private const val TILE_ASPECT = 0.8f
