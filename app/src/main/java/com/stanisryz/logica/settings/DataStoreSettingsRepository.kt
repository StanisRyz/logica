package com.stanisryz.logica.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                UserSettings(
                    themeMode =
                        preferences[THEME_MODE]
                            ?.let { storedValue ->
                                ThemeMode.entries.firstOrNull { it.name == storedValue }
                            } ?: ThemeMode.SYSTEM,
                    soundEnabled = preferences[SOUND_ENABLED] ?: true,
                    hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
                    balanceTutorialCompleted = preferences[BALANCE_TUTORIAL_COMPLETED] ?: false,
                )
            }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTICS_ENABLED] = enabled
        }
    }

    override suspend fun setBalanceTutorialCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[BALANCE_TUTORIAL_COMPLETED] = completed
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val BALANCE_TUTORIAL_COMPLETED = booleanPreferencesKey("balance_tutorial_completed")
    }
}
