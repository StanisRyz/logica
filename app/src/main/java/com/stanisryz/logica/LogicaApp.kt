package com.stanisryz.logica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.ads.InterstitialAdController
import com.stanisryz.logica.ads.InterstitialAdControllerFactory
import com.stanisryz.logica.ads.RewardedLifeController
import com.stanisryz.logica.ads.RewardedLifeControllerFactory
import com.stanisryz.logica.economy.EconomyViewModel
import com.stanisryz.logica.economy.EconomyViewModelFactory
import com.stanisryz.logica.navigation.LogicaNavigation
import com.stanisryz.logica.platform.android.AndroidAdDisplayHost
import com.stanisryz.logica.settings.SettingsViewModel
import com.stanisryz.logica.settings.SettingsViewModelFactory
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
fun LogicaApp() {
    val application = LocalContext.current.applicationContext as LogicaApplication
    val settingsRepository = application.container.settingsRepository
    val catalogLevelRepository = application.container.catalogLevelRepository
    val dailyChallengeRepository = application.container.dailyChallengeRepository
    val gameCompletionRepository = application.container.gameCompletionRepository
    val statisticsRepository = application.container.statisticsRepository
    val dailyResultRepository = application.container.dailyResultRepository
    val economyRepository = application.container.economyRepository
    val platform = application.container.platform
    val platformServices = platform.services
    val viewModelFactory =
        remember(settingsRepository) {
            SettingsViewModelFactory(settingsRepository)
        }
    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val economyViewModelFactory =
        remember(economyRepository) {
            EconomyViewModelFactory(economyRepository)
        }
    val economyViewModel: EconomyViewModel = viewModel(factory = economyViewModelFactory)
    val economy by economyViewModel.economy.collectAsStateWithLifecycle()
    val rewardedControllerFactory =
        remember(economyRepository, platformServices.rewardedAds) {
            RewardedLifeControllerFactory(platformServices.rewardedAds, economyRepository)
        }
    val rewardedController: RewardedLifeController = viewModel(factory = rewardedControllerFactory)
    val rewardedState by rewardedController.state.collectAsStateWithLifecycle()
    val interstitialControllerFactory =
        remember(platformServices.fullscreenAds) {
            InterstitialAdControllerFactory(
                ads = platformServices.fullscreenAds,
                opportunities = application.container.interstitialOpportunities,
                cooldown = application.container.interstitialCooldownPolicy,
            )
        }
    val interstitialController: InterstitialAdController = viewModel(factory = interstitialControllerFactory)
    val interstitialOpportunity by interstitialController.pendingOpportunity.collectAsStateWithLifecycle()

    LogicaTheme(themeMode = settings.themeMode) {
        LogicaNavigation(
            settings = settings,
            settingsRepository = settingsRepository,
            catalogLevelRepository = catalogLevelRepository,
            gameCompletionRepository = gameCompletionRepository,
            dailyChallengeRepository = dailyChallengeRepository,
            statisticsRepository = statisticsRepository,
            dailyResultRepository = dailyResultRepository,
            economyRepository = economyRepository,
            economy = economy,
            rewardedState = rewardedState,
            interstitialOpportunity = interstitialOpportunity,
            storeGateway = platformServices.store,
            storeProducts = platform.gemPackProducts,
            onRestoreLife = economyViewModel::refillLife,
            onPreloadRewardedAd = rewardedController::preload,
            onReleaseRewardedAd = rewardedController::release,
            onWatchRewardedAd = { rewardedController.show(AndroidAdDisplayHost(it)) },
            onRetryRewardedAd = rewardedController::retry,
            onGameplayStarted = interstitialController::onGameplayStarted,
            onGameplayStopped = interstitialController::onGameplayStopped,
            onShowInterstitialForTerminalAction = { opportunity, activity, onFinished ->
                interstitialController.showForTerminalAction(
                    opportunity,
                    activity?.let(::AndroidAdDisplayHost),
                    onFinished,
                )
            },
            onThemeModeChanged = settingsViewModel::setThemeMode,
            onSoundEnabledChanged = settingsViewModel::setSoundEnabled,
            onHapticsEnabledChanged = settingsViewModel::setHapticsEnabled,
            onCrownsTutorialCompleted = settingsViewModel::setCrownsTutorialCompleted,
            onWordTutorialCompleted = settingsViewModel::setWordTutorialCompleted,
        )
    }
}
