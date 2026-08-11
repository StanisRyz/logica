package com.stanisryz.logica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.ads.RewardedLifeController
import com.stanisryz.logica.ads.RewardedLifeControllerFactory
import com.stanisryz.logica.catalog.CatalogViewModel
import com.stanisryz.logica.catalog.CatalogViewModelFactory
import com.stanisryz.logica.economy.EconomyViewModel
import com.stanisryz.logica.economy.EconomyViewModelFactory
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
    val dailyResultRepository = application.container.dailyResultRepository
    val economyRepository = application.container.economyRepository
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
    val hasActiveCrownsSession by catalogViewModel.hasActiveCrownsSession.collectAsStateWithLifecycle()
    val hasActiveWordSession by catalogViewModel.hasActiveWordSession.collectAsStateWithLifecycle()
    val economyViewModelFactory =
        remember(economyRepository) {
            EconomyViewModelFactory(economyRepository)
        }
    val economyViewModel: EconomyViewModel = viewModel(factory = economyViewModelFactory)
    val economy by economyViewModel.economy.collectAsStateWithLifecycle()
    val rewardedControllerFactory =
        remember(economyRepository) {
            RewardedLifeControllerFactory(application, economyRepository)
        }
    val rewardedController: RewardedLifeController = viewModel(factory = rewardedControllerFactory)
    val rewardedState by rewardedController.state.collectAsStateWithLifecycle()

    LogicaTheme(themeMode = settings.themeMode) {
        LogicaNavigation(
            settings = settings,
            settingsRepository = settingsRepository,
            gameSessionRepository = gameSessionRepository,
            gameCompletionRepository = gameCompletionRepository,
            dailyChallengeRepository = dailyChallengeRepository,
            statisticsRepository = statisticsRepository,
            dailyResultRepository = dailyResultRepository,
            economyRepository = economyRepository,
            economy = economy,
            hasActiveBalanceSession = hasActiveBalanceSession,
            hasActiveCrownsSession = hasActiveCrownsSession,
            hasActiveWordSession = hasActiveWordSession,
            rewardedState = rewardedState,
            onRestoreLife = economyViewModel::refillLife,
            onPreloadRewardedAd = rewardedController::preload,
            onReleaseRewardedAd = rewardedController::release,
            onWatchRewardedAd = rewardedController::show,
            onRetryRewardedAd = rewardedController::retry,
            onThemeModeChanged = settingsViewModel::setThemeMode,
            onSoundEnabledChanged = settingsViewModel::setSoundEnabled,
            onHapticsEnabledChanged = settingsViewModel::setHapticsEnabled,
            onCrownsTutorialCompleted = settingsViewModel::setCrownsTutorialCompleted,
            onWordTutorialCompleted = settingsViewModel::setWordTutorialCompleted,
        )
    }
}
