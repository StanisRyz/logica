package com.stanisryz.logica.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.stanisryz.logica.ui.theme.LogicaMotion

internal fun horizontalSlideTransition(
    incomingDirection: Int,
    outgoingDirection: Int,
) = slideInHorizontally(navigationSlideSpec()) { width ->
    incomingDirection * width
} togetherWith
    slideOutHorizontally(navigationSlideSpec()) { width ->
        outgoingDirection * width
    }

private fun navigationSlideSpec() =
    tween<IntOffset>(
        durationMillis = LogicaMotion.NAVIGATION_SLIDE_MILLIS,
        easing = FastOutSlowInEasing,
    )
