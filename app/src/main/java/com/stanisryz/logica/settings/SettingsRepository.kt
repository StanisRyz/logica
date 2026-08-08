package com.stanisryz.logica.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setBalanceTutorialCompleted(completed: Boolean)
}
