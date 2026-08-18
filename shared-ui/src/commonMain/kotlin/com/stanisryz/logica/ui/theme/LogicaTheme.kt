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
        primary = Color(0xFF3F515E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCBDDEB),
        onPrimaryContainer = Color(0xFF1B2A35),
        secondary = Color(0xFF52646F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E3EB),
        onSecondaryContainer = Color(0xFF101D25),
        tertiary = Color(0xFF665F6F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE7DFF0),
        onTertiaryContainer = Color(0xFF211B29),
        error = Color(0xFFA33C33),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF7DDDA),
        onErrorContainer = Color(0xFF41110D),
        background = Color(0xFFFBFCFD),
        onBackground = Color(0xFF191C1E),
        surface = Color(0xFFFBFCFD),
        onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFDDE3E8),
        onSurfaceVariant = Color(0xFF41484D),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F7F9),
        surfaceContainer = Color(0xFFEEF1F4),
        surfaceContainerHigh = Color(0xFFE8ECF0),
        surfaceContainerHighest = Color(0xFFE2E7EB),
        outline = Color(0xFF71787D),
        outlineVariant = Color(0xFFC1C7CC),
        inverseSurface = Color(0xFF2E3133),
        inverseOnSurface = Color(0xFFEFF1F3),
        inversePrimary = Color(0xFFB8CAD7),
        scrim = Color(0xFF000000),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFB8CAD7),
        onPrimary = Color(0xFF22323E),
        primaryContainer = Color(0xFF384955),
        onPrimaryContainer = Color(0xFFD4E4F1),
        secondary = Color(0xFFBAC9D1),
        onSecondary = Color(0xFF243239),
        secondaryContainer = Color(0xFF3A4950),
        onSecondaryContainer = Color(0xFFD6E5ED),
        tertiary = Color(0xFFD0C3D6),
        onTertiary = Color(0xFF352E3C),
        tertiaryContainer = Color(0xFF4C4453),
        onTertiaryContainer = Color(0xFFECDFF2),
        error = Color(0xFFF0B4AE),
        onError = Color(0xFF5C1710),
        errorContainer = Color(0xFF7C2E26),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF101416),
        onBackground = Color(0xFFE1E3E5),
        surface = Color(0xFF101416),
        onSurface = Color(0xFFE1E3E5),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CC),
        surfaceContainerLowest = Color(0xFF0A0E10),
        surfaceContainerLow = Color(0xFF181C1E),
        surfaceContainer = Color(0xFF1C2022),
        surfaceContainerHigh = Color(0xFF262A2D),
        surfaceContainerHighest = Color(0xFF313538),
        outline = Color(0xFF8B9297),
        outlineVariant = Color(0xFF41484D),
        inverseSurface = Color(0xFFE1E3E5),
        inverseOnSurface = Color(0xFF2E3133),
        inversePrimary = Color(0xFF3F515E),
        scrim = Color(0xFF000000),
    )

private val LogicaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
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
