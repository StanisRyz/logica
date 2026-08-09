package com.stanisryz.logica.settings

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val balanceTutorialCompleted: Boolean = false,
    val crownsTutorialCompleted: Boolean = false,
)
