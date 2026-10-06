package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.profile_achievements
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_daily_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_games_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_rating_title
import com.stanisryz.logica.ui.components.ContinueGameCard
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameHubContent
import com.stanisryz.logica.ui.profile.Achievement
import com.stanisryz.logica.ui.profile.AchievementAnnouncementHost
import com.stanisryz.logica.ui.profile.AchievementAnnouncer
import com.stanisryz.logica.ui.profile.AchievementsScreenContent
import com.stanisryz.logica.ui.profile.LocalAchievementAnnouncer
import com.stanisryz.logica.ui.profile.ProfilePage
import com.stanisryz.logica.ui.profile.ProfilePageContent
import com.stanisryz.logica.ui.profile.ProfileUiState
import com.stanisryz.logica.ui.profile.unlockedAchievementIds
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_to_profile
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

@Composable
internal fun ReadyContent(
    mode: WebHostMode,
    lifecycleState: PlatformLifecycleState,
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    crownsController: WebCrownsController,
    wordController: WebWordController,
    sudokuController: WebSudokuController,
    game2048Controller: Web2048Controller,
    nonogramController: WebNonogramController,
    blockSudokuController: WebBlockSudokuController,
    playerSession: WebPlayerSessionController,
    dailyCoordinator: WebDailyGameplayCoordinator,
    storeProcessor: WebStoreProcessor,
    paymentsCoordinator: WebPaymentsCoordinator,
    rewardedAds: WebRewardedAds,
    leaderboard: WebLeaderboardController,
    interstitialController: WebInterstitialContinuationController,
    stickyBannerController: WebStickyBannerController,
    onRendered: () -> Unit,
) {
    var route by remember { mutableStateOf<WebRoute>(WebRoute.GameHub) }
    // The achievements list opens in place of the Profile and closes whenever the route changes.
    var achievementsOpen by remember { mutableStateOf(false) }
    // A Profile page (the Daily calendar, the games, the leaderboard) opened in place of the Profile.
    var profilePage by remember { mutableStateOf<ProfilePage?>(null) }
    LaunchedEffect(route) {
        achievementsOpen = false
        profilePage = null
    }

    // Desktop keyboard input goes to the puzzle only while it is actively played and uncovered.
    val keyboard = remember { WebKeyboard().also(WebKeyboard::install) }

    // «Как играть?» opens the shared onboarding over the difficulty screen it came from.
    var tutorialFor by remember { mutableStateOf<PuzzleType?>(null) }

    // From a game the Store opens as a sheet over the board, so the unsaved attempt stays put.
    var storeSheetOpen by remember { mutableStateOf(false) }
    val openStore: () -> Unit = {
        if (route in PRIMARY_ROUTES) route = WebRoute.Store else storeSheetOpen = true
    }
    val balanceState = balanceController.state
    val crownsState = crownsController.state
    val wordState = wordController.state
    val sudokuState = sudokuController.state
    val game2048State = game2048Controller.state
    val nonogramState = nonogramController.state
    val blockSudokuState = blockSudokuController.state
    val accountChangeRevision = playerSession.accountChangeRevision

    // Fullscreen ads are part of the EFFECTIVE lifecycle: WebHostLifecycle owns the suppression
    // flag, so lifecycleState already reflects ad-driven inactivity for GameplayAPI and audio
    // consumers; closing an ad recomputes from real browser visibility/focus/Yandex state.
    val hasActivePuzzle =
        routeHasActivePuzzle(route, balanceState, crownsState, wordState, sudokuState, game2048State, nonogramState, blockSudokuState)
    LaunchedEffect(route, hasActivePuzzle, storeSheetOpen, lifecycleState) {
        controller.setGameplayActive(
            hasActivePuzzle && !storeSheetOpen && lifecycleState == PlatformLifecycleState.ACTIVE,
        )
    }

    // Sticky-banner visibility is platform-side (rendered by Yandex, never drawn in Compose).
    // Applied only while the effective lifecycle is ACTIVE — which also suppresses transitions
    // during fullscreen ads and reapplies/reconciles the desired state after one closes.
    val bannerVisible = stickyBannerVisible(route, hasActivePuzzle)
    LaunchedEffect(bannerVisible, lifecycleState) {
        if (lifecycleState == PlatformLifecycleState.ACTIVE) {
            stickyBannerController.applyVisibility(bannerVisible)
            stickyBannerController.reconcileUsingPlatformStatus()
        }
    }

    // A passed midnight must re-render the new day's Daily definition, both while the page stays
    // open (one timer to the next local midnight, no polling) and on resume after the page slept.
    // Gameplay attempts keep their own captured challenge date, so this only affects presentation.
    var dailyDate by remember { mutableStateOf(BrowserLocalWebDailyDateProvider.currentDate()) }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState != PlatformLifecycleState.ACTIVE) return@LaunchedEffect
        while (true) {
            dailyDate = BrowserLocalWebDailyDateProvider.currentDate()
            delay(millisUntilNextLocalMidnight() + MIDNIGHT_ROLLOVER_SLACK_MS)
        }
    }

    LaunchedEffect(mode) {
        playerSession.start()
        withFrameNanos { }
        onRendered()
    }
    LaunchedEffect(accountChangeRevision) {
        if (accountChangeRevision > 0L) {
            balanceController.showDifficultySelector()
            crownsController.showDifficultySelector()
            wordController.showDifficultySelector()
            sudokuController.showDifficultySelector()
            game2048Controller.showDifficultySelector()
            nonogramController.showDifficultySelector()
            blockSudokuController.showDifficultySelector()
            storeSheetOpen = false
            tutorialFor = null
            route = WebRoute.GameHub
        }
    }

    // Catalog SOLVED -> Next Level is the first interstitial placement: the user always sees
    // the terminal success state first, and the continuation runs exactly once even when no ad
    // can be shown.
    val runSolvedNextLevel: (() -> Unit) -> Unit = { continuation ->
        interstitialController.runWithInterstitial(
            WebAdPlacements.CATALOG_NEXT_LEVEL_INTERSTITIAL,
            continuation,
        )
    }
    // Every other tapped way out of a game (leaving it, Retry, To difficulty, To games) shares
    // the same interstitial controller, cooldown, and exactly-once continuation.
    val runTransitionAd: (() -> Unit) -> Unit = { continuation ->
        interstitialController.runWithInterstitial(
            WebAdPlacements.GAMEPLAY_TRANSITION_INTERSTITIAL,
            continuation,
        )
    }

    // Lives regenerate while the app is active; a Catalog attempt needs at least one life to start.
    val economyBinding by playerSession.economyBinding.collectAsState()
    val economyRepository = (economyBinding as? WebEconomyBinding.Ready)?.repository
    val economyState =
        economyRepository?.let { repository -> key(repository) { repository.state.collectAsState().value } }
    WebLivesRegenerationEffect(
        repository = economyRepository,
        nextLifeRestoreAtEpochMs = economyState?.nextLifeRestoreAtEpochMs,
        active = lifecycleState == PlatformLifecycleState.ACTIVE,
    )
    // The bound Player's solved total feeds the Yandex leaderboard whenever it grows.
    val leaderboardBinding = playerSession.statisticsBinding.collectAsState().value as? WebStatisticsBinding.Ready
    if (leaderboardBinding != null) {
        key(leaderboardBinding.token) {
            val statisticsSnapshot by leaderboardBinding.repository.snapshot.collectAsState()
            val solvedTotal = WebStatisticsAggregator.aggregate(statisticsSnapshot).toProfileStatistics().totalSolved
            LaunchedEffect(solvedTotal) {
                leaderboard.submit(WebLeaderboardController.SOLVED_LEADERBOARD, leaderboardBinding.token, solvedTotal)
            }
        }
    }
    // Each game's rating feeds its own table whenever it grows.
    val ratingBinding = playerSession.progressBinding.collectAsState().value as? WebCatalogProgressBinding.Ready
    val ratingProgress =
        ratingBinding?.let { binding ->
            key(binding.token) {
                binding.repository.snapshot
                    .collectAsState()
                    .value
            }
        }
            ?: WebCatalogProgressSnapshot.EMPTY
    val ratingBest2048 =
        ratingBinding?.let { binding ->
            key(binding.token) {
                binding.repository.best2048
                    .collectAsState()
                    .value
            }
        } ?: 0L
    if (ratingBinding != null) {
        GAME_CATALOG_PUZZLE_TYPES.forEach { puzzleType ->
            val value = gameRating(puzzleType, ratingProgress, ratingBest2048).value
            LaunchedEffect(ratingBinding.token, puzzleType, value) {
                leaderboard.submit(WebLeaderboardController.ratingLeaderboard(puzzleType), ratingBinding.token, value)
            }
        }
    }
    val ratingUi =
        WebRatingUi(
            progress = ratingProgress,
            rating = { puzzleType -> gameRating(puzzleType, ratingProgress, ratingBest2048) },
            leaderboard = if (leaderboard.isSupported) ({ puzzleType -> WebRatingLeaderboard(leaderboard, puzzleType) }) else null,
        )
    var showNoLives by remember { mutableStateOf(false) }
    val livesUi =
        WebLivesUi(
            state = economyState,
            guard = { start ->
                economyRepository?.refresh()
                if (economyRepository != null && economyRepository.state.value.lives <= 0) showNoLives = true else start()
            },
            rewardedLife = rewardedAds.life,
        )
    if (showNoLives) {
        WebNoLivesDialog(
            state = economyState,
            rewardedLife = rewardedAds.life,
            onOpenStore = {
                showNoLives = false
                openStore()
            },
            onDismiss = { showNoLives = false },
        )
    }

    val storeBinding by playerSession.storeBinding.collectAsState()
    val hintCount =
        (storeBinding as? WebStoreBinding.Ready)?.repository?.let { repository ->
            key(repository) {
                repository.snapshot
                    .collectAsState()
                    .value
                    .quantityOf(STORE_INVENTORY_HINTS)
            }
        }

    // Read in composition (not inside the effect) so every change of these states re-applies it.
    val keyboardEnabled = (hasActivePuzzle || tutorialFor != null) && !storeSheetOpen && !showNoLives
    SideEffect { keyboard.enabled = keyboardEnabled }
    LaunchedEffect(keyboard, route) {
        keyboard.keys.collect { key ->
            when (route) {
                WebRoute.Sudoku -> sudokuController.onHardwareKey(key)
                // Word edits its draft and 2048 buffers its moves inside the shared presentation.
                else -> Unit
            }
        }
    }

    // The bound Player's best stars per level, for the difficulty cards.
    val progressRepository = (playerSession.progressBinding.collectAsState().value as? WebCatalogProgressBinding.Ready)?.repository
    val catalogStars =
        progressRepository?.let { repository -> key(repository) { repository.stars.collectAsState().value } }
            ?: WebCatalogStarsSnapshot.EMPTY
    // The bound Player's solved Daily Nonogram pictures, for the gallery's Daily chip.
    val dailyHistory = (playerSession.dailyBinding.collectAsState().value as? WebDailyBinding.Ready)?.repository
    val dailySnapshot = dailyHistory?.let { repository -> key(repository) { repository.snapshot.collectAsState().value } }
    val dailyPictures = remember(dailySnapshot) { dailySnapshot?.let(::solvedDailyNonogramPictures).orEmpty() }
    val achievementAnnouncer = remember { AchievementAnnouncer() }
    val abandonEconomy = remember(playerSession) { WebGameplayEconomyCoordinator(playerSession) }
    val abandonAttempt: () -> Unit = remember(abandonEconomy) { { abandonEconomy.recordAbandonedAttempt() } }
    CompositionLocalProvider(
        LocalAchievementAnnouncer provides achievementAnnouncer,
        LocalWebCatalogStars provides catalogStars,
        LocalWebDailyPictures provides dailyPictures,
        LocalWebRating provides ratingUi,
        LocalWebSecondChanceAd provides rewardedAds.secondChance,
        LocalWebLives provides livesUi,
        LocalWebOpenStore provides openStore,
        LocalWebKeyboard provides keyboard,
        LocalWebTransitionAd provides runTransitionAd,
        LocalWebAbandonAttempt provides abandonAttempt,
        LocalOpenTutorial provides { puzzleType -> tutorialFor = puzzleType },
    ) {
        tutorialFor?.let { puzzleType ->
            WideReadableColumn(WIDE_TUTORIAL_MAX_WIDTH) {
                WebTutorialScreen(puzzleType = puzzleType, onClose = { tutorialFor = null })
            }
            return@CompositionLocalProvider
        }
        when (route) {
            WebRoute.GameHub ->
                PrimaryDestinationShell(
                    selected = WebRoute.GameHub,
                    onSelect = { route = it },
                ) {
                    GameHubContent(
                        puzzleTypes = GAME_CATALOG_PUZZLE_TYPES,
                        catalogEnabled = true,
                        onGameSelected = { puzzleType ->
                            route =
                                when (puzzleType) {
                                    PuzzleType.BALANCE -> {
                                        balanceController.showDifficultySelector()
                                        WebRoute.Balance
                                    }
                                    PuzzleType.CROWNS -> {
                                        crownsController.showDifficultySelector()
                                        WebRoute.Crowns
                                    }
                                    PuzzleType.WORD -> {
                                        wordController.showDifficultySelector()
                                        WebRoute.Word
                                    }
                                    PuzzleType.SUDOKU -> {
                                        sudokuController.showDifficultySelector()
                                        WebRoute.Sudoku
                                    }
                                    PuzzleType.GAME_2048 -> {
                                        game2048Controller.showDifficultySelector()
                                        WebRoute.Game2048
                                    }
                                    PuzzleType.NONOGRAM -> {
                                        nonogramController.showDifficultySelector()
                                        WebRoute.Nonogram
                                    }
                                    PuzzleType.BLOCK_SUDOKU -> {
                                        blockSudokuController.showDifficultySelector()
                                        WebRoute.BlockSudoku
                                    }
                                    else -> error("$puzzleType has no Web game flow.")
                                }
                        },
                        rewardsContent = {
                            WebDailyRewardsRoute(
                                progressRepository = progressRepository,
                                economyRepository = economyRepository,
                                playerSession = playerSession,
                                currentDate = dailyDate,
                            )
                        },
                        continueContent =
                            WebLastPlayed.value?.let { (puzzleType, difficulty) ->
                                {
                                    val progress by playerSession.progressBinding.collectAsState()
                                    val ready = progress as? WebCatalogProgressBinding.Ready
                                    val snapshot =
                                        ready
                                            ?.repository
                                            ?.snapshot
                                            ?.collectAsState()
                                            ?.value
                                    val level = snapshot?.gameLevel(puzzleType, difficulty)?.value
                                    ContinueGameCard(
                                        puzzleType = puzzleType,
                                        difficultyLabel = stringResource(difficulty.hubLabelResource()),
                                        levelNumber = level,
                                        enabled = ready != null,
                                        noLives = livesUi.state?.let { it.lives <= 0 } == true,
                                        onContinue = {
                                            livesUi.guard {
                                                route =
                                                    when (puzzleType) {
                                                        PuzzleType.BALANCE ->
                                                            WebRoute.Balance.also {
                                                                balanceController.selectDifficulty(
                                                                    difficulty,
                                                                )
                                                            }
                                                        PuzzleType.CROWNS ->
                                                            WebRoute.Crowns.also {
                                                                crownsController.selectDifficulty(
                                                                    difficulty,
                                                                )
                                                            }
                                                        PuzzleType.WORD ->
                                                            WebRoute.Word.also {
                                                                wordController.selectDifficulty(
                                                                    difficulty,
                                                                )
                                                            }
                                                        PuzzleType.SUDOKU ->
                                                            WebRoute.Sudoku.also {
                                                                sudokuController.selectDifficulty(
                                                                    difficulty,
                                                                )
                                                            }
                                                        PuzzleType.GAME_2048 ->
                                                            WebRoute.Game2048.also { game2048Controller.selectDifficulty(difficulty) }
                                                        PuzzleType.NONOGRAM ->
                                                            WebRoute.Nonogram.also { nonogramController.selectDifficulty(difficulty) }
                                                        PuzzleType.BLOCK_SUDOKU ->
                                                            WebRoute.BlockSudoku.also { blockSudokuController.selectDifficulty(difficulty) }
                                                        else -> error("$puzzleType has no Web game flow.")
                                                    }
                                            }
                                        },
                                    )
                                }
                            },
                        headerContent = {
                            WebDailyHubRoute(
                                playerSession = playerSession,
                                coordinator = dailyCoordinator,
                                currentDate = dailyDate,
                                onStartDaily = { puzzleType ->
                                    // A Daily attempt costs a life when lost, so it needs one to start, like the Catalog.
                                    livesUi.guard {
                                        when (val started = dailyCoordinator.start(puzzleType)) {
                                            is WebDailyStartResult.Started -> {
                                                route =
                                                    when (puzzleType) {
                                                        PuzzleType.BALANCE -> {
                                                            balanceController.startDaily(started.attempt)
                                                            WebRoute.Balance
                                                        }
                                                        PuzzleType.CROWNS -> {
                                                            crownsController.startDaily(started.attempt)
                                                            WebRoute.Crowns
                                                        }
                                                        PuzzleType.WORD -> {
                                                            wordController.startDaily(started.attempt)
                                                            WebRoute.Word
                                                        }
                                                        PuzzleType.SUDOKU -> {
                                                            sudokuController.startDaily(started.attempt)
                                                            WebRoute.Sudoku
                                                        }
                                                        PuzzleType.GAME_2048 -> {
                                                            game2048Controller.startDaily(started.attempt)
                                                            WebRoute.Game2048
                                                        }
                                                        PuzzleType.NONOGRAM -> {
                                                            nonogramController.startDaily(started.attempt)
                                                            WebRoute.Nonogram
                                                        }
                                                        PuzzleType.BLOCK_SUDOKU -> {
                                                            blockSudokuController.startDaily(started.attempt)
                                                            WebRoute.BlockSudoku
                                                        }
                                                        else -> error("$puzzleType has no Daily gameplay.")
                                                    }
                                            }
                                            else -> Unit // surfaced as a start error by the shared hub section
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
            WebRoute.Profile ->
                if (achievementsOpen) {
                    Column(Modifier.fillMaxSize()) {
                        WebTopBar(
                            backLabel = stringResource(WebRes.string.web_to_profile),
                            onBack = { achievementsOpen = false },
                            title = stringResource(Res.string.profile_achievements),
                        )
                        WideReadableColumn(WIDE_PROFILE_MAX_WIDTH) {
                            val binding = playerSession.statisticsBinding.collectAsState().value
                            if (binding is WebStatisticsBinding.Ready) {
                                key(binding.token) {
                                    AchievementsScreenContent(
                                        webProfileStatistics(playerSession, binding, dailyDate),
                                        rewards = webAchievementRewards(progressRepository, economyRepository),
                                    )
                                }
                            }
                        }
                    }
                } else if (profilePage != null) {
                    val page = requireNotNull(profilePage)
                    Column(Modifier.fillMaxSize()) {
                        WebTopBar(
                            backLabel = stringResource(WebRes.string.web_to_profile),
                            onBack = { profilePage = null },
                            title =
                                stringResource(
                                    when (page) {
                                        ProfilePage.DAILY -> Res.string.profile_page_daily_title
                                        ProfilePage.GAMES -> Res.string.profile_page_games_title
                                        ProfilePage.RATING -> Res.string.profile_page_rating_title
                                    },
                                ),
                        )
                        WideReadableColumn(WIDE_PROFILE_MAX_WIDTH) {
                            if (page == ProfilePage.RATING) {
                                Box(Modifier.fillMaxSize().padding(LogicaSpacing.screenHorizontal)) { WebLeaderboardCard(leaderboard) }
                            } else {
                                val binding = playerSession.statisticsBinding.collectAsState().value
                                val uiState =
                                    when (binding) {
                                        is WebStatisticsBinding.Ready ->
                                            key(binding.token) { webProfileStatistics(playerSession, binding, dailyDate).toUiState() }
                                        is WebStatisticsBinding.Unavailable -> ProfileUiState.Error
                                        else -> ProfileUiState.Loading
                                    }
                                ProfilePageContent(page, uiState)
                            }
                        }
                    }
                } else {
                    PrimaryDestinationShell(
                        selected = WebRoute.Profile,
                        onSelect = { route = it },
                    ) {
                        WideReadableColumn(WIDE_PROFILE_MAX_WIDTH) {
                            WebProfileRoute(
                                playerSession = playerSession,
                                leaderboard = leaderboard,
                                binding = playerSession.statisticsBinding.collectAsState().value,
                                currentDate = dailyDate,
                                onRetry = playerSession::retryCurrentContext,
                                onOpenGames = { route = WebRoute.GameHub },
                                onOpenAchievements = { achievementsOpen = true },
                                onOpenPage = { profilePage = it },
                                achievementRewards = webAchievementRewards(progressRepository, economyRepository),
                            )
                        }
                    }
                }
            WebRoute.Store ->
                PrimaryDestinationShell(
                    selected = WebRoute.Store,
                    onSelect = { route = it },
                ) {
                    WideReadableColumn(WIDE_STORE_MAX_WIDTH) {
                        WebStoreScreen(
                            playerSession = playerSession,
                            storeProcessor = storeProcessor,
                            paymentsCoordinator = paymentsCoordinator,
                            rewardedAds = rewardedAds,
                        )
                    }
                }
            WebRoute.Balance ->
                BalanceFlow(
                    state = balanceState,
                    controller = balanceController,
                    hintCount = hintCount,
                    onOpenStore = openStore,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitBalance = {
                        balanceController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.Crowns ->
                CrownsFlow(
                    state = crownsState,
                    controller = crownsController,
                    hintCount = hintCount,
                    onOpenStore = openStore,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitCrowns = {
                        crownsController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.Word ->
                WordFlow(
                    state = wordState,
                    controller = wordController,
                    hardwareKeys = keyboard.keys,
                    hintCount = hintCount,
                    onOpenStore = openStore,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitWord = {
                        wordController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.Sudoku ->
                SudokuFlow(
                    state = sudokuState,
                    controller = sudokuController,
                    hintCount = hintCount,
                    onOpenStore = openStore,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitSudoku = {
                        sudokuController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.Game2048 ->
                Game2048Flow(
                    state = game2048State,
                    controller = game2048Controller,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitGame2048 = {
                        game2048Controller.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.Nonogram ->
                NonogramFlow(
                    state = nonogramState,
                    controller = nonogramController,
                    hintCount = hintCount,
                    onOpenStore = openStore,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExitNonogram = {
                        nonogramController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
            WebRoute.BlockSudoku ->
                BlockSudokuFlow(
                    state = blockSudokuState,
                    controller = blockSudokuController,
                    onSolvedNextLevel = runSolvedNextLevel,
                    onExit = {
                        blockSudokuController.showDifficultySelector()
                        route = WebRoute.GameHub
                    },
                )
        }
    }

    WebStoreSheet(
        visible = storeSheetOpen,
        onDismiss = { storeSheetOpen = false },
    ) {
        WebStoreScreen(
            playerSession = playerSession,
            storeProcessor = storeProcessor,
            paymentsCoordinator = paymentsCoordinator,
            rewardedAds = rewardedAds,
        )
    }
    // A newly reached achievement is announced once: inside an open result card, else as a banner.
    if (leaderboardBinding != null) {
        key(leaderboardBinding.token) {
            CompositionLocalProvider(LocalWebCatalogStars provides catalogStars) {
                val unlocked = webProfileStatistics(playerSession, leaderboardBinding, dailyDate).unlockedAchievementIds()
                LaunchedEffect(unlocked) {
                    WebAchievementsSeen
                        .newlyUnlocked(leaderboardBinding.repository.scope, unlocked)
                        .mapNotNull { id -> Achievement.entries.firstOrNull { it.id == id } }
                        .let(achievementAnnouncer::announce)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AchievementAnnouncementHost(achievementAnnouncer)
    }
}

private fun Difficulty.hubLabelResource(): StringResource =
    when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.EXPERT -> Res.string.difficulty_expert
    }

private val WIDE_PROFILE_MAX_WIDTH = 720.dp

private val WIDE_STORE_MAX_WIDTH = 640.dp

private val WIDE_TUTORIAL_MAX_WIDTH = 1100.dp
