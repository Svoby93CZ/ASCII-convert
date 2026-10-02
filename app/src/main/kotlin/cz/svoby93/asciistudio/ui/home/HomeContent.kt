package cz.svoby93.asciistudio.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.Signature
import cz.svoby93.asciistudio.ui.studio.BlinkingDot
import cz.svoby93.asciistudio.ui.studio.LocalStudioColors
import cz.svoby93.asciistudio.ui.studio.MorseLight
import cz.svoby93.asciistudio.ui.studio.RoundButton
import cz.svoby93.asciistudio.ui.studio.StudioTopBar
import cz.svoby93.asciistudio.ui.studio.TerminalFrame
import cz.svoby93.asciistudio.ui.studio.TerminalLine
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily

/**
 * The start screen: the spinning donut and a menu of what to do next, over the background of the
 * app. On wide screens the two stand side by side.
 *
 * @param recentImage the image from the last session, offered as "continue editing".
 * @param onSignature called when five taps turn the donut into the author's nickname.
 */
@Composable
fun HomeContent(
    recentImage: ImageBitmap?,
    galleryCount: Int,
    onPickImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onContinueEditing: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onSignature: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val menu: @Composable () -> Unit = {
        HomeMenu(recentImage, galleryCount, onPickImage, onOpenCamera, onOpenGallery, onContinueEditing)
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val hero: @Composable (Modifier) -> Unit = { heroModifier -> HeroWindow(onSignature, heroModifier) }
        val landscape = maxWidth > maxHeight && maxWidth >= 480.dp
        Column(Modifier.fillMaxSize()) {
            StudioTopBar(
                title = stringResource(R.string.app_name),
                onBack = null,
                titleStyle = MaterialTheme.typography.headlineSmall,
            ) {
                RoundButton(icon = R.drawable.ic_info, description = R.string.action_about, onClick = onAbout)
            }
            if (landscape) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    hero(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Subtitle()
                        menu()
                        Tip()
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    hero(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.3f),
                    )
                    Subtitle()
                    menu()
                    Tip()
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HeroWindow(onSignature: () -> Unit, modifier: Modifier) {
    val running = stringResource(R.string.home_hero_status)
    TerminalFrame(
        title = stringResource(R.string.home_hero_title),
        status = { BlinkingDot(running, morse = SignatureLight) },
        modifier = modifier,
    ) {
        DonutHero(onSignature, Modifier.fillMaxSize())
    }
}

@Composable
private fun Subtitle() {
    TerminalLine(
        stringResource(R.string.home_subtitle),
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Tip() {
    Text(
        stringResource(R.string.home_tip),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** The actions of the start screen as the menu of a text program; the first one is highlighted. */
@Composable
private fun HomeMenu(
    recentImage: ImageBitmap?,
    galleryCount: Int,
    onPickImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onContinueEditing: () -> Unit,
) {
    TerminalFrame(title = stringResource(R.string.home_menu), modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            MenuItem(
                label = stringResource(R.string.action_pick_image),
                icon = R.drawable.ic_photo_library,
                highlighted = true,
                onClick = onPickImage,
            )
            MenuItem(
                label = stringResource(R.string.action_live_camera),
                icon = R.drawable.ic_photo_camera,
                onClick = onOpenCamera,
            )
            MenuItem(
                label = stringResource(R.string.gallery_title),
                icon = R.drawable.ic_grid_view,
                trailing = if (galleryCount > 0) galleryCount.toString() else null,
                onClick = onOpenGallery,
            )
            if (recentImage != null) {
                MenuItem(
                    label = stringResource(R.string.action_continue),
                    supporting = stringResource(R.string.continue_supporting),
                    image = recentImage,
                    onClick = onContinueEditing,
                )
            }
        }
    }
}

/**
 * One line of the menu with an [icon] or a small [image], a [label] and a [trailing] note or an
 * arrow. A [highlighted] line is drawn in inverse video.
 */
@Composable
private fun MenuItem(
    label: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    image: ImageBitmap? = null,
    supporting: String? = null,
    trailing: String? = null,
    highlighted: Boolean = false,
) {
    val colors = LocalStudioColors.current
    val content = if (highlighted) colors.paper else colors.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ItemShape)
            .background(if (highlighted) colors.ink else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(ThumbnailShape)
                    .border(1.dp, content.copy(alpha = 0.6f), ThumbnailShape),
            )
            icon != null -> Icon(painterResource(icon), contentDescription = null, tint = content)
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = content,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    color = content.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            trailing ?: ">",
            color = content,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            // The arrow is decoration; a count is worth reading out.
            modifier = if (trailing == null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

private val ItemShape = RoundedCornerShape(10.dp)
private val ThumbnailShape = RoundedCornerShape(8.dp)

/** The light in the border of the donut's window blinks the author's nickname. */
private val SignatureLight = MorseLight(Signature.NAME)
