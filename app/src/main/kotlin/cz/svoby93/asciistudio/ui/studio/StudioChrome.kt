package cz.svoby93.asciistudio.ui.studio

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cz.svoby93.asciistudio.R
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily

/** The bar at the top of a screen: back button, title and round action buttons. */
@Composable
fun StudioTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            RoundButton(icon = R.drawable.ic_arrow_back, description = R.string.action_back, onClick = onBack)
        }
        Text(
            title,
            style = titleStyle,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

/** A round outlined button; with [checked] it is a toggle that turns to inverse video when on. */
@Composable
fun RoundButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    checked: Boolean? = null,
    enabled: Boolean = true,
) {
    val colors = LocalStudioColors.current
    val label = stringResource(description)
    val on = checked == true
    val interaction = if (checked == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
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

/** A dialog in a [TerminalFrame]: [content] scrolls when it is long, [buttons] stay at the bottom. */
@Composable
fun TerminalDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        TerminalDialogWindow(title = title, buttons = buttons, content = content)
    }
}

/** The window of a [TerminalDialog], on its own so that it can be previewed without a dialog. */
@Composable
fun TerminalDialogWindow(
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The labels stick out of the window, over the scrim of the dialog.
    TerminalFrame(
        title = title,
        labelBackground = LocalStudioColors.current.desk,
        modifier = modifier.widthIn(max = 480.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                content = buttons,
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
