package com.stanisryz.logica.navigation

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.InterstitialOpportunity
import com.stanisryz.logica.ads.RewardedAdKind
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.ads.TerminalActionCoordinator
import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyGameLaunch
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.economy.DailyRewardsRepository
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.game2048.Game2048BestScore
import com.stanisryz.logica.platform.StoreGateway
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.runCatchingCancellable
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.settings.tutorialCompleted
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.store.GemPackProductMapping
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameRulesSheet
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LicensesContent
import com.stanisryz.logica.ui.components.LivesDialog
import com.stanisryz.logica.ui.components.LocalResultLives
import com.stanisryz.logica.ui.components.LocalSecondChanceAd
import com.stanisryz.logica.ui.components.PuzzleStartScreen
import com.stanisryz.logica.ui.components.ResultLives
import com.stanisryz.logica.ui.components.SecondChanceAd
import com.stanisryz.logica.ui.components.licenseNoticesFor
import com.stanisryz.logica.ui.screens.AchievementsRoute
import com.stanisryz.logica.ui.screens.BalanceGameRoute
import com.stanisryz.logica.ui.screens.BalanceTutorialRoute
import com.stanisryz.logica.ui.screens.BlockSudokuRoute
import com.stanisryz.logica.ui.screens.BlockSudokuTutorialRoute
import com.stanisryz.logica.ui.screens.CrownsGameRoute
import com.stanisryz.logica.ui.screens.CrownsTutorialRoute
import com.stanisryz.logica.ui.screens.Game2048Route
import com.stanisryz.logica.ui.screens.Game2048TutorialRoute
import com.stanisryz.logica.ui.screens.GameHubRoute
import com.stanisryz.logica.ui.screens.NonogramGameRoute
import com.stanisryz.logica.ui.screens.NonogramTutorialRoute
import com.stanisryz.logica.ui.screens.ProfilePageRoute
import com.stanisryz.logica.ui.screens.ProfileRoute
import com.stanisryz.logica.ui.screens.SettingsScreen
import com.stanisryz.logica.ui.screens.StoreRewardedOffers
import com.stanisryz.logica.ui.screens.StoreRoute
import com.stanisryz.logica.ui.screens.StoreSheet
import com.stanisryz.logica.ui.screens.SudokuGameRoute
import com.stanisryz.logica.ui.screens.SudokuTutorialRoute
import com.stanisryz.logica.ui.screens.WordGameRoute
import com.stanisryz.logica.ui.screens.WordTutorialRoute
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun LogicaNavigation(
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    catalogLevelRepository: CatalogLevelRepository,
    gameCompletionRepository: GameCompletionRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    statisticsRepository: StatisticsRepository,
    dailyResultRepository: DailyResultRepository,
    dailyRewardsRepository: DailyRewardsRepository,
    economyRepository: EconomyRepository,
    game2048BestScore: Game2048BestScore,
    economy: PlayerEconomy,
    rewardedState: RewardedAdState,
    interstitialOpportunity: InterstitialOpportunity?,
    storeGateway: StoreGateway,
    storeProducts: GemPackProductMapping,
    onRestoreLife: () -> Unit,
    onPreloadRewardedAd: () -> Unit,
    onReleaseRewardedAd: () -> Unit,
    onWatchRewardedAd: (Activity, RewardedAdKind) -> Unit,
    onWatchContinueAd: (Activity, () -> Unit) -> Unit,
    onRetryRewardedAd: () -> Unit,
    onGameplayStarted: () -> Unit,
    onGameplayStopped: () -> Unit,
    onShowInterstitialForTerminalAction: (InterstitialOpportunity, Activity?, () -> Unit) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onHapticsEnabledChanged: (Boolean) -> Unit,
    onTutorialSeen: (PuzzleType) -> Unit,
    onLastPlayed: (PuzzleType, Difficulty) -> Unit,
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
    val abandonScope = rememberCoroutineScope()
    val exitGuard =
        remember(economyRepository) {
            GameplayExitGuard(
                onAbandon = {
                    abandonScope.launch { economyRepository.spendLifeForAbandonedAttempt(UUID.randomUUID().toString()) }
                },
            )
        }
    val goBack = { exitGuard.requestBack { backStack.removeLastOrNull() } }
    val currentDestination = backStack.last()
    var showLivesDialog by rememberSaveable { mutableStateOf(false) }
    var rulesFor by remember { mutableStateOf<PuzzleType?>(null) }
    rulesFor?.let { puzzleType -> GameRulesSheet(puzzleType, onDismiss = { rulesFor = null }) }
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

    var showStoreSheet by rememberSaveable { mutableStateOf(false) }

    /**
     * There is one store. From a running game it opens as a sheet over the board, so the attempt and
     * its progress stay exactly where they were; everywhere else it selects the Store tab.
     */
    val openStore = {
        showLivesDialog = false
        if (currentDestination.opensStoreAsSheet()) {
            showStoreSheet = true
        } else {
            selectedTab = PrimaryTab.STORE
            collapseToHome(backStack)
        }
    }
    // The sheet belongs to the game it was opened from and never outlives it.
    LaunchedEffect(currentDestination) {
        if (!currentDestination.opensStoreAsSheet()) showStoreSheet = false
    }

    /*
     * The rewarded ad is loaded only where it can actually be offered: the Store (its +1 gem offer,
     * and +1 life while one is missing), the Lives dialog while a life is missing, or a screen a game
     * can be started or played from while the player is out of lives. Profile, tutorials, and
     * Settings never trigger a load. Leaving those places releases whatever was loaded instead of
     * rotating ads in the background, and a failed load stays failed until the player asks for a retry.
     */
    val storeVisible = showStoreSheet || (currentDestination == AppDestination.Home && selectedTab == PrimaryTab.STORE)
    // A game waiting on its one ad-paid second chance after the third mistake.
    var secondChanceVisible by remember { mutableStateOf(false) }
    val rewardedOfferVisible =
        secondChanceVisible ||
            storeVisible ||
            (!economy.isFull && showLivesDialog) ||
            (!economy.isGameplayAllowed && currentDestination.allowsRewardedOffer(selectedTab))
    val storeRewardedOffers =
        StoreRewardedOffers(
            state = rewardedState,
            onWatch = { kind -> activity?.let { onWatchRewardedAd(it, kind) } },
            onRetry = onRetryRewardedAd,
        )
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
            onLastPlayed(puzzleType, difficulty)
            navigationScope.launch {
                val levelId =
                    runCatchingCancellable { catalogLevelRepository.currentLevelId(puzzleType, difficulty) }
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

    /** A cleared level again, from the level map or the gallery; it needs a life like any start. */
    val replayLevel: (PuzzleType, Difficulty, Int) -> Unit = { puzzleType, difficulty, level ->
        if (economy.isGameplayAllowed) {
            val levelId = CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(level))
            backStack.add(puzzleType.gameDestination(GameAttemptLaunch.Level(levelId, replay = true)))
        } else {
            onRestoreLife()
        }
    }

    /** Re-read progression for Next as well, so every Catalog launch has authoritative identity. */
    val openNextLevel: (PuzzleType, GameAttemptLaunch) -> Unit = { puzzleType, launch ->
        val level = launch as? GameAttemptLaunch.Level
        if (level == null) {
            returnToGameHub(backStack) { selectedTab = it }
        } else {
            val sourceDestination = backStack.lastOrNull()
            if (sourceDestination == puzzleType.gameDestination(launch)) {
                navigationScope.launch {
                    val levelResult =
                        runCatchingCancellable { catalogLevelRepository.currentLevelId(puzzleType, level.levelId.difficulty) }
                    // The database lookup suspends. Only replace the exact route entry that launched it;
                    // otherwise a late response could overwrite a screen opened in the meantime.
                    if (backStack.lastOrNull() !== sourceDestination) return@launch
                    levelResult
                        .onSuccess { levelId ->
                            backStack[backStack.lastIndex] =
                                puzzleType.gameDestination(GameAttemptLaunch.Level(levelId))
                        }.onFailure { snackbarHostState.showSnackbar(levelUnavailableMessage) }
                }
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
            if (currentDestination.isGameplay()) {
                GameTopBar(
                    title = destinationTitle(currentDestination, selectedTab),
                    onHelp = { rulesFor = currentDestination.gameplayPuzzleType() },
                    economy = economy,
                    onBack = goBack,
                    onOpenSettings = { backStack.add(AppDestination.Settings) },
                    onOpenLives = { showLivesDialog = true },
                    onOpenStore = openStore,
                )
            } else {
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
            }
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            CompositionLocalProvider(
                LocalSecondChanceAd provides
                    SecondChanceAd(
                        state = rewardedState,
                        setVisible = { secondChanceVisible = it },
                        watch = { onGranted -> activity?.let { onWatchContinueAd(it, onGranted) } },
                        retry = onRetryRewardedAd,
                    ),
                LocalResultLives provides
                    ResultLives(
                        economy = economy,
                        ad = rewardedState,
                        watch = { activity?.let { onWatchRewardedAd(it, RewardedAdKind.LIFE) } },
                        retry = onRetryRewardedAd,
                    ),
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
                                // A tablet or a landscape window of 720 dp and more keeps the tabs in a rail on the
                                // left, like the Web host; the tabs, their back-stack entry and state are unchanged.
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
                                    LaunchedEffect(wide) { if (wide) primaryNavigationBarSize = IntSize.Zero }
                                    Row(Modifier.fillMaxSize()) {
                                        if (wide) AppNavigationRail(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(bottom = if (wide) 0.dp else primaryNavigationBarHeight),
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
                                                                    dailyRewardsRepository = dailyRewardsRepository,
                                                                    catalog = GAME_CATALOG_PUZZLE_TYPES,
                                                                    economy = economy,
                                                                    onGameSelected = onGameSelected,
                                                                    onOpenDaily = openDaily,
                                                                    onRestoreLife = onRestoreLife,
                                                                    continueGame =
                                                                        settings.lastPlayedPuzzle?.let { puzzle ->
                                                                            settings.lastPlayedDifficulty?.let { puzzle to it }
                                                                        },
                                                                    catalogLevelRepository = catalogLevelRepository,
                                                                    onContinue = { puzzle, difficulty ->
                                                                        if (economy.isGameplayAllowed) {
                                                                            openLevel(puzzle, difficulty)
                                                                        } else {
                                                                            showLivesDialog =
                                                                                true
                                                                        }
                                                                    },
                                                                )
                                                            PrimaryTab.STORE ->
                                                                ReadableWidth(wide, STORE_MAX_WIDTH) {
                                                                    StoreRoute(
                                                                        economy = economy,
                                                                        economyRepository = economyRepository,
                                                                        storeGateway = storeGateway,
                                                                        storeProducts = storeProducts,
                                                                        rewarded = storeRewardedOffers,
                                                                    )
                                                                }
                                                            PrimaryTab.PROFILE ->
                                                                ReadableWidth(wide, PROFILE_MAX_WIDTH) {
                                                                    ProfileRoute(
                                                                        statisticsRepository,
                                                                        dailyRewardsRepository,
                                                                        onOpenGames = { selectedTab = PrimaryTab.GAME },
                                                                        onOpenAchievements = { backStack.add(AppDestination.Achievements) },
                                                                        onOpenPage = { backStack.add(AppDestination.ProfileSection(it)) },
                                                                    )
                                                                }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (!wide) {
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
                            }
                            entry<AppDestination.Achievements> {
                                AchievementsRoute(statisticsRepository, dailyRewardsRepository)
                            }
                            entry<AppDestination.ProfileSection> { destination ->
                                ProfilePageRoute(destination.page, statisticsRepository)
                            }
                            entry<AppDestination.Settings> {
                                SettingsScreen(
                                    settings,
                                    onThemeModeChanged,
                                    onSoundEnabledChanged,
                                    onHapticsEnabledChanged,
                                    onOpenLicenses = { backStack.add(AppDestination.Licenses) },
                                )
                            }
                            entry<AppDestination.Licenses> {
                                LicensesContent(licenseNoticesFor(android = true), Modifier.fillMaxSize())
                            }
                            entry<AppDestination.BalanceStart> {
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.BALANCE,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.BALANCE),
                                    rating = levelRating(catalogLevelRepository, PuzzleType.BALANCE),
                                    levelMap = { onDismiss ->
                                        LevelMap(
                                            catalogLevelRepository,
                                            statisticsRepository,
                                            PuzzleType.BALANCE,
                                            onDismiss,
                                            openLevel,
                                            replayLevel,
                                        )
                                    },
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.BALANCE),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.BALANCE) },
                                    onOpenTutorial = { backStack.add(AppDestination.BalanceTutorial) },
                                    onStart = { difficulty -> openLevel(PuzzleType.BALANCE, difficulty) },
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                            entry<AppDestination.BalanceTutorial> {
                                BalanceTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                            }
                            entry<AppDestination.CrownsStart> {
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.CROWNS,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.CROWNS),
                                    rating = levelRating(catalogLevelRepository, PuzzleType.CROWNS),
                                    levelMap = { onDismiss ->
                                        LevelMap(
                                            catalogLevelRepository,
                                            statisticsRepository,
                                            PuzzleType.CROWNS,
                                            onDismiss,
                                            openLevel,
                                            replayLevel,
                                        )
                                    },
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.CROWNS),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.CROWNS) },
                                    onOpenTutorial = { backStack.add(AppDestination.CrownsTutorial) },
                                    onStart = { difficulty -> openLevel(PuzzleType.CROWNS, difficulty) },
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
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.WORD,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.WORD),
                                    rating = levelRating(catalogLevelRepository, PuzzleType.WORD),
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.WORD),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.WORD) },
                                    onOpenTutorial = { backStack.add(AppDestination.WordTutorial) },
                                    onStart = { difficulty -> openLevel(PuzzleType.WORD, difficulty) },
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                            entry<AppDestination.WordTutorial> {
                                WordTutorialRoute(settingsRepository = settingsRepository, onDone = { backStack.removeLastOrNull() })
                            }
                            entry<AppDestination.SudokuStart> {
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.SUDOKU,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.SUDOKU),
                                    rating = levelRating(catalogLevelRepository, PuzzleType.SUDOKU),
                                    levelMap = { onDismiss ->
                                        LevelMap(
                                            catalogLevelRepository,
                                            statisticsRepository,
                                            PuzzleType.SUDOKU,
                                            onDismiss,
                                            openLevel,
                                            replayLevel,
                                        )
                                    },
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.SUDOKU),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.SUDOKU) },
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
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.GAME_2048,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.GAME_2048),
                                    rating = bestScoreRating(game2048BestScore),
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.GAME_2048),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.GAME_2048) },
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
                            entry<AppDestination.NonogramStart> {
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.NONOGRAM,
                                    stars = difficultyStars(statisticsRepository, PuzzleType.NONOGRAM),
                                    rating = levelRating(catalogLevelRepository, PuzzleType.NONOGRAM),
                                    gallery = { onDismiss ->
                                        NonogramGallery(catalogLevelRepository, statisticsRepository, onDismiss) { difficulty, level ->
                                            onDismiss()
                                            replayLevel(PuzzleType.NONOGRAM, difficulty, level)
                                        }
                                    },
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.NONOGRAM),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.NONOGRAM) },
                                    onOpenTutorial = { backStack.add(AppDestination.NonogramTutorial) },
                                    onStart = { difficulty -> openLevel(PuzzleType.NONOGRAM, difficulty) },
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                            entry<AppDestination.NonogramTutorial> {
                                NonogramTutorialRoute(
                                    settingsRepository = settingsRepository,
                                    onDone = { backStack.removeLastOrNull() },
                                )
                            }
                            entry<AppDestination.BlockSudokuStart> {
                                PuzzleStartScreen(
                                    puzzleType = PuzzleType.BLOCK_SUDOKU,
                                    rating = levelRating(catalogLevelRepository, PuzzleType.BLOCK_SUDOKU),
                                    economy = economy,
                                    tutorialPending = !settings.tutorialCompleted(PuzzleType.BLOCK_SUDOKU),
                                    onTutorialOffered = { onTutorialSeen(PuzzleType.BLOCK_SUDOKU) },
                                    onOpenTutorial = { backStack.add(AppDestination.BlockSudokuTutorial) },
                                    onStart = { difficulty -> openLevel(PuzzleType.BLOCK_SUDOKU, difficulty) },
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                            entry<AppDestination.BlockSudokuTutorial> {
                                BlockSudokuTutorialRoute(
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
                                    onOpenStore = openStore,
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
                                    onOpenStore = openStore,
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
                                    onOpenStore = openStore,
                                )
                            }
                            entry<AppDestination.Game2048Game> { destination ->
                                Game2048Route(
                                    launch = destination.launch,
                                    attemptFactory = attemptFactory,
                                    completionRepository = gameCompletionRepository,
                                    economyRepository = economyRepository,
                                    bestScore = game2048BestScore,
                                    exitGuard = exitGuard,
                                    hapticsEnabled = settings.hapticsEnabled,
                                    onBack = goBack,
                                    onNextLevel = { openNextLevel(PuzzleType.GAME_2048, destination.launch) },
                                    onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                                    onTerminalAction = onTerminalAction,
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                            entry<AppDestination.NonogramGame> { destination ->
                                NonogramGameRoute(
                                    launch = destination.launch,
                                    attemptFactory = attemptFactory,
                                    completionRepository = gameCompletionRepository,
                                    economyRepository = economyRepository,
                                    exitGuard = exitGuard,
                                    hapticsEnabled = settings.hapticsEnabled,
                                    onBack = goBack,
                                    onNextLevel = { openNextLevel(PuzzleType.NONOGRAM, destination.launch) },
                                    onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                                    onTerminalAction = onTerminalAction,
                                    onRestoreLife = onRestoreLife,
                                    onOpenStore = openStore,
                                )
                            }
                            entry<AppDestination.BlockSudokuGame> { destination ->
                                BlockSudokuRoute(
                                    launch = destination.launch,
                                    attemptFactory = attemptFactory,
                                    completionRepository = gameCompletionRepository,
                                    economyRepository = economyRepository,
                                    exitGuard = exitGuard,
                                    hapticsEnabled = settings.hapticsEnabled,
                                    onBack = goBack,
                                    onNextLevel = { openNextLevel(PuzzleType.BLOCK_SUDOKU, destination.launch) },
                                    onGameHub = { returnToGameHub(backStack) { selectedTab = it } },
                                    onTerminalAction = onTerminalAction,
                                    onRestoreLife = onRestoreLife,
                                )
                            }
                        },
                )
            }
        }
    }

    if (showStoreSheet) {
        StoreSheet(
            economy = economy,
            economyRepository = economyRepository,
            storeGateway = storeGateway,
            storeProducts = storeProducts,
            onDismiss = { showStoreSheet = false },
            rewarded = storeRewardedOffers,
        )
    }

    if (showLivesDialog) {
        LivesDialog(
            economy = economy,
            rewardedState = rewardedState,
            onRestoreLife = onRestoreLife,
            onWatchRewardedAd = { activity?.let { onWatchRewardedAd(it, RewardedAdKind.LIFE) } },
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
        PuzzleType.NONOGRAM -> AppDestination.NonogramGame(launch)
        PuzzleType.BLOCK_SUDOKU -> AppDestination.BlockSudokuGame(launch)
        else -> error("$this is not a Catalog game.")
    }

private fun PuzzleType.startDestination(): AppDestination =
    when (this) {
        PuzzleType.BALANCE -> AppDestination.BalanceStart
        PuzzleType.CROWNS -> AppDestination.CrownsStart
        PuzzleType.WORD -> AppDestination.WordStart
        PuzzleType.SUDOKU -> AppDestination.SudokuStart
        PuzzleType.GAME_2048 -> AppDestination.Game2048Start
        PuzzleType.NONOGRAM -> AppDestination.NonogramStart
        PuzzleType.BLOCK_SUDOKU -> AppDestination.BlockSudokuStart
        else -> error("$this is not a Catalog game.")
    }

private val WIDE_LAYOUT_MIN_WIDTH = 720.dp

private val PROFILE_MAX_WIDTH = 720.dp

private val STORE_MAX_WIDTH = 640.dp

/** Used for the first measure only; [AppBottomBar] immediately supplies its actual inset. */
private val PRIMARY_NAVIGATION_BAR_FALLBACK_HEIGHT = 80.dp
