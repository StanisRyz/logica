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
)

internal val LightLogicaPalette =
    LogicaPalette(
        success = Color(0xFF326B41),
        onSuccess = Color(0xFFFFFFFF),
        successContainer = Color(0xFFD3E8D6),
        onSuccessContainer = Color(0xFF14301D),
        crownsRegions =
            listOf(
                Color(0xFFD3E2F2),
                Color(0xFFDCE8D7),
                Color(0xFFF2E3D3),
                Color(0xFFE4DDEF),
                Color(0xFFF2DCDF),
                Color(0xFFD6E7E7),
                Color(0xFFECE8D2),
                Color(0xFFE1E2E6),
            ),
        onCrownsRegion = Color(0xFF191C1E),
    )

internal val DarkLogicaPalette =
    LogicaPalette(
        success = Color(0xFFA5CFAB),
        onSuccess = Color(0xFF0C3A1D),
        successContainer = Color(0xFF2C4732),
        onSuccessContainer = Color(0xFFC1E4C6),
        crownsRegions =
            listOf(
                Color(0xFF2B3B4A),
                Color(0xFF334230),
                Color(0xFF4A3D2E),
                Color(0xFF3B3549),
                Color(0xFF4A343A),
                Color(0xFF2C4144),
                Color(0xFF444134),
                Color(0xFF34363B),
            ),
        onCrownsRegion = Color(0xFFE1E3E5),
    )

/** Provided by [LogicaTheme]; the default keeps previews and tests usable. */
val LocalLogicaPalette = staticCompositionLocalOf { LightLogicaPalette }
