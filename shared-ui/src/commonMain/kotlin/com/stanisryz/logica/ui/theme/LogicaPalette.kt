package com.stanisryz.logica.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LogicaPalette(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val crownsRegions: List<Color>,
    val onCrownsRegion: Color,
    /** The earned-star gold of the result card; Material has no role for it. */
    val star: Color = Color(0xFFE0A526),
    /**
     * 2048 tile colours from 2 upwards (container to content), one step per doubling; values past
     * the end keep the last step. A game ramp, like the Crowns regions, not a Material role.
     */
    val game2048Tiles: List<Pair<Color, Color>> = LightGame2048Tiles,
)

private val LightGame2048Tiles =
    listOf(
        Color(0xFFFFFFFF) to Color(0xFF45403A),
        Color(0xFFF1E1C0) to Color(0xFF45403A),
        Color(0xFFF3C088) to Color(0xFF4A2C0C),
        Color(0xFFEF9D6A) to Color(0xFF4A1F08),
        Color(0xFFE2735A) to Color(0xFFFFFFFF),
        Color(0xFFCF5343) to Color(0xFFFFFFFF),
        Color(0xFFEBC65C) to Color(0xFF3D2E05),
        Color(0xFFDDAE36) to Color(0xFF3A2A02),
        Color(0xFF9CC685) to Color(0xFF17331A),
        Color(0xFF4E9A7A) to Color(0xFFFFFFFF),
        Color(0xFF2F6B5A) to Color(0xFFFFFFFF),
        Color(0xFF3E3A5C) to Color(0xFFFFFFFF),
    )

private val DarkGame2048Tiles =
    listOf(
        Color(0xFF4A443C) to Color(0xFFEDE6DC),
        Color(0xFF5C503D) to Color(0xFFF2E8D6),
        Color(0xFF8A5A2E) to Color(0xFFFFF1E2),
        Color(0xFF99502D) to Color(0xFFFFEDE2),
        Color(0xFFA5452F) to Color(0xFFFFFFFF),
        Color(0xFFB23A2D) to Color(0xFFFFFFFF),
        Color(0xFF9E8027) to Color(0xFFFFF6DC),
        Color(0xFFB38E22) to Color(0xFFFFF8E1),
        Color(0xFF4F7D46) to Color(0xFFF0FFE8),
        Color(0xFF2F7A62) to Color(0xFFFFFFFF),
        Color(0xFF3D9277) to Color(0xFFFFFFFF),
        Color(0xFF6A62A8) to Color(0xFFFFFFFF),
    )

internal val LightLogicaPalette =
    LogicaPalette(
        success = Color(0xFF356748),
        onSuccess = Color(0xFFFFFFFF),
        successContainer = Color(0xFFD9E9D9),
        onSuccessContainer = Color(0xFF1B3824),
        // Eight soft but clearly different hues at a similar lightness, so no region reads as
        // "more important" and neighbouring regions never blend together.
        crownsRegions =
            listOf(
                Color(0xFFC9DCF2), // sky
                Color(0xFFD2E6C2), // leaf
                Color(0xFFF6D3B8), // apricot
                Color(0xFFDDD1F1), // lavender
                Color(0xFFF2CBD3), // rose
                Color(0xFFBFE2DA), // mint
                Color(0xFFF2E2A8), // butter
                Color(0xFFDDD6CB), // stone
            ),
        onCrownsRegion = Color(0xFF191C1E),
    )

internal val DarkLogicaPalette =
    LogicaPalette(
        success = Color(0xFFA8D0AE),
        onSuccess = Color(0xFF183B24),
        successContainer = Color(0xFF304C35),
        onSuccessContainer = Color(0xFFD0E8D0),
        crownsRegions =
            listOf(
                Color(0xFF2F5275), // sky
                Color(0xFF3E6336), // leaf
                Color(0xFF8A5434), // apricot
                Color(0xFF52457A), // lavender
                Color(0xFF763F52), // rose
                Color(0xFF2B645A), // mint
                Color(0xFF7D6520), // butter
                Color(0xFF4D4A45), // stone
            ),
        onCrownsRegion = Color(0xFFE1E3E5),
        game2048Tiles = DarkGame2048Tiles,
    )

/** Provided by [LogicaTheme]; the default keeps previews and tests usable. */
val LocalLogicaPalette = staticCompositionLocalOf { LightLogicaPalette }
