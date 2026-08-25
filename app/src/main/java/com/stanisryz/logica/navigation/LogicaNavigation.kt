package com.stanisryz.logica.navigation

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.InterstitialOpportunity
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.ads.TerminalActionCoordinator
import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.platform.StoreGateway
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.store.GemPackProductMapping
import com.stanisryz.logica.ui.components.EconomyBar
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameplayExitGuard
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
import com.stanisryz.logica.ui.theme.LogicaMotion
import kotlinx.coroutines.launch

@Composable
internal fun LogicaNavigation(
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    catalogLevelRepository: CatalogLevelRepository,
    gameCompletionRepository: GameCompletionRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    statisticsRepository: StatisticsRepository,
    dailyResultRepository: DailyResultRepository,
    economyRepository: EconomyRepository,
    economy: PlayerEconomy,
    rewardedState: RewardedAdState,
    interstitialOpportunity: InterstitialOpportunity?,
    storeGateway: StoreGateway,
    storeProducts: GemPackProductMapping,
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
    /*
     * Catalog levels are resolved from the frozen pack rather than from a random seed, and the
     * attempt they produce lives only in the gameplay ViewModel.
     */
    val attemptFactory = remember(catalogLevelRepository) { GameAttemptFactory(catalogLevelRepository) }
    /*
     * Unfinished attempts are no longer saved, so both ways back out of gameplay — the header Back
     * button and system/predictive back — ask the active gameplay screen first.
     */
    val exitGuard = remember { GameplayExitGuard() }
    val goBack = { exitGuard.requestBack { backStack.removeLastOrNull() } }
    val currentDestination = backStack.last()
    var showLivesDialog by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    var primaryNavigationBarSize by
        remember(density) {
            mutableStateOf(
                IntSize(width = 0, height = with(density) { PRIMARY_NAVIGATION_BAR_FALLBACK_HEIGHT.roundToPx() }),
            )
        }
    val primaryNavigationBarHeight = with(density) { primaryNavigationBarSize.height.toDp() }
    val activity = LocalActivity.current
    val navigationScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val levelUnavailableMessage = stringResource(R.string.level_content_error)
    var resolvingCatalogLevel by remember { mutableStateOf(false) }

    /** There is one store: everything that offers gems selects the Store tab instead of a dialog. */
    val openStore = {
        showLivesDialog = false
        selectedTab = PrimaryTab.STORE
        collapseToHome(backStack)
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

    // A game card simply leads to its difficulty screen, where the current level of each
    // difficulty is shown; there is no saved-game branch to choose between any more.
    val onGameSelected: (PuzzleType) -> Unit = { puzzleType ->
        backStack.add(puzzleType.startDestination())
    }
    val openDaily: (DailyGameLaunch) -> Unit = { dailyLaunch ->
        backStack.add(dailyLaunch.puzzleType.gameDestination(dailyLaunch.launch))
    }

    /** Resolve the selected difficulty directly; observed level maps are presentation only. */
    val openLevel: (PuzzleType, Difficulty) -> Unit = { puzzleType, difficulty ->
        if (!resolvingCatalogLevel) {
            resolvingCatalogLevel = true
            navigationScope.launch {
                val levelId =
                    runCatching { catalogLevelRepository.currentLevelId(puzzleType, difficulty) }
                        .getOrElse {
                            resolvingCatalogLevel = false
                            snackbarHostState.showSnackbar(levelUnavailableMessage)
                            return@launch
                        }
                resolvingCatalogLevel = false
                backStack.add(puzzleType.gameDestination(GameAttemptLaunch.Level(levelId)))
            }
        }
    }

    /** Re-read progression for Next as well, so every Catalog launch has authoritative identity. */
    val openNextLevel: (PuzzleType, GameAttemptLaunch) -> Unit = { puzzleType, launch ->
        val level = launch as? GameAttemptLaunch.Level
        if (level == null) {
            returnToGameHub(backStack) { selectedTab = it }
        } else {
            navigationScope.launch {
                runCatching { catalogLevelRepository.currentLevelId(puzzleType, level.levelId.difficulty) }
                    .onSuccess { levelId ->
                        backStack[backStack.lastIndex] = puzzleType.gameDestination(GameAttemptLaunch.Level(levelId))
                    }.onFailure { snackbarHostState.showSnackbar(levelUnavailableMessage) }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier =
                    Modifier.padding(
                        bottom = if (currentDestination == AppDestination.Home) primaryNavigationBarHeight else 0.dp,
                    ),
            )
        },
        topBar = {
            AppTopBar(
                title = destinationTitle(currentDestination, selectedTab),
                showBack = currentDestination != AppDestination.Home,
                showWallet = currentDestination.showsWallet(),
                showSettings = currentDestination.showsSettingsAction(),
                economy = economy,
                onBack = goBack,
                onOpenSettings = { backStack.add(AppDestination.Settings) },
                onOpenLives = { showLivesDialog = true },
                onOpenStore = openStore,
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background),
                onBack = goBack,
                transitionSpec = {
                    horizontalSlideTransition(
                        incomingDirection = 1,
                        outgoingDirection = -1,
                    )
                },
                popTransitionSpec = {
                    horizontalSlideTransition(
                        incomingDirection = -1,
                        outgoingDirection = 1,
                    )
                },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider =
                    entryProvider {
                    entry<AppDestination.Home> {
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = primaryNavigationBarHeight),
                            ) {
                                AnimatedContent(
                                    targetState = selectedTab,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clipToBounds()
                                            .background(MaterialTheme.colorScheme.background),
                                    transitionSpec = {
                                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                        horizontalSlideTransition(
                                            incomingDirection = direction,
                                            outgoingDirection = -direction,
                                        )
                                    },
                                    label = "primaryTab",
                                ) { tab ->
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                            .semantics { if (tab != selectedTab) hideFromAccessibility() },
                                    ) {
                                        tabStateHolder.SaveableStateProvider(tab) {
                                            when (tab) {
                                                PrimaryTab.GAME ->
                                                    GameHubRoute(
                                                        dailyChallengeRepository = dailyChallengeRepository,
                                                        statisticsRepository = statisticsRepository,
                                                        dailyResultRepository = dailyResultRepository,
                                                        catalog = GAME_CATALOG_PUZZLE_TYPES,
                                                        economy = economy,
                                                        onGameSelected = onGameSelected,
                                                        onOpenDaily = openDaily,
                                                        onRestoreLife = onRestoreLife,
                                                    )
                                                PrimaryTab.STORE ->
                                                    StoreRoute(
                                                        economy = economy,
                                                        economyRepository = economyRepository,
                                                        storeGateway = storeGateway,
                                                        storeProducts = storeProducts,
                                                    )
                                                PrimaryTab.PROFILE -> ProfileRoute(statisticsRepository)
                                            }
                                        }
                                    }
                                }
                            }
                            AppBottomBar(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .onSizeChanged { primaryNavigationBarSize = it },
                            )
                        }
                    }
                    entry<AppDestination.Settings> {
                        SettingsScreen(settings, onThemeModeChanged, onSoundEnabledChanged, onHapticsEnabledChanged)
                    }
                    entry<AppDestination.BalanceStart> {
                        BalanceStartScreen(
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.BalanceTutorial) },
                            onStart = { difficulty -> openLevel(PuzzleType.BALANCE, difficulty) },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.BalanceTutorial> {
                        BalanceTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<AppDestination.CrownsStart> {
                        CrownsStartScreen(
                            economy = economy,
                            onOpenTutorial = {
                                onCrownsTutorialCompleted(true)
                                backStack.add(AppDestination.CrownsTutorial)
                            },
                            onStart = { difficulty ->
                                onCrownsTutorialCompleted(true)
                                openLevel(PuzzleType.CROWNS, difficulty)
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
                            economy = economy,
                            onOpenTutorial = {
                                onWordTutorialCompleted(true)
                                backStack.add(AppDestination.WordTutorial)
                            },
                            onStart = { difficulty ->
                                onWordTutorialCompleted(true)
                                openLevel(PuzzleType.WORD, difficulty)
                            },
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.WordTutorial> {
                        WordTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<AppDestination.SudokuStart> {
                        SudokuStartScreen(
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.SudokuTutorial) },
                            onStart = { difficulty -> openLevel(PuzzleType.SUDOKU, difficulty) },
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
                            economy = economy,
                            onOpenTutorial = { backStack.add(AppDestination.Game2048Tutorial) },
                            onStart = { difficulty -> openLevel(PuzzleType.GAME_2048, difficulty) },
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
                            attemptFactory = attemptFactory,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            exitGuard = exitGuard,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = goBack,
                            onNextLevel = { openNextLevel(PuzzleType.BALANCE, destination.launch) },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.CrownsGame> { destination ->
                        CrownsGameRoute(
                            launch = destination.launch,
                            attemptFactory = attemptFactory,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            exitGuard = exitGuard,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = goBack,
                            onNextLevel = { openNextLevel(PuzzleType.CROWNS, destination.launch) },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.WordGame> { destination ->
                        WordGameRoute(
                            launch = destination.launch,
                            attemptFactory = attemptFactory,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            exitGuard = exitGuard,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = goBack,
                            onNextLevel = { openNextLevel(PuzzleType.WORD, destination.launch) },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.SudokuGame> { destination ->
                        SudokuGameRoute(
                            launch = destination.launch,
                            attemptFactory = attemptFactory,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            exitGuard = exitGuard,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = goBack,
                            onNextLevel = { openNextLevel(PuzzleType.SUDOKU, destination.launch) },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                    entry<AppDestination.Game2048Game> { destination ->
                        Game2048Route(
                            launch = destination.launch,
                            attemptFactory = attemptFactory,
                            completionRepository = gameCompletionRepository,
                            economyRepository = economyRepository,
                            exitGuard = exitGuard,
                            hapticsEnabled = settings.hapticsEnabled,
                            onBack = goBack,
                            onNextLevel = { openNextLevel(PuzzleType.GAME_2048, destination.launch) },
                            onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                            onTerminalAction = onTerminalAction,
                            onRestoreLife = onRestoreLife,
                        )
                    }
                },
        )
        }
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
    collapseToHome(backStack)
}

/** Keep the Home entry itself so its saved tab and ViewModel state remain intact. */
private fun collapseToHome(backStack: MutableList<AppDestination>) {
    if (backStack.size > 1) backStack.subList(1, backStack.size).clear()
}

private fun PuzzleType.gameDestination(launch: GameAttemptLaunch): AppDestination =
    when (this) {
        PuzzleType.BALANCE -> AppDestination.BalanceGame(launch)
        PuzzleType.CROWNS -> AppDestination.CrownsGame(launch)
        PuzzleType.WORD -> AppDestination.WordGame(launch)
        PuzzleType.SUDOKU -> AppDestination.SudokuGame(launch)
        PuzzleType.GAME_2048 -> AppDestination.Game2048Game(launch)
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

/**
 * The shared header of every screen: what you are looking at, the wallet where it belongs, and the
 * Settings gear on all three primary tabs. The wallet always remains on that same line; its compact
 * presentation protects normal portrait phones without introducing a second header row.
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
            if (showWallet) {
                EconomyBar(
                    economy = economy,
                    onOpenLives = onOpenLives,
                    onOpenGemStore = onOpenStore,
                    compact = true,
                )
            }
            if (showSettings) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
    )
}

private fun horizontalSlideTransition(
    incomingDirection: Int,
    outgoingDirection: Int,
) =
    slideInHorizontally(navigationSlideSpec()) { width ->
        incomingDirection * width
    } togetherWith
        slideOutHorizontally(navigationSlideSpec()) { width ->
            outgoingDirection * width
        }

private fun navigationSlideSpec() =
    tween<IntOffset>(
        durationMillis = LogicaMotion.NAVIGATION_SLIDE_MILLIS,
        easing = FastOutSlowInEasing,
    )

@Composable
private fun AppBottomBar(
    selectedTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier.fillMaxWidth()) {
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

/** Used for the first measure only; [AppBottomBar] immediately supplies its actual inset. */
private val PRIMARY_NAVIGATION_BAR_FALLBACK_HEIGHT = 80.dp

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
