package com.stanisryz.logica.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF315B4B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE9DF),
        onPrimaryContainer = Color(0xFF1E3B30),
        secondary = Color(0xFF80543D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0DDD0),
        onSecondaryContainer = Color(0xFF492B1D),
        tertiary = Color(0xFF756033),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF0E5C8),
        onTertiaryContainer = Color(0xFF3A2F17),
        error = Color(0xFFA23F36),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF7DDDA),
        onErrorContainer = Color(0xFF41110D),
        background = Color(0xFFF7F3EA),
        onBackground = Color(0xFF252822),
        surface = Color(0xFFFCFAF4),
        onSurface = Color(0xFF252822),
        surfaceVariant = Color(0xFFE8E1D4),
        onSurfaceVariant = Color(0xFF4A4B43),
        surfaceContainerLowest = Color(0xFFFFFDF8),
        surfaceContainerLow = Color(0xFFF4EFE5),
        surfaceContainer = Color(0xFFEEE8DC),
        surfaceContainerHigh = Color(0xFFE8E0D2),
        surfaceContainerHighest = Color(0xFFDED5C5),
        surfaceBright = Color(0xFFFFFDF8),
        surfaceDim = Color(0xFFE0D9CC),
        outline = Color(0xFF746E61),
        outlineVariant = Color(0xFFCFC6B7),
        inverseSurface = Color(0xFF302D26),
        inverseOnSurface = Color(0xFFF6F0E5),
        inversePrimary = Color(0xFFB7D8C4),
        scrim = Color(0xFF000000),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFB7D8C4),
        onPrimary = Color(0xFF18372B),
        primaryContainer = Color(0xFF365746),
        onPrimaryContainer = Color(0xFFD7EBDD),
        secondary = Color(0xFFE0B49D),
        onSecondary = Color(0xFF482B1C),
        secondaryContainer = Color(0xFF573D31),
        onSecondaryContainer = Color(0xFFF7DDD0),
        tertiary = Color(0xFFDDC990),
        onTertiary = Color(0xFF403316),
        tertiaryContainer = Color(0xFF534522),
        onTertiaryContainer = Color(0xFFF4E6BB),
        error = Color(0xFFF2B6AE),
        onError = Color(0xFF5C1710),
        errorContainer = Color(0xFF7C332A),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF171A17),
        onBackground = Color(0xFFE8E7DE),
        surface = Color(0xFF1B1E1A),
        onSurface = Color(0xFFE8E7DE),
        surfaceVariant = Color(0xFF45473F),
        onSurfaceVariant = Color(0xFFC5C6BA),
        surfaceContainerLowest = Color(0xFF111410),
        surfaceContainerLow = Color(0xFF22251F),
        surfaceContainer = Color(0xFF282B24),
        surfaceContainerHigh = Color(0xFF30332B),
        surfaceContainerHighest = Color(0xFF393C33),
        surfaceBright = Color(0xFF4A4D44),
        surfaceDim = Color(0xFF151814),
        outline = Color(0xFF929387),
        outlineVariant = Color(0xFF494B42),
        inverseSurface = Color(0xFFE8E7DE),
        inverseOnSurface = Color(0xFF302D26),
        inversePrimary = Color(0xFF315B4B),
        scrim = Color(0xFF000000),
    )

private val LogicaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )

/** Host-neutral product theme. The application host resolves system/user settings to [darkTheme]. */
@Composable
fun LogicaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLogicaPalette provides if (darkTheme) DarkLogicaPalette else LightLogicaPalette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography(),
            shapes = LogicaShapes,
            content = content,
        )
    }
}
