package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.board_zoom_reset
import org.jetbrains.compose.resources.stringResource

/**
 * A board that two fingers (or the mouse wheel) can zoom and pan, while one finger keeps playing:
 * every multi-touch change is consumed before the board sees it, so a pinch never opens a cell.
 * Pointer positions reach the board already mapped through the zoom. [resetKey] (a new puzzle)
 * returns to the whole board, and a small button does the same while zoomed. Presentation only.
 */
@Composable
fun ZoomableBoard(
    resetKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun apply(
        zoom: Float,
        centroid: Offset,
        pan: Offset,
    ) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val raw = centroid - (centroid - offset) * (newScale / scale) + pan
        val minX = viewport.width * (1f - newScale)
        val minY = viewport.height * (1f - newScale)
        offset = Offset(raw.x.coerceIn(minX, 0f), raw.y.coerceIn(minY, 0f))
        scale = newScale
    }

    Box(
        modifier =
            modifier
                .clipToBounds()
                .pointerInput(resetKey) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Read per gesture: a desktop window can resize the board after this input starts.
                        viewport = size
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                apply(event.calculateZoom(), event.calculateCentroid(useCurrent = true), event.calculatePan())
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }.pointerInput(resetKey) {
                    // A mouse wheel zooms around the pointer, for the desktop Web.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val delta = change.scrollDelta.y
                            if (delta == 0f) continue
                            viewport = size
                            apply(if (delta < 0f) WHEEL_STEP else 1f / WHEEL_STEP, change.position, Offset.Zero)
                            change.consume()
                        }
                    }
                },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            content = content,
        )
        if (scale > 1.01f) {
            val colors = MaterialTheme.colorScheme
            IconButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh.copy(alpha = 0.92f)),
            ) {
                Icon(Icons.Rounded.ZoomOutMap, contentDescription = stringResource(Res.string.board_zoom_reset), tint = colors.onSurface)
            }
        }
    }
}

private const val MAX_SCALE = 3f
private const val WHEEL_STEP = 1.15f
