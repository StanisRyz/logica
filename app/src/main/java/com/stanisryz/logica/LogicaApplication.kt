package com.stanisryz.logica

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.stanisryz.logica.settings.DataStoreSettingsRepository
import com.stanisryz.logica.settings.SettingsRepository

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

class LogicaApplication : Application() {
    internal val container: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}

internal class AppContainer(
    context: Context,
) {
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(
            dataStore = context.userSettingsDataStore,
        )
}
