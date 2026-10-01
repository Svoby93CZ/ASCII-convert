package cz.svoby93.asciistudio.ui.studio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlinx.coroutines.delay

/**
 * A window in the style of text user interfaces: a rounded border that glows in the ink colour,
 * with a [title] and an optional [status] set into the top border line and a [footer] set into
 * the bottom one.
 *
 * The frame measures like its [content] plus the border, so it can either wrap the content or fill
 * the size given by [modifier]. The labels are transparent unless [labelBackground] is given, which
 * keeps them readable over something other than the background of the screens, like a dialog scrim.
 * [titleModifier] goes to the label with the title, e.g. to listen to taps on it.
 */
@Composable
fun TerminalFrame(
    title: String,
    modifier: Modifier = Modifier,
    status: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    labelBackground: Color = Color.Unspecified,
    titleModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalStudioColors.current
    val cuts = remember { BorderCuts() }
    val hasFooter = footer != null
    val bottomInset = if (hasFooter) LabelHeight / 2 else 0.dp
    Layout(
        content = {
            Box(
                Modifier
                    .layoutId(BODY)
                    .padding(top = LabelHeight / 2, bottom = bottomInset)
                    .clip(FrameShape)
                    .background(colors.paper)
                    .padding(top = LabelHeight / 2, bottom = bottomInset),
                content = content,
            )
            FrameLabel(Modifier.layoutId(TITLE).then(titleModifier), labelBackground) {
                Text(title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status != null) FrameLabel(Modifier.layoutId(STATUS), labelBackground, status)
            if (footer != null) FrameLabel(Modifier.layoutId(FOOTER), labelBackground, footer)
        },
        modifier = modifier.drawWithContent {
            val stroke = BorderWidth.toPx()
            val box = Rect(
                left = stroke / 2,
                top = LabelHeight.toPx() / 2 + stroke / 2,
                right = size.width - stroke / 2,
                bottom = size.height - bottomInset.toPx() - stroke / 2,
            )
            val radius = FrameRadius.toPx() - stroke / 2
            drawGlow(box, radius, colors.ink, if (colors.isLight) 0.12f else 0.22f)
            drawContent()
            // The labels sit in gaps of the border line, like the legend of a form field set.
            val gaps = Path()
            val labelHeight = LabelHeight.toPx()
            cuts.top.forEach { gaps.addRect(Rect(it.start, 0f, it.endInclusive, labelHeight)) }
            cuts.bottom.forEach { gaps.addRect(Rect(it.start, size.height - labelHeight, it.endInclusive, size.height)) }
            clipPath(gaps, ClipOp.Difference) {
                drawRoundRect(
                    color = colors.ink.copy(alpha = 0.85f),
                    topLeft = box.topLeft,
                    size = box.size,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(stroke),
                )
            }
        },
    ) { measurables, constraints ->
        val body = measurables.first { it.layoutId == BODY }.measure(constraints)
        val inset = LabelInset.roundToPx()
        val room = (body.width - 2 * inset).coerceAtLeast(0)
        val status = measurables.firstOrNull { it.layoutId == STATUS }?.measure(Constraints(maxWidth = room / 2))
        val footer = measurables.firstOrNull { it.layoutId == FOOTER }?.measure(Constraints(maxWidth = room))
        val titleRoom = (room - (status?.width ?: 0) - inset / 2).coerceAtLeast(0)
        val title = measurables.first { it.layoutId == TITLE }.measure(Constraints(maxWidth = titleRoom))
        layout(body.width, body.height) {
            body.place(0, 0)
            title.place(inset, 0)
            val statusX = body.width - inset - (status?.width ?: 0)
            status?.place(statusX, 0)
            val footerX = body.width - inset - (footer?.width ?: 0)
            if (footer != null) footer.place(footerX, body.height - footer.height)
            cuts.update(
                top = listOfNotNull(
                    inset.toFloat()..(inset + title.width).toFloat(),
                    status?.let { statusX.toFloat()..(statusX + it.width).toFloat() },
                ),
                bottom = listOfNotNull(footer?.let { footerX.toFloat()..(footerX + it.width).toFloat() }),
            )
        }
    }
}

/** Horizontal ranges of the border line that the labels cover; read while drawing. */
@Stable
private class BorderCuts {
    var top by mutableStateOf(emptyList<ClosedFloatingPointRange<Float>>())
        private set
    var bottom by mutableStateOf(emptyList<ClosedFloatingPointRange<Float>>())
        private set

    fun update(top: List<ClosedFloatingPointRange<Float>>, bottom: List<ClosedFloatingPointRange<Float>>) {
        if (top != this.top) this.top = top
        if (bottom != this.bottom) this.bottom = bottom
    }
}

@Composable
private fun FrameLabel(modifier: Modifier, background: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .height(LabelHeight)
            .then(if (background.isSpecified) Modifier.background(background, LabelShape) else Modifier)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides LocalStudioColors.current.ink,
            LocalTextStyle provides TerminalLabelStyle,
        ) {
            content()
        }
    }
}

/** A soft halo around [box], built from strokes that fade out with the distance. */
private fun DrawScope.drawGlow(box: Rect, radius: Float, color: Color, strength: Float) {
    val spread = GlowSpread.toPx()
    val step = spread / GLOW_STEPS
    for (i in 1..GLOW_STEPS) {
        val distance = step * (i - 0.5f)
        val alpha = strength * (1f - i / (GLOW_STEPS + 1f)).pow(2)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(box.left - distance, box.top - distance),
            size = Size(box.width + 2 * distance, box.height + 2 * distance),
            cornerRadius = CornerRadius(radius + distance),
            style = Stroke(step * 1.5f),
        )
    }
}

/**
 * A blinking dot followed by [text], like the recording light of a camera. With [morse] the dot
 * blinks a word in Morse code instead of pulsing.
 */
@Composable
fun RowScope.BlinkingDot(text: String, morse: MorseLight? = null) {
    val clock = rememberDecorationClock()
    Box(
        Modifier
            .size(7.dp)
            .graphicsLayer {
                val seconds = clock.floatValue
                alpha = when {
                    morse == null -> dotAlpha(seconds)
                    morse.isOn(seconds) -> 1f
                    else -> DOT_DIMMED
                }
            }
            .background(LocalContentColor.current, CircleShape),
    )
    Text(text.uppercase(), maxLines = 1)
}

/** The dot fades out and back in, easing like a tween that runs forwards and back. */
private fun dotAlpha(seconds: Float): Float {
    val phase = seconds * MILLIS_PER_SECOND / BLINK_MS % 2f
    val fade = FastOutSlowInEasing.transform(if (phase < 1f) phase else 2f - phase)
    return 1f - (1f - DOT_DIMMED) * fade
}

/** A terminal line that ends with a blinking block cursor. */
@Composable
fun TerminalLine(text: String, modifier: Modifier = Modifier, style: TextStyle = TerminalLabelStyle) {
    val animate = LocalAnimationsEnabled.current
    var cursorOn by remember { mutableStateOf(true) }
    if (animate) {
        // The hard blink of a text cursor needs no frames in between.
        LaunchedEffect(Unit) {
            while (true) {
                delay(BLINK_MS.toLong())
                cursorOn = !cursorOn
            }
        }
    }
    // Screen readers get the text without the prompt and the cursor.
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "> $text ",
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // The block is as tall as the text's capitals, whatever the size of the text.
        val cursorHeight = with(LocalDensity.current) { (style.fontSize * CURSOR_HEIGHT).toDp() }
        Box(
            Modifier
                .size(width = cursorHeight * CURSOR_ASPECT, height = cursorHeight)
                .graphicsLayer { alpha = if (cursorOn || !animate) 1f else 0f }
                .background(LocalContentColor.current),
        )
    }
}

internal val FrameRadius = 16.dp
internal val FrameShape = RoundedCornerShape(FrameRadius)

/** Height of the labels in the border lines; half of it sticks out of the window. */
internal val LabelHeight = 24.dp
private val LabelShape = RoundedCornerShape(6.dp)

private val BorderWidth = 1.5.dp
private val GlowSpread = 14.dp
private val LabelInset = 18.dp
private const val GLOW_STEPS = 7
private const val BLINK_MS = 650
private const val DOT_DIMMED = 0.15f
private const val MILLIS_PER_SECOND = 1000f
private const val CURSOR_HEIGHT = 1.15f
private const val CURSOR_ASPECT = 0.58f

private const val BODY = "body"
private const val TITLE = "title"
private const val STATUS = "status"
private const val FOOTER = "footer"
