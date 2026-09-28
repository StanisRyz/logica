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
    )

/** Provided by [LogicaTheme]; the default keeps previews and tests usable. */
val LocalLogicaPalette = staticCompositionLocalOf { LightLogicaPalette }
