package cz.svoby93.asciistudio.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.svoby93.asciistudio.data.Backdrop
import cz.svoby93.asciistudio.ui.theme.MonoFontFamily
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Decoration behind the windows of the screens, in the colours of the palette. With [animate] off,
 * moving backgrounds show a still frame; [scale] shrinks the pattern for previews.
 */
@Composable
fun StudioBackdrop(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    animate: Boolean = LocalAnimationsEnabled.current,
    scale: Float = 1f,
) {
    val colors = LocalStudioColors.current
    // A layer of its own, so that the animated backgrounds redraw without the rest of the screen.
    val layer = modifier.graphicsLayer { }
    when (backdrop) {
        Backdrop.ASCII -> AsciiBackdrop(colors, scale, layer)
        Backdrop.RAIN -> RainBackdrop(colors, animate, scale, layer)
        Backdrop.GRID -> GridBackdrop(colors, scale, layer)
        Backdrop.CRT -> CrtBackdrop(colors, animate, scale, layer)
        Backdrop.NONE -> Spacer(layer.background(colors.desk))
    }
}

/** Glyphs that get denser towards the edges: a vignette drawn in ASCII. */
@Composable
private fun AsciiBackdrop(colors: StudioColors, scale: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            if (size.minDimension <= 0f) return@drawWithCache onDrawBehind { }
            val style = GlyphStyle.copy(fontSize = GlyphStyle.fontSize * scale)
            val cell = measurer.measure("M", style)
            val cellWidth = cell.size.width.toFloat()
            val cellHeight = cell.size.height.toFloat()
            val columns = ceil(size.width / cellWidth).toInt() + 1
            val rows = ceil(size.height / cellHeight).toInt() + 1
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val random = Random(ASCII_SEED)
            val densest = ASCII_RAMP.length - 1
            val lines = List(rows) { row ->
                val line = CharArray(columns) { column ->
                    val dx = (column * cellWidth + cellWidth / 2f - centerX) / centerX
                    val dy = (row * cellHeight + cellHeight / 2f - centerY) / centerY
                    val edge = smoothStep(0.5f, 1.45f, hypot(dx, dy))
                    val jitter = (random.nextFloat() - 0.5f) * 0.3f
                    // The densest glyphs stay rare, so the pattern reads as texture, not as a wall.
                    val level = ((edge * ASCII_MAX_DENSITY + jitter) * densest).roundToInt()
                    ASCII_RAMP[level.coerceIn(0, densest)]
                }
                measurer.measure(String(line), style, softWrap = false, maxLines = 1)
            }
            val alpha = if (colors.isLight) 0.17f else 0.2f
            onDrawBehind {
                drawDesk(colors)
                lines.forEachIndexed { row, line ->
                    drawText(line, color = colors.ink, topLeft = Offset(0f, row * cellHeight), alpha = alpha)
                }
            }
        },
    )
}

/** Streams of glyphs falling at different speeds, each with a bright head and a fading trail. */
@Composable
private fun RainBackdrop(colors: StudioColors, animate: Boolean, scale: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val clock = rememberClock(animate)
    Spacer(
        modifier.drawWithCache {
            val style = GlyphStyle.copy(fontSize = GlyphStyle.fontSize * scale)
            val glyphs = RAIN_GLYPHS.map { measurer.measure(it.toString(), style) }
            val cellWidth = glyphs.first().size.width * 1.8f
            val cellHeight = glyphs.first().size.height.toFloat()
            val columns = ceil(size.width / cellWidth).toInt()
            val rows = ceil(size.height / cellHeight).toInt()
            val random = Random(RAIN_SEED)
            val speeds = FloatArray(columns) { 4f + random.nextFloat() * 9f }
            val lengths = IntArray(columns) { 6 + random.nextInt(16) }
            val phases = FloatArray(columns) { random.nextFloat() * 200f }
            val cells = IntArray(columns * rows) { random.nextInt(glyphs.size) }
            val headAlpha = if (colors.isLight) 0.45f else 0.6f
            val trailAlpha = if (colors.isLight) 0.24f else 0.3f
            onDrawBehind {
                drawDesk(colors)
                val time = clock.floatValue
                for (column in 0 until columns) {
                    val length = lengths[column]
                    // The stream enters above the screen and leaves below it before it comes back.
                    val cycle = rows + 2f * length
                    val head = floor((phases[column] + time * speeds[column]) % cycle).toInt() - length
                    for (k in 0 until length) {
                        val row = head - k
                        if (row !in 0 until rows) continue
                        val cell = column * rows + row
                        // The head keeps changing its glyph, the trail keeps what the head left behind.
                        val index = if (k == 0) (cells[cell] + (time * 14f).toInt()) % glyphs.size else cells[cell]
                        val glyph = glyphs[index]
                        val fade = 1f - k / length.toFloat()
                        drawText(
                            glyph,
                            color = colors.ink,
                            topLeft = Offset(column * cellWidth + (cellWidth - glyph.size.width) / 2f, row * cellHeight),
                            alpha = if (k == 0) headAlpha else trailAlpha * fade * fade,
                        )
                    }
                }
            }
        },
    )
}

/** Fine lines with brighter crosses at every fourth crossing, centred on the glow. */
@Composable
private fun GridBackdrop(colors: StudioColors, scale: Float, modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val step = 24.dp.toPx() * scale
            val arm = 4.dp.toPx() * scale
            val centerX = size.width / 2f
            val centerY = size.height * GLOW_CENTER_Y
            val firstColumn = -floor(centerX / step).toInt()
            val lastColumn = floor((size.width - centerX) / step).toInt()
            val firstRow = -floor(centerY / step).toInt()
            val lastRow = floor((size.height - centerY) / step).toInt()
            val lines = Path()
            val crosses = Path()
            for (column in firstColumn..lastColumn) {
                val x = centerX + column * step
                lines.moveTo(x, 0f)
                lines.lineTo(x, size.height)
            }
            for (row in firstRow..lastRow) {
                val y = centerY + row * step
                lines.moveTo(0f, y)
                lines.lineTo(size.width, y)
                if (row % 4 != 0) continue
                for (column in firstColumn..lastColumn) {
                    if (column % 4 != 0) continue
                    val x = centerX + column * step
                    crosses.moveTo(x - arm, y)
                    crosses.lineTo(x + arm, y)
                    crosses.moveTo(x, y - arm)
                    crosses.lineTo(x, y + arm)
                }
            }
            val lineWidth = 1.dp.toPx()
            val crossWidth = 1.5.dp.toPx()
            onDrawBehind {
                drawDesk(colors)
                drawPath(lines, colors.ink.copy(alpha = if (colors.isLight) 0.1f else 0.08f), style = Stroke(lineWidth))
                drawPath(crosses, colors.ink.copy(alpha = 0.35f), style = Stroke(crossWidth, cap = StrokeCap.Round))
            }
        },
    )
}

/** Scanlines, a glowing tube and dark corners of an old monitor, with a slowly rolling bright band. */
@Composable
private fun CrtBackdrop(colors: StudioColors, animate: Boolean, scale: Float, modifier: Modifier) {
    val clock = rememberClock(animate)
    Spacer(
        modifier.drawWithCache {
            val pitch = (3.dp.toPx() * scale).coerceAtLeast(2f)
            val scanlines = Path()
            var y = pitch / 2f
            while (y < size.height) {
                scanlines.moveTo(0f, y)
                scanlines.lineTo(size.width, y)
                y += pitch
            }
            val lineWidth = pitch / 3f
            val lineColor = if (colors.isLight) colors.ink.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.45f)
            val corners = if (colors.isLight) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.7f)
            val vignette = Brush.radialGradient(
                0f to Color.Transparent,
                0.6f to Color.Transparent,
                1f to corners,
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(1f, hypot(size.width, size.height) / 2f),
            )
            val bandHeight = size.height * 0.3f
            onDrawBehind {
                drawDesk(colors, glow = 1.8f)
                if (animate) {
                    // A bright band rolls down every few seconds, like on a badly synced monitor.
                    val top = (clock.floatValue / ROLL_SECONDS % 1f) * (size.height + bandHeight) - bandHeight
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.ink.copy(alpha = 0.06f), Color.Transparent),
                            startY = top,
                            endY = top + bandHeight,
                        ),
                    )
                }
                drawPath(scanlines, lineColor, style = Stroke(lineWidth))
                drawRect(vignette)
            }
        },
    )
}

/** Seconds since the backdrop appeared, updated every frame while [animate] is on. */
@Composable
private fun rememberClock(animate: Boolean): MutableFloatState {
    val clock = remember { mutableFloatStateOf(START_SECONDS) }
    if (animate) {
        LaunchedEffect(clock) {
            val start = withFrameNanos { it } - (clock.floatValue * NANOS_PER_SECOND).toLong()
            while (true) {
                withFrameNanos { now -> clock.floatValue = (now - start) / NANOS_PER_SECOND }
            }
        }
    }
    return clock
}

/** The desk colour with a soft light behind the main window. */
private fun DrawScope.drawDesk(colors: StudioColors, glow: Float = 1f) {
    drawRect(colors.desk)
    if (size.minDimension <= 0f) return
    val light = if (colors.isLight) Color.White.copy(alpha = 0.4f * glow) else colors.ink.copy(alpha = 0.1f * glow)
    drawRect(
        Brush.radialGradient(
            listOf(light, Color.Transparent),
            center = Offset(size.width / 2f, size.height * GLOW_CENTER_Y),
            radius = max(size.width, size.height) * 0.6f,
        ),
    )
}

private fun smoothStep(from: Float, to: Float, value: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val GlyphStyle = TextStyle(fontFamily = MonoFontFamily, fontSize = 13.sp)

private const val ASCII_RAMP = " .,:;-~=+*o#%@"
private const val ASCII_MAX_DENSITY = 0.78f
private const val RAIN_GLYPHS = "01<>{}[]()/\\|=+*#%@$&"
private const val ASCII_SEED = 1977
private const val RAIN_SEED = 1999
private const val GLOW_CENTER_Y = 0.4f
private const val ROLL_SECONDS = 7f
private const val START_SECONDS = 3f
private const val NANOS_PER_SECOND = 1_000_000_000f
