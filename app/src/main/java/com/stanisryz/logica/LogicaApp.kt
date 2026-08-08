package com.stanisryz.logica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.navigation.LogicaNavigation
import com.stanisryz.logica.settings.SettingsViewModel
import com.stanisryz.logica.settings.SettingsViewModelFactory
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
fun LogicaApp() {
    val application = LocalContext.current.applicationContext as LogicaApplication
    val repository = application.container.settingsRepository
    val viewModelFactory =
        remember(repository) {
            SettingsViewModelFactory(repository)
        }
    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    LogicaTheme(themeMode = settings.themeMode) {
        LogicaNavigation(
            settings = settings,
            onThemeModeChanged = settingsViewModel::setThemeMode,
            onSoundEnabledChanged = settingsViewModel::setSoundEnabled,
            onHapticsEnabledChanged = settingsViewModel::setHapticsEnabled,
        )
    }
}
