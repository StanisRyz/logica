package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

/**
 * Motion for a cell's content when its validation changes: a small spring pop when it becomes
 * correct and a short shake when it becomes wrong. Only a change seen by this composition animates,
 * so a restored board, a retry, or the first frame never moves.
 */
@Composable
fun Modifier.cellFeedbackMotion(
    correct: Boolean,
    incorrect: Boolean,
): Modifier {
    val scale = remember { Animatable(1f) }
    val shift = remember { Animatable(0f) }
    val last = remember { booleanArrayOf(correct, incorrect) }
    val shakePx = with(LocalDensity.current) { SHAKE_DP * density }
    LaunchedEffect(correct, incorrect) {
        val becameCorrect = correct && !last[0]
        val becameIncorrect = incorrect && !last[1]
        last[0] = correct
        last[1] = incorrect
        if (becameCorrect) {
            launch {
                scale.snapTo(POP_START_SCALE)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
        if (becameIncorrect) {
            launch {
                shift.animateTo(
                    0f,
                    keyframes {
                        durationMillis = SHAKE_MILLIS
                        -shakePx at 50
                        shakePx at 110
                        -shakePx * 0.6f at 170
                        shakePx * 0.4f at 230
                    },
                )
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = shift.value
    }
}

private const val POP_START_SCALE = 0.6f
private const val SHAKE_DP = 4f
private const val SHAKE_MILLIS = 300
