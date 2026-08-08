package com.stanisryz.logica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stanisryz.logica.settings.ThemeMode

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF3F515E),
        secondary = Color(0xFF52646F),
        tertiary = Color(0xFF665F6F),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFB8CAD7),
        secondary = Color(0xFFBAC9D1),
        tertiary = Color(0xFFD0C3D6),
    )

@Composable
fun LogicaTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content,
    )
}
