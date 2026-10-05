package com.stanisryz.logica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.stanisryz.logica.settings.ThemeMode

/** Whether this mode resolves to the dark theme right now, with the system choice for SYSTEM. */
@Composable
fun ThemeMode.isDarkTheme(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

@Composable
fun LogicaTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    LogicaTheme(darkTheme = themeMode.isDarkTheme(), content = content)
}
