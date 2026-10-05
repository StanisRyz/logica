package com.stanisryz.logica

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.stanisryz.logica.platform.android.AndroidGameSoundPlayer
import com.stanisryz.logica.settings.SettingsViewModel
import com.stanisryz.logica.settings.SettingsViewModelFactory
import com.stanisryz.logica.statistics.toProfileStatistics
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.profile.Achievement
import com.stanisryz.logica.ui.profile.AchievementAnnouncementHost
import com.stanisryz.logica.ui.profile.AchievementAnnouncer
import com.stanisryz.logica.ui.profile.LocalAchievementAnnouncer
import com.stanisryz.logica.ui.profile.unlockedAchievementIds
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
    // Read once at startup so the ledger is already being watched before the first ad decision.
    val ownedPurchases = application.container.ownedPurchases
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
                adsRemoved = { ownedPurchases.value.noAds },
            )
        }
    val interstitialController: InterstitialAdController = viewModel(factory = interstitialControllerFactory)
    val interstitialOpportunity by interstitialController.pendingOpportunity.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val soundScope = rememberCoroutineScope()
    val currentSettings by rememberUpdatedState(settings)
    val soundPlayer =
        remember(context) { AndroidGameSoundPlayer(context, soundScope) { currentSettings.soundEnabled } }
    DisposableEffect(soundPlayer) { onDispose { soundPlayer.release() } }

    // A newly reached achievement is announced once: inside an open result card, else as a banner.
    val achievementAnnouncer = remember { AchievementAnnouncer() }
    val statisticsFlow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    LaunchedEffect(statisticsFlow) {
        statisticsFlow
            .map { snapshot -> snapshot.statistics.toProfileStatistics().unlockedAchievementIds() }
            .distinctUntilChanged()
            .collect { unlocked ->
                val seen = settingsRepository.settings.first().seenAchievements
                if (seen == null) {
                    // The first look only records what is already reached, so nothing old is announced.
                    settingsRepository.setSeenAchievements(unlocked)
                } else {
                    val fresh = Achievement.entries.filter { it.id in unlocked && it.id !in seen }
                    if (fresh.isNotEmpty()) {
                        achievementAnnouncer.announce(fresh)
                        settingsRepository.setSeenAchievements(seen + unlocked)
                    }
                }
            }
    }

    val darkTheme = settings.themeMode.isDarkTheme()
    SystemBarsFollowTheme(darkTheme)
    LogicaTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalGameSounds provides soundPlayer,
            LocalAchievementAnnouncer provides achievementAnnouncer,
        ) {
            Box(Modifier.fillMaxSize()) {
                LogicaNavigation(
                    settings = settings,
                    settingsRepository = settingsRepository,
                    catalogLevelRepository = catalogLevelRepository,
                    gameCompletionRepository = gameCompletionRepository,
                    dailyChallengeRepository = dailyChallengeRepository,
                    statisticsRepository = statisticsRepository,
                    dailyResultRepository = dailyResultRepository,
                    dailyRewardsRepository = application.container.dailyRewardsRepository,
                    economyRepository = economyRepository,
                    game2048BestScore = application.container.game2048BestScore,
                    economy = economy,
                    rewardedState = rewardedState,
                    interstitialOpportunity = interstitialOpportunity,
                    storeGateway = platformServices.store,
                    storeProducts = platform.gemPackProducts,
                    onRestoreLife = economyViewModel::refillLife,
                    onPreloadRewardedAd = rewardedController::preload,
                    onReleaseRewardedAd = rewardedController::release,
                    onWatchRewardedAd = { activity, kind -> rewardedController.show(AndroidAdDisplayHost(activity), kind) },
                    onWatchContinueAd = { activity, onGranted ->
                        rewardedController.showContinue(AndroidAdDisplayHost(activity), onGranted)
                    },
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
                    onTutorialSeen = settingsViewModel::markTutorialSeen,
                    onLastPlayed = settingsViewModel::setLastPlayed,
                )
                AchievementAnnouncementHost(
                    announcer = achievementAnnouncer,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                )
            }
        }
    }
}
