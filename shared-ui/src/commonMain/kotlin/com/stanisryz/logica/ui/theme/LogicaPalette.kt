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
        crownsRegions =
            listOf(
                Color(0xFFDCE3EC),
                Color(0xFFE1E8D9),
                Color(0xFFF1E0D1),
                Color(0xFFE7E0EA),
                Color(0xFFEEDCDA),
                Color(0xFFDCE9E4),
                Color(0xFFF0E7CC),
                Color(0xFFE3E1D8),
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
                Color(0xFF354554),
                Color(0xFF3C4935),
                Color(0xFF504033),
                Color(0xFF443B4B),
                Color(0xFF503A3B),
                Color(0xFF344744),
                Color(0xFF4C4732),
                Color(0xFF3A3C36),
            ),
        onCrownsRegion = Color(0xFFE1E3E5),
        game2048Tiles = DarkGame2048Tiles,
    )

/** Provided by [LogicaTheme]; the default keeps previews and tests usable. */
val LocalLogicaPalette = staticCompositionLocalOf { LightLogicaPalette }
