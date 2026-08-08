package com.stanisryz.logica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.catalog.CatalogViewModel
import com.stanisryz.logica.catalog.CatalogViewModelFactory
import com.stanisryz.logica.navigation.LogicaNavigation
import com.stanisryz.logica.settings.SettingsViewModel
import com.stanisryz.logica.settings.SettingsViewModelFactory
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
fun LogicaApp() {
    val application = LocalContext.current.applicationContext as LogicaApplication
    val settingsRepository = application.container.settingsRepository
    val gameSessionRepository = application.container.gameSessionRepository
    val dailyChallengeRepository = application.container.dailyChallengeRepository
    val gameCompletionRepository = application.container.gameCompletionRepository
    val statisticsRepository = application.container.statisticsRepository
    val viewModelFactory =
        remember(settingsRepository) {
            SettingsViewModelFactory(settingsRepository)
        }
    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val catalogViewModelFactory =
        remember(gameSessionRepository) {
            CatalogViewModelFactory(gameSessionRepository)
        }
    val catalogViewModel: CatalogViewModel = viewModel(factory = catalogViewModelFactory)
    val hasActiveBalanceSession by catalogViewModel.hasActiveBalanceSession.collectAsStateWithLifecycle()

    LogicaTheme(themeMode = settings.themeMode) {
        LogicaNavigation(
            settings = settings,
            settingsRepository = settingsRepository,
            gameSessionRepository = gameSessionRepository,
            gameCompletionRepository = gameCompletionRepository,
            dailyChallengeRepository = dailyChallengeRepository,
            statisticsRepository = statisticsRepository,
            hasActiveBalanceSession = hasActiveBalanceSession,
            onThemeModeChanged = settingsViewModel::setThemeMode,
            onSoundEnabledChanged = settingsViewModel::setSoundEnabled,
            onHapticsEnabledChanged = settingsViewModel::setHapticsEnabled,
        )
    }
}
