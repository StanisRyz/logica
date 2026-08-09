package com.stanisryz.logica.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The single layout rhythm shared by every screen. Screens use these values instead of inventing
 * their own paddings so Today, Catalog, Statistics, Settings, and the start screens stay aligned.
 */
object LogicaSpacing {
    /** Horizontal inset of every screen's content. */
    val screenHorizontal = 20.dp

    /** Vertical inset at the top and bottom of a screen. */
    val screenVertical = 20.dp

    /**
     * A tighter horizontal inset for gameplay that fills the whole width. The Word keyboard packs
     * twelve keys into one row, so every millimetre of width is a wider tap target.
     */
    val gameplayHorizontal = 12.dp

    /** Between two top-level sections of a screen. */
    val section = 20.dp

    /** Between sibling items inside one section, such as cards in a list. */
    val item = 12.dp

    /** Padding inside a card. */
    val cardPadding = 20.dp

    /** Between blocks inside one card. */
    val cardContent = 12.dp

    /** Between a title and its supporting line. */
    val text = 4.dp

    /** Between sibling actions in an action row. */
    val action = 12.dp
}
