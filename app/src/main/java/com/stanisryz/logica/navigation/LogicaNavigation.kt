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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stanisryz.logica.AppLanguage
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.InterstitialOpportunity
import com.stanisryz.logica.ads.RewardedAdKind
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.ads.TerminalActionCoordinator
import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.isReplay
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
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.settings.UserSettings
import com.stanisryz.logica.settings.tutorialCompleted
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.store.GemPackProductMapping
import com.stanisryz.logica.ui.components.EconomyBar
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameRulesSheet
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LevelMapSheet
import com.stanisryz.logica.ui.components.LivesDialog
import com.stanisryz.logica.ui.components.LocalLevelReplay
import com.stanisryz.logica.ui.components.LocalSecondChanceAd
import com.stanisryz.logica.ui.components.PuzzleStartScreen
import com.stanisryz.logica.ui.components.SecondChanceAd
import com.stanisryz.logica.ui.nonogram.DailyGalleryPicture
import com.stanisryz.logica.ui.nonogram.NonogramGallerySheet
import com.stanisryz.logica.ui.rating.GameRating
import com.stanisryz.logica.ui.screens.AchievementsRoute
import com.stanisryz.logica.ui.screens.BalanceGameRoute
import com.stanisryz.logica.ui.screens.BalanceTutorialRoute
import com.stanisryz.logica.ui.screens.CrownsGameRoute
import com.stanisryz.logica.ui.screens.CrownsTutorialRoute
import com.stanisryz.logica.ui.screens.Game2048Route
import com.stanisryz.logica.ui.screens.Game2048TutorialRoute
import com.stanisryz.logica.ui.screens.GameHubRoute
import com.stanisryz.logica.ui.screens.NonogramGameRoute
import com.stanisryz.logica.ui.screens.NonogramTutorialRoute
import com.stanisryz.logica.ui.screens.ProfileRoute
import com.stanisryz.logica.ui.screens.SettingsScreen
import com.stanisryz.logica.ui.screens.StoreRewardedOffers
import com.stanisryz.logica.ui.screens.StoreRoute
import com.stanisryz.logica.ui.screens.StoreSheet
import com.stanisryz.logica.ui.screens.SudokuGameRoute
import com.stanisryz.logica.ui.screens.SudokuTutorialRoute
import com.stanisryz.logica.ui.screens.WordGameRoute
import com.stanisryz.logica.ui.screens.WordTutorialRoute
import com.stanisryz.logica.ui.theme.LogicaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
                        runCatching { catalogLevelRepository.currentLevelId(puzzleType, level.levelId.difficulty) }
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
                                                            StoreRoute(
                                                                economy = economy,
                                                                economyRepository = economyRepository,
                                                                storeGateway = storeGateway,
                                                                storeProducts = storeProducts,
                                                                rewarded = storeRewardedOffers,
                                                            )
                                                        PrimaryTab.PROFILE ->
                                                            ProfileRoute(
                                                                statisticsRepository,
                                                                dailyRewardsRepository,
                                                                onOpenGames = { selectedTab = PrimaryTab.GAME },
                                                                onOpenAchievements = { backStack.add(AppDestination.Achievements) },
                                                            )
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
                            entry<AppDestination.Achievements> {
                                AchievementsRoute(statisticsRepository, dailyRewardsRepository)
                            }
                            entry<AppDestination.Settings> {
                                SettingsScreen(settings, onThemeModeChanged, onSoundEnabledChanged, onHapticsEnabledChanged)
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
                            entry<AppDestination.BalanceGame> { destination ->
                                // A replayed level pays no gems; its result card says so.
                                CompositionLocalProvider(LocalLevelReplay provides destination.launch.isReplay) {
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
                            }
                            entry<AppDestination.CrownsGame> { destination ->
                                // A replayed level pays no gems; its result card says so.
                                CompositionLocalProvider(LocalLevelReplay provides destination.launch.isReplay) {
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
                                // A replayed level pays no gems; its result card says so.
                                CompositionLocalProvider(LocalLevelReplay provides destination.launch.isReplay) {
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
                                // A replayed level pays no gems; its result card says so.
                                CompositionLocalProvider(LocalLevelReplay provides destination.launch.isReplay) {
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        // The bar shares the screen background, so no seam runs under it.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private fun horizontalSlideTransition(
    incomingDirection: Int,
    outgoingDirection: Int,
) = slideInHorizontally(navigationSlideSpec()) { width ->
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
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        PrimaryTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    when (tab) {
                        PrimaryTab.GAME -> Icon(Icons.Rounded.SportsEsports, null)
                        PrimaryTab.STORE -> Icon(Icons.Rounded.Storefront, null)
                        PrimaryTab.PROFILE -> Icon(Icons.Rounded.Person, null)
                    }
                },
                label = { Text(stringResource(tab.titleResource)) },
            )
        }
    }
}

/** A lower-profile game HUD keeps navigation and the wallet reachable without a full app bar. */
@Composable
private fun GameTopBar(
    title: String,
    onHelp: () -> Unit,
    economy: PlayerEconomy,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLives: () -> Unit,
    onOpenStore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(GAME_TOP_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = GAME_TOP_BAR_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAME_TOP_BAR_CONTENT_GAP),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(GAME_TOP_BAR_HEIGHT)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onHelp, modifier = Modifier.size(HELP_BUTTON_SIZE)) {
            Icon(
                Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = stringResource(R.string.game_rules),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(HELP_ICON_SIZE),
            )
        }
        Spacer(Modifier.weight(1f))
        EconomyBar(
            economy = economy,
            onOpenLives = onOpenLives,
            onOpenGemStore = onOpenStore,
            compact = true,
        )
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(GAME_TOP_BAR_HEIGHT)) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

private val HELP_BUTTON_SIZE = 36.dp
private val HELP_ICON_SIZE = 20.dp
private val GAME_TOP_BAR_HEIGHT = 48.dp
private val GAME_TOP_BAR_HORIZONTAL_PADDING = 4.dp
private val GAME_TOP_BAR_CONTENT_GAP = 4.dp

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
            AppDestination.Achievements -> R.string.achievements
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
            AppDestination.NonogramStart, is AppDestination.NonogramGame -> R.string.nonogram
            AppDestination.NonogramTutorial -> R.string.nonogram_tutorial_title
        },
    )

/** Best stars per difficulty for one game's start screen, read from durable results. */
@Composable
private fun difficultyStars(
    statisticsRepository: StatisticsRepository,
    puzzleType: PuzzleType,
): Map<Difficulty, Long> {
    val flow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val snapshot by flow.collectAsStateWithLifecycle(initialValue = null)
    return remember(snapshot, puzzleType) {
        snapshot
            ?.statistics
            ?.levelStars
            ?.filter { it.puzzleType == puzzleType }
            ?.groupBy { it.difficulty }
            ?.mapValues { (_, levels) -> levels.sumOf { it.stars.toLong() } }
            .orEmpty()
    }
}

/** A level game's rating: Catalog levels cleared per difficulty, straight from progression. */
@Composable
private fun levelRating(
    catalogLevelRepository: CatalogLevelRepository,
    puzzleType: PuzzleType,
): GameRating {
    val flow = remember(catalogLevelRepository, puzzleType) { catalogLevelRepository.observeCurrentLevels(puzzleType) }
    val levels by flow.collectAsStateWithLifecycle(initialValue = emptyMap())
    return GameRating.Levels(levels.clearedLevels().mapValues { it.value.toLong() })
}

@Composable
private fun bestScoreRating(bestScore: Game2048BestScore): GameRating {
    val best by bestScore.best.collectAsStateWithLifecycle(initialValue = 0L)
    return GameRating.BestScore(best)
}

/** Levels are cleared in order, so everything below the current level is solved. */
private fun Map<Difficulty, CatalogLevelNumber>.clearedLevels(): Map<Difficulty, Int> = mapValues { (_, level) -> level.value - 1 }

/** The Nonogram gallery: every cleared level's picture, rebuilt from its frozen level on demand. */
@Composable
private fun NonogramGallery(
    catalogLevelRepository: CatalogLevelRepository,
    statisticsRepository: StatisticsRepository,
    onDismiss: () -> Unit,
    onReplay: (Difficulty, Int) -> Unit,
) {
    val levelsFlow = remember(catalogLevelRepository) { catalogLevelRepository.observeCurrentLevels(PuzzleType.NONOGRAM) }
    val levels by levelsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val statisticsFlow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val statistics by statisticsFlow.collectAsStateWithLifecycle(initialValue = null)
    val stars =
        remember(statistics) {
            statistics
                ?.statistics
                ?.levelStars
                ?.filter { it.puzzleType == PuzzleType.NONOGRAM }
                ?.associate { (it.difficulty to it.level) to it.stars }
                .orEmpty()
        }
    val generator = remember { NonogramGeneratorV1() }
    val dailyFlow = remember(statisticsRepository) { statisticsRepository.observeSolvedDailyPictures() }
    val dailySolved by dailyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyPictures =
        remember(dailySolved) {
            val pictures = NonogramGeneratorV2()
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(AppLanguage.locale)
            dailySolved.mapNotNull { (date, seed) ->
                runCatching {
                    DailyGalleryPicture(formatter.format(date), pictures.generate(PuzzleSeed(seed), Difficulty.MEDIUM))
                }.getOrNull()
            }
        }
    NonogramGallerySheet(
        clearedLevels = levels.clearedLevels(),
        loadPicture = { difficulty, level ->
            withContext(Dispatchers.Default) {
                runCatching {
                    val definition =
                        catalogLevelRepository.resolve(CatalogLevelId(PuzzleType.NONOGRAM, difficulty, CatalogLevelNumber(level)))
                    generator.generate(definition.seed, difficulty)
                }.getOrNull()
            }
        },
        starsOf = { difficulty, level -> stars[difficulty to level] ?: 0 },
        onDismiss = onDismiss,
        onReplay = onReplay,
        dailyPictures = dailyPictures,
    )
}

/** One game's level map over its progression and the best stars of each cleared level. */
@Composable
private fun LevelMap(
    catalogLevelRepository: CatalogLevelRepository,
    statisticsRepository: StatisticsRepository,
    puzzleType: PuzzleType,
    onDismiss: () -> Unit,
    onPlay: (PuzzleType, Difficulty) -> Unit,
    onReplay: (PuzzleType, Difficulty, Int) -> Unit,
) {
    val levelsFlow = remember(catalogLevelRepository, puzzleType) { catalogLevelRepository.observeCurrentLevels(puzzleType) }
    val levels by levelsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val statisticsFlow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val statistics by statisticsFlow.collectAsStateWithLifecycle(initialValue = null)
    val stars =
        remember(statistics, puzzleType) {
            statistics
                ?.statistics
                ?.levelStars
                ?.filter { it.puzzleType == puzzleType }
                ?.associate { (it.difficulty to it.level) to it.stars }
                .orEmpty()
        }
    LevelMapSheet(
        currentLevels = levels.mapValues { it.value.value },
        starsOf = { difficulty, level -> stars[difficulty to level] ?: 0 },
        onPlayCurrent = { difficulty ->
            onDismiss()
            onPlay(puzzleType, difficulty)
        },
        onReplay = { difficulty, level ->
            onDismiss()
            onReplay(puzzleType, difficulty, level)
        },
        onDismiss = onDismiss,
    )
}
