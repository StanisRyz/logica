package com.stanisryz.logica.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.stanisryz.logica.ui.word.ABSENT_KEY_CONTAINER_ALPHA
import com.stanisryz.logica.ui.word.ABSENT_KEY_CONTENT_ALPHA
import kotlin.math.pow

/**
 * WCAG 2.1 contrast of the theme's own colour pairs: text 4.5:1, large text and non-text elements
 * (icons, borders, states) 3:1. [checkedPairs] lists the pairs where a token, not a single screen,
 * decides readability, and a test keeps every one of them at its threshold in both themes.
 * Decorative dividers and grid lines (`outlineVariant`) are not listed: no card relies on them alone.
 */
object LogicaContrast {
    const val TEXT = 4.5
    const val LARGE_OR_GRAPHIC = 3.0

    data class Pair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    ) {
        val ratio: Double get() = ratio(foreground, background)
    }

    /** The WCAG contrast ratio of two opaque colours, from 1 to 21. */
    fun ratio(
        first: Color,
        second: Color,
    ): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** [top] drawn over the opaque [bottom], as the screen shows a translucent colour. */
    fun over(
        top: Color,
        bottom: Color,
    ): Color =
        Color(
            red = top.red * top.alpha + bottom.red * (1 - top.alpha),
            green = top.green * top.alpha + bottom.green * (1 - top.alpha),
            blue = top.blue * top.alpha + bottom.blue * (1 - top.alpha),
        )

    fun checkedPairs(): List<Pair> =
        pairs("light", LightColorScheme, LightLogicaPalette) + pairs("dark", DarkColorScheme, DarkLogicaPalette)

    private fun pairs(
        theme: String,
        colors: ColorScheme,
        palette: LogicaPalette,
    ): List<Pair> {
        val absentKey = over(colors.surfaceVariant.copy(alpha = ABSENT_KEY_CONTAINER_ALPHA), colors.background)
        val absentLetter = over(colors.onSurfaceVariant.copy(alpha = ABSENT_KEY_CONTENT_ALPHA), absentKey)
        return listOf(
            Pair("$theme: earned star on the result card", palette.star, colors.surfaceContainerHigh, LARGE_OR_GRAPHIC),
            Pair("$theme: star on Profile", palette.star, colors.surfaceContainer, LARGE_OR_GRAPHIC),
            Pair("$theme: missing star on the result card", colors.outline, colors.surfaceContainerHigh, LARGE_OR_GRAPHIC),
            Pair("$theme: unused mistake mark", colors.outline, colors.background, LARGE_OR_GRAPHIC),
            Pair("$theme: empty Word tile border", colors.outline, colors.surface, LARGE_OR_GRAPHIC),
            Pair("$theme: Word absent tile letter", colors.onSurfaceVariant, colors.surfaceVariant, TEXT),
            Pair("$theme: Word absent key letter", absentLetter, absentKey, TEXT),
        ) +
            listOf(
                "surface" to colors.surface,
                "background" to colors.background,
                "surfaceContainerLow" to colors.surfaceContainerLow,
                "surfaceContainer" to colors.surfaceContainer,
                "surfaceContainerHigh" to colors.surfaceContainerHigh,
                "surfaceContainerHighest" to colors.surfaceContainerHighest,
            ).map { (name, surface) -> Pair("$theme: onSurfaceVariant on $name", colors.onSurfaceVariant, surface, TEXT) } +
            // 2048 numbers are large bold text.
            palette.game2048Tiles.mapIndexed { index, (container, content) ->
                Pair("$theme: 2048 tile ${2 shl index}", content, container, LARGE_OR_GRAPHIC)
            }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
