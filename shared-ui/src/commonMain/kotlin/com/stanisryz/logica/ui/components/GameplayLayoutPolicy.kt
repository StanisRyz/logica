package com.stanisryz.logica.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared breakpoints keep all game boards consistent across phones, tablets, and browser windows. */
internal fun isWideGameplayLayout(
    maxWidth: Dp,
    maxHeight: Dp,
): Boolean =
    maxWidth >= EXPANDED_GAME_WIDTH ||
        (maxWidth >= LANDSCAPE_GAME_WIDTH && maxWidth > maxHeight * LANDSCAPE_ASPECT_RATIO)

private val LANDSCAPE_GAME_WIDTH = 520.dp
private val EXPANDED_GAME_WIDTH = 840.dp
private const val LANDSCAPE_ASPECT_RATIO = 1.2f
