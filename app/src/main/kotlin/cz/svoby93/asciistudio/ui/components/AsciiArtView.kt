package cz.svoby93.asciistudio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.svoby93.asciistudio.LocalAppContainer
import cz.svoby93.asciistudio.engine.AsciiArt
import cz.svoby93.asciistudio.render.ArtStyle
import cz.svoby93.asciistudio.render.AsciiRenderer
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** Zoom and pan of an [AsciiArtView]; zoom 1 means "fit to the view". */
@Stable
class ArtViewportState {
    var zoom by mutableFloatStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set

    /** Derived, so that readers recompose only when it flips, not on every zoom step. */
    val isZoomed: Boolean by derivedStateOf { zoom > 1.01f }

    suspend fun reset() = animateTo(1f, Offset.Zero)

    internal suspend fun animateTo(targetZoom: Float, targetOffset: Offset) {
        val startZoom = zoom
        val startOffset = offset
        val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        animate(0f, 1f, animationSpec = spec) { fraction, _ ->
            zoom = startZoom + (targetZoom - startZoom) * fraction
            offset = lerp(startOffset, targetOffset, fraction)
        }
    }

    companion object {
        const val MAX_ZOOM = 12f
    }
}

@Composable
fun rememberArtViewportState(vararg keys: Any?): ArtViewportState = remember(*keys) { ArtViewportState() }

@Composable
fun rememberAsciiRenderer(): AsciiRenderer {
    val typeface = LocalAppContainer.current.asciiTypeface
    return remember(typeface) { AsciiRenderer(typeface) }
}

/**
 * Draws [art] centred and scaled to fit, with pinch-to-zoom, panning and double-tap zoom when
 * [interactive]. Text is re-rasterised at every zoom level, so it stays sharp.
 */
@Composable
fun AsciiArtView(
    art: AsciiArt?,
    style: ArtStyle,
    modifier: Modifier = Modifier,
    viewport: ArtViewportState = rememberArtViewportState(),
    interactive: Boolean = true,
    contentPadding: Dp = 12.dp,
) {
    val renderer = rememberAsciiRenderer()
    val currentArt by rememberUpdatedState(art)
    val scope = rememberCoroutineScope()

    fun fitScale(art: AsciiArt, size: Size, padding: Float): Float = min(
        (size.width - 2 * padding).coerceAtLeast(1f) / renderer.width(art),
        (size.height - 2 * padding).coerceAtLeast(1f) / renderer.height(art),
    )

    fun clampOffset(offset: Offset, zoom: Float, size: Size, padding: Float): Offset {
        val art = currentArt ?: return Offset.Zero
        val scale = fitScale(art, size, padding) * zoom
        val maxX = max(0f, (renderer.width(art) * scale - size.width) / 2f + padding)
        val maxY = max(0f, (renderer.height(art) * scale - size.height) / 2f + padding)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    val gestures = if (interactive) {
        Modifier
            .pointerInput(viewport) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val viewSize = Size(size.width.toFloat(), size.height.toFloat())
                    val padding = contentPadding.toPx()
                    val zoom = (viewport.zoom * gestureZoom).coerceIn(1f, ArtViewportState.MAX_ZOOM)
                    val factor = zoom / viewport.zoom
                    val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                    // Keep the point under the fingers in place while zooming, then follow the pan.
                    val offset = (viewport.offset + center - centroid) * factor + centroid - center + pan
                    viewport.zoom = zoom
                    viewport.offset = clampOffset(offset, zoom, viewSize, padding)
                }
            }
            .pointerInput(viewport) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val viewSize = Size(size.width.toFloat(), size.height.toFloat())
                        val padding = contentPadding.toPx()
                        scope.launch {
                            if (viewport.isZoomed) {
                                viewport.reset()
                            } else {
                                val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                                // Zoom in around the tapped point.
                                val target = clampOffset((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM, viewSize, padding)
                                viewport.animateTo(DOUBLE_TAP_ZOOM, target)
                            }
                        }
                    },
                )
            }
    } else {
        Modifier
    }

    Canvas(modifier.clipToBounds().then(gestures)) {
        val current = art ?: return@Canvas
        val padding = contentPadding.toPx()
        val scale = fitScale(current, size, padding) * viewport.zoom
        val left = (size.width - renderer.width(current) * scale) / 2f + viewport.offset.x
        val top = (size.height - renderer.height(current) * scale) / 2f + viewport.offset.y
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.translate(left, top)
            native.scale(scale, scale)
            renderer.draw(native, current, style)
            native.restoreToCount(checkpoint)
        }
    }
}

private const val DOUBLE_TAP_ZOOM = 3f
