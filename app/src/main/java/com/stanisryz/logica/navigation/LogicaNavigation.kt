package com.stanisryz.logica.navigation

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.InterstitialOpportunity
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.ads.TerminalActionCoordinator
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.game2048.Game2048Launch
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.store.RuStorePayGateway
import com.stanisryz.logica.sudoku.SudokuGameLaunch
import com.stanisryz.logica.ui.components.EconomyBar
import com.stanisryz.logica.ui.components.LivesDialog
import com.stanisryz.logica.ui.screens.BalanceGameRoute
import com.stanisryz.logica.ui.screens.BalanceStartScreen
import com.stanisryz.logica.ui.screens.BalanceTutorialRoute
import com.stanisryz.logica.ui.screens.CrownsGameRoute
import com.stanisryz.logica.ui.screens.CrownsStartScreen
import com.stanisryz.logica.ui.screens.CrownsTutorialRoute
import com.stanisryz.logica.ui.screens.Game2048Route
import com.stanisryz.logica.ui.screens.Game2048StartScreen
import com.stanisryz.logica.ui.screens.Game2048TutorialRoute
import com.stanisryz.logica.ui.screens.GameHubRoute
import com.stanisryz.logica.ui.screens.ProfileRoute
import com.stanisryz.logica.ui.screens.SettingsScreen
import com.stanisryz.logica.ui.screens.StoreRoute
import com.stanisryz.logica.ui.screens.SudokuGameRoute
import com.stanisryz.logica.ui.screens.SudokuStartScreen
import com.stanisryz.logica.ui.screens.SudokuTutorialRoute
import com.stanisryz.logica.ui.screens.WordGameRoute
import com.stanisryz.logica.ui.screens.WordStartScreen
import com.stanisryz.logica.ui.screens.WordTutorialRoute
import com.stanisryz.logica.ui.screens.gameCatalogEntries
import com.stanisryz.logica.ui.theme.LogicaMotion
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.word.WordGameLaunch
import java.security.SecureRandom

@Composable
internal fun LogicaNavigation(
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    gameSessionRepository: GameSessionRepository,
    gameCompletionRepository: GameCompletionRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    statisticsRepository: StatisticsRepository,
    dailyResultRepository: DailyResultRepository,
    economyRepository: EconomyRepository,
    economy: PlayerEconomy,
    hasActiveBalanceSession: Boolean,
    hasActiveCrownsSession: Boolean,
    hasActiveWordSession: Boolean,
    hasActiveSudokuSession: Boolean,
    hasActiveGame2048Session: Boolean,
    rewardedState: RewardedAdState,
    interstitialOpportunity: InterstitialOpportunity?,
    ruStorePayGateway: RuStorePayGateway,
    onRestoreLife: () -> Unit,
    onPreloadRewardedAd: () -> Unit,
    onReleaseRewardedAd: () -> Unit,
    onWatchRewardedAd: (Activity) -> Unit,
    onRetryRewardedAd: () -> Unit,
    onGameplayStarted: () -> Unit,
    onGameplayStopped: () -> Unit,
    onShowInterstitialForTerminalAction: (InterstitialOpportunity, Activity?, () -> Unit) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onCrownsTutorialCompleted: (Boolean) -> Unit,
    onWordTutorialCompleted: (Boolean) -> Unit,
) {
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Home) }
    /*
     * The selected tab is shell state rather than a back-stack entry: the three primary screens
     * share one entry, so switching tabs keeps their ViewModels and — through the state holder
     * below — their scroll positions and other saved Compose state.
     */
    var selectedTab by rememberSaveable { mutableStateOf(PrimaryTab.START) }
    val tabStateHolder = rememberSaveableStateHolder()
    val catalogSeedSource = remember { CatalogSeedSource() }
    val currentDestination = backStack.last()
    var showLivesDialog by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current

    /** There is one store: everything that offers gems selects the Store tab instead of a dialog. */
    val openStore = {
        showLivesDialog = false
        selectedTab = PrimaryTab.STORE
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    /*
     * The rewarded ad is loaded only where it can actually be offered: the player is out of lives
     * and either the Lives dialog with the offer in it is open, or they are standing on a screen a
     * game can be started or played from. The Store and Profile tabs carry the same wallet without
     * ever causing a load, and tutorials and Settings never trigger one either. A wallet with lives
     * in it releases whatever was loaded instead of rotating ads in the background, and a failed
     * load stays failed until the player asks for a retry.
     */
    val rewardedOfferVisible =
        !economy.isGameplayAllowed &&
            (showLivesDialog || currentDestination.allowsRewardedOffer(selectedTab))
    LaunchedEffect(rewardedOfferVisible, rewardedState) {
        when {
            rewardedOfferVisible && rewardedState == RewardedAdState.IDLE -> onPreloadRewardedAd()
            !rewardedOfferVisible -> onReleaseRewardedAd()
        }
    }

    /*
     * The interstitial preloads while a game is actually being played, and only then: the hub, the
     * Store, Profile, Settings, the start screens, and the tutorials never download an ad. Leaving
     * gameplay drops whatever preload was still waiting for the cooldown to run out.
     */
    val gameplayActive = currentDestination.isGameplay()
    LaunchedEffect(gameplayActive) {
        if (gameplayActive) onGameplayStarted() else onGameplayStopped()
    }

    /*
     * The one place an interstitial may appear, and the one place the five games route their
     * terminal actions through. The opportunity exists only because a terminal result and its
     * economy transaction are already durable; it waits on the finished screen until the player
     * chooses what to do next — Retry, a new game, or the Game hub — so the result is always read
     * before an ad, and that chosen action continues once the ad is gone. An ad that is not loaded,
     * still cooling down, or failing to appear is skipped instead of waited for.
     */
    val currentOpportunity by rememberUpdatedState(interstitialOpportunity)
    val currentActivity by rememberUpdatedState(activity)
    val currentShowInterstitial by rememberUpdatedState(onShowInterstitialForTerminalAction)
    val terminalActions =
        remember {
            TerminalActionCoordinator(
                pendingOpportunity = { currentOpportunity },
                present = { opportunity, onFinished ->
                    currentShowInterstitial(opportunity, currentActivity, onFinished)
                },
            )
        }
    val onTerminalAction: (() -> Unit) -> Unit = terminalActions::run

    val catalog =
        gameCatalogEntries(
            hasActiveSession = { puzzleType ->
                when (puzzleType) {
                    PuzzleType.BALANCE -> hasActiveBalanceSession
                    PuzzleType.CROWNS -> hasActiveCrownsSession
                    PuzzleType.WORD -> hasActiveWordSession
                    PuzzleType.SUDOKU -> hasActiveSudokuSession
                    PuzzleType.GAME_2048 -> hasActiveGame2048Session
                    else -> error("$puzzleType is not a Catalog game.")
                }
            },
            onContinue = { puzzleType -> backStack.add(puzzleType.catalogGameDestination()) },
            onNew = { puzzleType -> backStack.add(puzzleType.startDestination()) },
        )
    val openDaily: (DailyGameLaunch) -> Unit = { dailyLaunch ->
        backStack.add(dailyLaunch.gameDestination())
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = destinationTitle(currentDestination, selectedTab),
                showBack = currentDestination != AppDestination.Home,
                showWallet = currentDestination.showsWallet(),
                showSettings = currentDestination.showsSettingsAction(),
                economy = economy,
                onBack = { backStack.removeLastOrNull() },
                onOpenSettings = { backStack.add(AppDestination.Settings) },
                onOpenLives = { showLivesDialog = true },
                onOpenStore = openStore,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = currentDestination.showsBottomBar(),
                enter = fadeIn(tween(LogicaMotion.SHORT_MILLIS)),
                exit = fadeOut(tween(LogicaMotion.SHORT_MILLIS)),
            ) {
                AppBottomBar(selectedTab) { selectedTab = it }
            }
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(contentPadding),
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                (
                    fadeIn(tween(LogicaMotion.SCREEN_MILLIS)) +
                        slideInHorizontally(tween(LogicaMotion.SCREEN_MILLIS)) { width -> width / 20 }
                ) togetherWith (
                    fadeOut(tween(LogicaMotion.SHORT_MILLIS)) +
                        slideOutHorizontally(tween(LogicaMotion.SCREEN_MILLIS)) { width -> -width / 28 }
                )
            },
            popTransitionSpec = {
                (
                    fadeIn(tween(LogicaMotion.SCREEN_MILLIS)) +
                        slideInHorizontally(tween(LogicaMotion.SCREEN_MILLIS)) { width -> -width / 20 }
                ) togetherWith (
                    fadeOut(tween(LogicaMotion.SHORT_MILLIS)) +
                        slideOutHorizontally(tween(LogicaMotion.SCREEN_MILLIS)) { width -> width / 28 }
                )
            },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<AppDestination.Home> {
                        AnimatedContent(
                            targetState = selectedTab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (
                                    fadeIn(tween(LogicaMotion.SHORT_MILLIS)) +
                                        slideInHorizontally(tween(LogicaMotion.SCREEN_MILLIS)) { width ->
                                            direction * (width / 28)
                                        }
                                ) togetherWith fadeOut(tween(LogicaMotion.SHORT_MILLIS))
                            },
                            label = "primaryTab",
                        ) { tab ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .semantics { if (tab != selectedTab) hideFromAccessibility() },
                            ) {
                                tabStateHolder.SaveableStateProvider(tab) {
                                    when (tab) {
                                        PrimaryTab.GAME ->
                                            GameHubRoute(
                                                dailyChallengeRepository = dailyChallengeRepository,
                                                gameSessionRepository = gameSessionRepository,
                                                statisticsRepository = statisticsRepository,
                                                dailyResultRepository = dailyResultRepository,
                                                catalog = catalog,
                                                economy = economy,
                                                onOpenDaily = openDaily,
                                                onRestoreLife = onRestoreLife,
                                            )
                                        PrimaryTab.STORE ->
                                            StoreRoute(
                                                economy = economy,
                                                economyRepository = economyRepository,
                                                ruStorePayGateway = ruStorePayGateway,
                                            )
                                        PrimaryTab.PROFILE -> ProfileRoute(statisticsRepository)
                                    }
                                }
                            }
                        }
                    }
                    entry<AppDestination.Settings> {
                        SettingsScreen(settings, onThemeModeChanged, onSoundEnabledChanged, onHapticsEnabledChanged)
                    }
                    entry<AppDestination.BalanceStart> {
                        BalanceStartScreen(
                            hasActiveSession = hasActiveBalanceSession,
                            tutorialCompleted = settings.balanceTutorialCompleted,
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.BalanceTutorial) },
                            onStart = { difficulty ->
                                backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.New(difficulty, catalogSeedSource.nextSeed())))
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.BalanceTutorial> {
                        BalanceTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<AppDestination.CrownsStart> {
                        CrownsStartScreen(
                            hasActiveSession = hasActiveCrownsSession,
                            tutorialCompleted = settings.crownsTutorialCompleted,
                            economy = economy,
                            onOpenTutorial = {
                                onCrownsTutorialCompleted(true)
                                backStack.add(AppDestination.CrownsTutorial)
                            },
                            onStart = { difficulty ->
                                onCrownsTutorialCompleted(true)
                                backStack.add(
                                    AppDestination.CrownsGame(
                                        CrownsGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.CrownsTutorial> {
                        CrownsTutorialRoute(
                            settingsRepository = settingsRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AppDestination.WordStart> {
                        WordStartScreen(
                            hasActiveSession = hasActiveWordSession,
                            tutorialCompleted = settings.wordTutorialCompleted,
                            economy = economy,
                            onOpenTutorial = {
                                onWordTutorialCompleted(true)
                                backStack.add(AppDestination.WordTutorial)
                            },
                            onStart = { difficulty ->
                                onWordTutorialCompleted(true)
                                backStack.add(
                                    AppDestination.WordGame(
                                        WordGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.WordTutorial> {
                        WordTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<AppDestination.SudokuStart> {
                        SudokuStartScreen(
                            hasActiveSession = hasActiveSudokuSession,
                            tutorialCompleted = settings.sudokuTutorialCompleted,
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.SudokuTutorial) },
                            onStart = { difficulty ->
                                backStack.add(
                                    AppDestination.SudokuGame(
                                        SudokuGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.SudokuTutorial> {
                        SudokuTutorialRoute(
                            settingsRepository = settingsRepository,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AppDestination.Game2048Start> {
                        Game2048StartScreen(
                            hasActiveSession = hasActiveGame2048Session,
                            tutorialCompleted = settings.game2048TutorialCompleted,
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.Game2048Tutorial) },
                            onStart = { difficulty ->
                                backStack.add(
                                    AppDestination.Game2048Game(
                                        Game2048Launch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.Game2048Tutorial> {
                        Game2048TutorialRoute(
                            settingsRepository = settingsRepository,
                            onDone = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AppDestination.BalanceGame> { destination ->
                        BalanceGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.BalanceGame(BalanceGameLaunch.New(difficulty, catalogSeedSource.nextSeed())))
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.BalanceStart)
                            },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.CrownsGame> { destination ->
                        CrownsGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(
                                    AppDestination.CrownsGame(
                                        CrownsGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.CrownsStart)
                            },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.WordGame> { destination ->
                        WordGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(
                                    AppDestination.WordGame(
                                        WordGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.WordStart)
                            },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.SudokuGame> { destination ->
                        SudokuGameRoute(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onNewPuzzle = { difficulty ->
                                backStack.removeLastOrNull()
                                backStack.add(
                                    AppDestination.SudokuGame(
                                        SudokuGameLaunch.New(difficulty, catalogSeedSource.nextSeed()),
                                    ),
                                )
                            },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.SudokuStart)
                            },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.Game2048Game> { destination ->
                        Game2048Route(
                            launch = destination.launch,
                            sessionRepository = gameSessionRepository,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = { backStack.removeLastOrNull() },
                            onStartNew = {
                                backStack.removeLastOrNull()
                                backStack.add(AppDestination.Game2048Start)
                            },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                },
        )
    }

    if (showLivesDialog) {
        LivesDialog(
            economy = economy,
            rewardedState = rewardedState,
            onRestoreLife = onRestoreLife,
            onWatchRewardedAd = { activity?.let(onWatchRewardedAd) },
            onRetryRewardedAd = onRetryRewardedAd,
            onOpenGemStore = openStore,
            onDismiss = { showLivesDialog = false },
        )
    }
}

/** Daily and the catalog share one tab, so every way out of a game leads back to the same hub. */
private fun returnToGameHub(
    backStack: MutableList<AppDestination>,
    onSelectTab: (PrimaryTab) -> Unit,
) {
    onSelectTab(PrimaryTab.GAME)
    while (backStack.size > 1) backStack.removeLastOrNull()
}

private fun PuzzleType.catalogGameDestination(): AppDestination =
    when (this) {
        PuzzleType.BALANCE -> AppDestination.BalanceGame(BalanceGameLaunch.Restore())
        PuzzleType.CROWNS -> AppDestination.CrownsGame(CrownsGameLaunch.Restore())
        PuzzleType.WORD -> AppDestination.WordGame(WordGameLaunch.Restore())
        PuzzleType.SUDOKU -> AppDestination.SudokuGame(SudokuGameLaunch.Restore())
        PuzzleType.GAME_2048 -> AppDestination.Game2048Game(Game2048Launch.Restore())
        else -> error("$this is not a Catalog game.")
    }

private fun PuzzleType.startDestination(): AppDestination =
    when (this) {
        PuzzleType.BALANCE -> AppDestination.BalanceStart
        PuzzleType.CROWNS -> AppDestination.CrownsStart
        PuzzleType.WORD -> AppDestination.WordStart
        PuzzleType.SUDOKU -> AppDestination.SudokuStart
        PuzzleType.GAME_2048 -> AppDestination.Game2048Start
        else -> error("$this is not a Catalog game.")
    }

private fun DailyGameLaunch.gameDestination(): AppDestination =
    when (this) {
        is DailyGameLaunch.Balance -> AppDestination.BalanceGame(launch)
        is DailyGameLaunch.Crowns -> AppDestination.CrownsGame(launch)
        is DailyGameLaunch.Word -> AppDestination.WordGame(launch)
        is DailyGameLaunch.Sudoku -> AppDestination.SudokuGame(launch)
        is DailyGameLaunch.Game2048 -> AppDestination.Game2048Game(launch)
    }

/**
 * The shared header of every screen: what you are looking at, the wallet where it belongs, and the
 * Settings gear on all three primary tabs. On a narrow screen the wallet moves to its own line
 * instead of squeezing the title.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    showWallet: Boolean,
    showSettings: Boolean,
    economy: PlayerEconomy,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLives: () -> Unit,
    onOpenStore: () -> Unit,
) {
    BoxWithConstraints {
        val walletOnOwnLine = showWallet && maxWidth < COMPACT_HEADER_WIDTH
        Column {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (showWallet && !walletOnOwnLine) {
                        EconomyBar(economy = economy, onOpenLives = onOpenLives, onOpenGemStore = onOpenStore)
                    }
                    if (showSettings) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                },
            )
            if (walletOnOwnLine) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.text),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EconomyBar(economy = economy, onOpenLives = onOpenLives, onOpenGemStore = onOpenStore)
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        PrimaryTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    when (tab) {
                        PrimaryTab.GAME -> Icon(Icons.Filled.SportsEsports, null)
                        PrimaryTab.STORE -> Icon(Icons.Filled.Storefront, null)
                        PrimaryTab.PROFILE -> Icon(Icons.Filled.Person, null)
                    }
                },
                label = { Text(stringResource(tab.titleResource)) },
            )
        }
    }
}

@Composable
private fun destinationTitle(
    destination: AppDestination,
    tab: PrimaryTab,
): String =
    stringResource(
        when (destination) {
            AppDestination.Home -> tab.titleResource
            AppDestination.Settings -> R.string.settings
            AppDestination.BalanceStart, is AppDestination.BalanceGame -> R.string.balance
            AppDestination.BalanceTutorial -> R.string.balance_tutorial_title
            AppDestination.CrownsStart, is AppDestination.CrownsGame -> R.string.crowns
            AppDestination.CrownsTutorial -> R.string.crowns_tutorial_title
            AppDestination.WordStart, is AppDestination.WordGame -> R.string.word
            AppDestination.WordTutorial -> R.string.word_tutorial_title
            AppDestination.SudokuStart, is AppDestination.SudokuGame -> R.string.sudoku
            AppDestination.SudokuTutorial -> R.string.sudoku_tutorial_title
            AppDestination.Game2048Start, is AppDestination.Game2048Game -> R.string.game_2048_title
            AppDestination.Game2048Tutorial -> R.string.game_2048_tutorial_title
        },
    )

private class CatalogSeedSource {
    private val random = SecureRandom()

    fun nextSeed(): PuzzleSeed = PuzzleSeed(random.nextLong())
}

/** Below this the title, the wallet, and the gear stop fitting on one comfortable line. */
private val COMPACT_HEADER_WIDTH = 380.dp
