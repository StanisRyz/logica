package com.stanisryz.logica.web

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuStatus
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_score
import com.stanisryz.logica.shared.ui.generated.resources.daily_marker
import com.stanisryz.logica.shared.ui.generated.resources.daily_start_error
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_easy
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_expert
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_hard
import com.stanisryz.logica.shared.ui.generated.resources.difficulty_medium
import com.stanisryz.logica.shared.ui.generated.resources.how_to_play
import com.stanisryz.logica.shared.ui.generated.resources.primary_games
import com.stanisryz.logica.shared.ui.generated.resources.primary_profile
import com.stanisryz.logica.shared.ui.generated.resources.primary_store
import com.stanisryz.logica.shared.ui.generated.resources.profile_achievements
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_daily_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_games_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_page_rating_title
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.blocksudoku.BlockSudokuContent
import com.stanisryz.logica.ui.components.ContinueGameCard
import com.stanisryz.logica.ui.components.DailyRewardsCard
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameHubContent
import com.stanisryz.logica.ui.components.GameKey
import com.stanisryz.logica.ui.components.LevelMapSheet
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.components.WordLanguageNotice
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.components.dailyRewardsUiState
import com.stanisryz.logica.ui.components.starsForWordAttempts
import com.stanisryz.logica.ui.crowns.CrownsGameContent
import com.stanisryz.logica.ui.daily.DailyHubResultRow
import com.stanisryz.logica.ui.daily.DailyHubSection
import com.stanisryz.logica.ui.daily.DailyHubUiState
import com.stanisryz.logica.ui.daily.DailyShareFormatter
import com.stanisryz.logica.ui.daily.DailyShareLanguage
import com.stanisryz.logica.ui.game2048.Game2048Content
import com.stanisryz.logica.ui.game2048.formatGame2048Number
import com.stanisryz.logica.ui.nonogram.NonogramGallerySheet
import com.stanisryz.logica.ui.nonogram.NonogramGameContent
import com.stanisryz.logica.ui.profile.Achievement
import com.stanisryz.logica.ui.profile.AchievementAnnouncementHost
import com.stanisryz.logica.ui.profile.AchievementAnnouncer
import com.stanisryz.logica.ui.profile.AchievementRewards
import com.stanisryz.logica.ui.profile.AchievementsScreenContent
import com.stanisryz.logica.ui.profile.DailyProfileMetrics
import com.stanisryz.logica.ui.profile.LocalAchievementAnnouncer
import com.stanisryz.logica.ui.profile.ProfileContent
import com.stanisryz.logica.ui.profile.ProfileEconomyMetrics
import com.stanisryz.logica.ui.profile.ProfilePage
import com.stanisryz.logica.ui.profile.ProfilePageContent
import com.stanisryz.logica.ui.profile.ProfileStarSummary
import com.stanisryz.logica.ui.profile.ProfileStatistics
import com.stanisryz.logica.ui.profile.ProfileUiState
import com.stanisryz.logica.ui.profile.unlockedAchievementIds
import com.stanisryz.logica.ui.rating.DifficultyScreenActions
import com.stanisryz.logica.ui.rating.GameRatingSheet
import com.stanisryz.logica.ui.sudoku.SudokuGameContent
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.ui.tutorial.FirstPlayTutorialDialog
import com.stanisryz.logica.ui.word.WordGameContent
import com.stanisryz.logica.web.generated.resources.web_fatal_title
import com.stanisryz.logica.web.generated.resources.web_loading
import com.stanisryz.logica.web.generated.resources.web_score_final
import com.stanisryz.logica.web.generated.resources.web_settings
import com.stanisryz.logica.web.generated.resources.web_to_games
import com.stanisryz.logica.web.generated.resources.web_to_profile
import com.stanisryz.logica.web.generated.resources.web_word_answer
import com.stanisryz.logica.web.generated.resources.web_word_guessed
import com.stanisryz.logica.web.generated.resources.web_word_level_solved
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

private val PRIMARY_ROUTES = setOf<WebRoute>(WebRoute.GameHub, WebRoute.Profile, WebRoute.Store)

private sealed interface WebRoute {
    data object GameHub : WebRoute

    data object Profile : WebRoute

    data object Store : WebRoute

    data object Balance : WebRoute

    data object Crowns : WebRoute

    data object Word : WebRoute

    data object Sudoku : WebRoute

    data object Game2048 : WebRoute

    data object Nonogram : WebRoute

    data object BlockSudoku : WebRoute
}

private fun routeHasActivePuzzle(
    route: WebRoute,
    balanceState: WebBalanceState,
    crownsState: WebCrownsState,
    wordState: WebWordState,
    sudokuState: WebSudokuState,
    game2048State: Web2048State,
    nonogramState: WebNonogramState,
    blockSudokuState: WebBlockSudokuState,
): Boolean =
    when (route) {
        WebRoute.GameHub, WebRoute.Profile, WebRoute.Store -> false
        WebRoute.Balance ->
            balanceState is WebBalanceState.Playing &&
                balanceState.game.status == BalanceGameStatus.IN_PROGRESS
        WebRoute.Crowns ->
            crownsState is WebCrownsState.Playing &&
                crownsState.game.status == CrownsGameStatus.IN_PROGRESS
        WebRoute.Word ->
            wordState is WebWordState.Playing &&
                wordState.game.status == WordGameStatus.IN_PROGRESS
        WebRoute.Sudoku ->
            sudokuState is WebSudokuState.Playing &&
                sudokuState.game.status == SudokuGameStatus.IN_PROGRESS
        WebRoute.Game2048 ->
            game2048State is Web2048State.Playing &&
                game2048State.game.status == Game2048Status.IN_PROGRESS
        WebRoute.Nonogram ->
            nonogramState is WebNonogramState.Playing &&
                nonogramState.game.status == NonogramGameStatus.IN_PROGRESS
        WebRoute.BlockSudoku ->
            blockSudokuState is WebBlockSudokuState.Playing &&
                blockSudokuState.game.status == BlockSudokuStatus.IN_PROGRESS
    }

/**
 * Initial sticky-banner policy: hub/profile/store show the Yandex-rendered banner; a game route
 * hides it while a puzzle is actively being played. Isolated here so real Yandex layout testing
 * can tune it without touching navigation or ad plumbing.
 */
private fun stickyBannerVisible(
    route: WebRoute,
    hasActivePuzzle: Boolean,
): Boolean =
    when (route) {
        WebRoute.GameHub, WebRoute.Profile, WebRoute.Store -> true
        else -> !hasActivePuzzle
    }

@Composable
internal fun WebApp(
    controller: WebBootstrapController,
    balanceController: WebBalanceController,
    crownsController: WebCrownsController,
    wordController: WebWordController,
    sudokuController: WebSudokuController,
    game2048Controller: Web2048Controller,
    nonogramController: WebNonogramController,
    blockSudokuController: WebBlockSudokuController,
    lifecycle: WebHostLifecycle,
    playerSession: WebPlayerSessionController,
    dailyCoordinator: WebDailyGameplayCoordinator,
    storeProcessor: WebStoreProcessor,
    paymentsCoordinator: WebPaymentsCoordinator,
    rewardedAds: WebRewardedAds,
    leaderboard: WebLeaderboardController,
    interstitialController: WebInterstitialContinuationController,
    stickyBannerController: WebStickyBannerController,
) {
    val lifecycleState by lifecycle.state.collectAsState()
    val soundPlayer = remember(lifecycle) { WebGameSoundPlayer(lifecycle.state) }
    LaunchedEffect(soundPlayer, lifecycleState) { soundPlayer.onLifecycle(lifecycleState) }

    // Like Android's default, the Web follows the device's light/dark preference unless the player
    // picked a theme in the Web settings.
    val darkTheme =
        when (WebSettings.themeMode) {
            WebThemeMode.SYSTEM -> isSystemInDarkTheme()
            WebThemeMode.LIGHT -> false
            WebThemeMode.DARK -> true
        }
    LogicaTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalGameSounds provides soundPlayer) {
            LaunchedEffect(controller) {
                withFrameNanos { }
                controller.onComposeRootRendered()
            }
            DisposableEffect(
                controller,
                balanceController,
                crownsController,
                wordController,
                sudokuController,
                game2048Controller,
                nonogramController,
                blockSudokuController,
                playerSession,
            ) {
                onDispose {
                    controller.setGameplayActive(false)
                    balanceController.dispose()
                    crownsController.dispose()
                    wordController.dispose()
                    sudokuController.dispose()
                    game2048Controller.dispose()
                    nonogramController.dispose()
                    blockSudokuController.dispose()
                    playerSession.dispose()
                }
            }

            PortraitHostSurface {
                when (val state = controller.state) {
                    WebBootstrapState.Loading -> LoadingContent()
                    is WebBootstrapState.Ready ->
                        ReadyContent(
                            mode = state.mode,
                            lifecycleState = lifecycleState,
                            controller = controller,
                            balanceController = balanceController,
                            crownsController = crownsController,
                            wordController = wordController,
                            sudokuController = sudokuController,
                            game2048Controller = game2048Controller,
                            nonogramController = nonogramController,
                            blockSudokuController = blockSudokuController,
                            playerSession = playerSession,
                            dailyCoordinator = dailyCoordinator,
                            storeProcessor = storeProcessor,
                            rewardedAds = rewardedAds,
                            leaderboard = leaderboard,
                            interstitialController = interstitialController,
                            stickyBannerController = stickyBannerController,
                            paymentsCoordinator = paymentsCoordinator,
                            onRendered = controller::onInitialHostUiReady,
                        )
                    is WebBootstrapState.FatalError -> FatalContent(state.message)
                }
            }
        }
    }
}

@Composable
private fun PortraitHostSurface(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // A landscape window (a desktop browser, Yandex Games on a computer) gets the whole width;
        // a portrait or narrow one gets a phone column. The column fills a phone's whole screen,
        // also when a sticky banner takes part of its height, and narrows only in a window too
        // wide for a phone layout, so text never wraps because the window became shorter.
        val wide = maxWidth >= WIDE_HOST_MIN_WIDTH && maxWidth > maxHeight * WIDE_HOST_ASPECT
        if (wide) {
            Surface(
                modifier = Modifier.widthIn(max = WIDE_HOST_MAX_WIDTH).fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                CompositionLocalProvider(LocalWebWideLayout provides true) { content() }
            }
            return@BoxWithConstraints
        }
        val columnWidth = minOf(maxWidth, maxHeight * PORTRAIT_COLUMN_MAX_ASPECT)
        Surface(
            modifier = Modifier.width(columnWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (columnWidth < maxWidth) 8.dp else 0.dp,
        ) {
            content()
        }
    }
}

/** True when the Web host fills a landscape window instead of the 9:16 column. */
internal val LocalWebWideLayout = staticCompositionLocalOf { false }

/** Centres a tab's content at a readable width in the wide host; the phone column is untouched. */
@Composable
internal fun WideReadableColumn(
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    if (!LocalWebWideLayout.current) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}

@Composable
private fun LoadingContent() {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(WebRes.string.web_loading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReadyContent(
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
        WebLivesUi(economyState) { start ->
            economyRepository?.refresh()
            if (economyRepository != null && economyRepository.state.value.lives <= 0) showNoLives = true else start()
        }
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
                WebRoute.Game2048 -> key.game2048Direction()?.let(game2048Controller::move)
                WebRoute.Sudoku -> sudokuController.onHardwareKey(key)
                else -> Unit // Word edits its draft inside the shared presentation.
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
                                    val level =
                                        snapshot
                                            ?.currentLevel(
                                                WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPackVersion.V1),
                                            )?.value
                                    ContinueGameCard(
                                        puzzleType = puzzleType,
                                        difficultyLabel = stringResource(difficulty.hubLabelResource()),
                                        levelNumber = level,
                                        enabled = ready != null,
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

@Composable
private fun PrimaryDestinationShell(
    selected: WebRoute,
    onSelect: (WebRoute) -> Unit,
    content: @Composable () -> Unit,
) {
    val wide = LocalWebWideLayout.current
    val destinations =
        listOf(
            Triple(WebRoute.GameHub, Icons.Outlined.SportsEsports, Res.string.primary_games),
            Triple(WebRoute.Profile, Icons.Outlined.PersonOutline, Res.string.primary_profile),
            Triple(WebRoute.Store, Icons.Outlined.ShoppingCart, Res.string.primary_store),
        )
    Row(Modifier.fillMaxSize()) {
        // A landscape window keeps the tabs in a rail on the left, so the content gets the height.
        if (wide) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(LogicaSpacing.section))
                destinations.forEach { (route, icon, label) ->
                    NavigationRailItem(
                        selected = selected == route,
                        onClick = { onSelect(route) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Every tab opens with its name and the wallet, the Store included.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(GAME_HEADER_HEIGHT)
                        .padding(start = LogicaSpacing.screenHorizontal, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(destinations.first { it.first == selected }.third),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                WebGameplayWallet()
                var settingsOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(WebRes.string.web_settings))
                }
                if (settingsOpen) WebSettingsDialog(onDismiss = { settingsOpen = false })
            }
            Box(Modifier.weight(1f)) { content() }
            if (!wide) {
                NavigationBar(modifier = Modifier.fillMaxWidth().height(PRIMARY_NAVIGATION_HEIGHT)) {
                    destinations.forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = selected == route,
                            onClick = { onSelect(route) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The day's login gift and quests for the bound Player. Quest counters and claims live beside
 * Catalog progress; Daily solves come from the durable Daily history. A claim is made durable
 * first and pays its gems only after that, so a repeated tap never pays twice.
 */
@Composable
private fun WebDailyRewardsRoute(
    progressRepository: WebCatalogProgressRepository?,
    economyRepository: WebPlayerEconomyRepository?,
    playerSession: WebPlayerSessionController,
    currentDate: DailyDate,
) {
    val repository = progressRepository ?: return
    val economy = economyRepository ?: return
    val rewards by key(repository) { repository.rewards.collectAsState() }
    val dailyBinding by playerSession.dailyBinding.collectAsState()
    val dailySnapshot =
        (dailyBinding as? WebDailyBinding.Ready)?.let { ready ->
            key(ready.token) {
                ready.repository.snapshot
                    .collectAsState()
                    .value
            }
        }
    val today = currentDate.toDailyEpochDay()
    val dailySolved = dailySnapshot?.days?.get(currentDate)?.completedEntryCount ?: 0
    val state =
        dailyRewardsUiState(
            epochDay = today,
            activity = rewards.activity(today, dailySolved),
            claimedQuests = rewards.claimedQuests(today),
            lastGiftEpochDay = rewards.lastGiftDayOrNull,
            lastGiftStreakDay = rewards.giftStreakDay,
        )
    DailyRewardsCard(
        state = state,
        onClaimGift = { repository.claimLoginGift(today)?.let(economy::grantGems) },
        onClaimQuest = { index ->
            val quest = state.quests.firstOrNull { it.quest.index == index }
            if (quest != null && quest.complete && repository.claimQuest(today, index)) economy.grantGems(quest.quest.gems)
        },
    )
}

/**
 * Reactive Web Daily Hub presentation over the current Player-scoped binding. It performs no
 * cloud read and mutates nothing: the durable run is created only when gameplay actually starts.
 */
@Composable
private fun WebDailyHubRoute(
    playerSession: WebPlayerSessionController,
    coordinator: WebDailyGameplayCoordinator,
    currentDate: DailyDate,
    onStartDaily: (PuzzleType) -> Unit,
) {
    val binding by playerSession.dailyBinding.collectAsState()
    when (val current = binding) {
        WebDailyBinding.Loading ->
            DailyHubSection(
                uiState = DailyHubUiState.Loading,
                gameplayAllowed = true,
                onStart = {},
            )
        is WebDailyBinding.Unavailable ->
            DailyHubSection(
                uiState = DailyHubUiState.Error(),
                gameplayAllowed = true,
                onStart = {},
                onRetryLoad = playerSession::retryCurrentContext,
            )
        is WebDailyBinding.Ready ->
            key(current.token) {
                val snapshot by current.repository.snapshot.collectAsState()
                val hubState =
                    if (coordinator.lastStartWasRejected) {
                        DailyHubUiState.Error(stringResource(Res.string.daily_start_error))
                    } else {
                        buildWebDailyHubUiState(snapshot, currentDate)
                    }
                // The Web share action exists only for a fully completed, still-current Daily day;
                // it is user-initiated from the shared completion card's optional callback.
                val uiStateWithShare =
                    if (hubState is DailyHubUiState.Content) {
                        val sharePayload =
                            webDailySharePayloadOrNull(
                                snapshot.days[currentDate],
                                currentDate,
                                hubState.streak.current,
                            )
                        val completionWithShare =
                            hubState.completion?.let { completion ->
                                val record = snapshot.days.getValue(currentDate)
                                if (sharePayload == null) {
                                    completion
                                } else {
                                    val definition =
                                        DailyChallengePolicyResolver.definitionFor(record.date, record.policyVersion)
                                    completion.copy(
                                        resultRows =
                                            definition.entries.map { entry ->
                                                DailyHubResultRow(
                                                    puzzleType = entry.puzzleType,
                                                    solved = true,
                                                    wordAttemptsUsed =
                                                        record.wordSolvedAttemptsUsed.takeIf { entry.puzzleType == PuzzleType.WORD },
                                                )
                                            },
                                        onShare = {
                                            WebDailyTextSharer.share(
                                                DailyShareFormatter.format(
                                                    sharePayload,
                                                    DailyShareLanguage.fromTag(currentWebAppLanguage.tag),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        if (completionWithShare != null) hubState.copy(completion = completionWithShare) else hubState
                    } else {
                        hubState
                    }
                DailyHubSection(
                    uiState = uiStateWithShare,
                    gameplayAllowed = true,
                    onStart = onStartDaily,
                    onRetryLoad = coordinator::clearStartRejection,
                )
            }
    }
}

@Composable
private fun WebProfileRoute(
    playerSession: WebPlayerSessionController,
    leaderboard: WebLeaderboardController,
    binding: WebStatisticsBinding,
    currentDate: DailyDate,
    onRetry: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
    achievementRewards: AchievementRewards?,
) {
    when (binding) {
        WebStatisticsBinding.Loading ->
            ProfileContent(
                uiState = ProfileUiState.Loading,
                onRetry = onRetry,
            )
        is WebStatisticsBinding.Unavailable ->
            ProfileContent(
                uiState = ProfileUiState.Error,
                onRetry = onRetry,
            )
        is WebStatisticsBinding.Ready ->
            key(binding.token) {
                ProfileContent(
                    uiState = webProfileStatistics(playerSession, binding, currentDate).toUiState(),
                    onRetry = onRetry,
                    onOpenGames = onOpenGames,
                    onOpenPage = onOpenPage,
                    hasRatingPage = leaderboard.isSupported,
                    onOpenAchievements = onOpenAchievements,
                    achievementRewards = achievementRewards,
                )
            }
    }
}

/**
 * Achievement rewards for the bound Player: the paid ids live in the Player-scoped rewards record
 * beside Catalog progress, and a claim is made durable there before its gems are paid.
 */
@Composable
private fun webAchievementRewards(
    progressRepository: WebCatalogProgressRepository?,
    economyRepository: WebPlayerEconomyRepository?,
): AchievementRewards? {
    val repository = progressRepository ?: return null
    val economy = economyRepository ?: return null
    val rewards by key(repository) { repository.rewards.collectAsState() }
    return remember(repository, economy, rewards.claimedAchievements) {
        AchievementRewards(rewards.claimedAchievements) { achievement ->
            if (repository.claimAchievement(achievement.id)) economy.grantGems(achievement.gems)
        }
    }
}

/** Everything the Profile shows for the bound Player, derived locally without any cloud read. */
@Composable
private fun webProfileStatistics(
    playerSession: WebPlayerSessionController,
    binding: WebStatisticsBinding.Ready,
    currentDate: DailyDate,
): ProfileStatistics {
    val snapshot by binding.repository.snapshot.collectAsState()
    // Real Daily metrics come from the currently bound Player's Daily repository and update
    // locally after gameplay; opening Profile never triggers a cloud read.
    val dailyMetrics =
        webDailyProfileMetricsOrNull(
            dailyBinding = playerSession.dailyBinding.collectAsState().value,
            statisticsToken = binding.token,
            currentDate = currentDate,
        )
    val economyMetrics =
        webEconomyMetricsOrNull(
            economyBinding = playerSession.economyBinding.collectAsState().value,
            statisticsToken = binding.token,
        )
    return WebStatisticsAggregator
        .aggregate(snapshot)
        .toProfileStatistics()
        .copy(
            dailyMetrics = dailyMetrics,
            economy = economyMetrics,
            stars = ProfileStarSummary.from(LocalWebCatalogStars.current.levelRecords()),
        )
}

/** Daily metrics from the Daily repository bound to exactly this Player context, else absent. */
@Composable
private fun webDailyProfileMetricsOrNull(
    dailyBinding: WebDailyBinding,
    statisticsToken: WebPlayerContextToken,
    currentDate: DailyDate,
): DailyProfileMetrics? =
    when {
        dailyBinding is WebDailyBinding.Ready && dailyBinding.token == statisticsToken -> {
            val snapshot by dailyBinding.repository.snapshot.collectAsState()
            snapshot.dailyProfileMetrics(currentDate)
        }
        else -> null
    }

/** Wallet display from the economy repository bound to exactly this Player context, else absent. */
@Composable
private fun webEconomyMetricsOrNull(
    economyBinding: WebEconomyBinding,
    statisticsToken: WebPlayerContextToken,
): ProfileEconomyMetrics? =
    when {
        economyBinding is WebEconomyBinding.Ready && economyBinding.token == statisticsToken -> {
            val state by economyBinding.repository.state.collectAsState()
            state.let {
                ProfileEconomyMetrics(
                    gems = it.gems.toLong(),
                    lives = it.lives.toLong(),
                    maximumLives = EconomyPolicy.MAXIMUM_LIVES.toLong(),
                )
            }
        }
        else -> null
    }

@Composable
private fun NonogramFlow(
    state: WebNonogramState,
    controller: WebNonogramController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitNonogram: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebNonogramState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.NONOGRAM,
                onBack = onExitNonogram,
                onStart = controller::selectDifficulty,
                gallery = { onDismiss ->
                    val stars = LocalWebCatalogStars.current
                    val lives = LocalWebLives.current
                    NonogramGallerySheet(
                        clearedLevels = LocalWebRating.current.progress.clearedLevels(PuzzleType.NONOGRAM),
                        loadPicture = controller::galleryPicture,
                        starsOf = { difficulty, level ->
                            stars.starsOf(WebCatalogProgressBucket(PuzzleType.NONOGRAM, difficulty, CatalogLevelPackVersion.V1), level)
                        },
                        onDismiss = onDismiss,
                        onReplay = { difficulty, level ->
                            onDismiss()
                            lives.guard { controller.replayLevel(difficulty, level) }
                        },
                        dailyPictures = LocalWebDailyPictures.current,
                    )
                },
            )
        is WebNonogramState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack = if (state.launch.isDaily) onExitNonogram else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebNonogramState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = if (state.launch.isDaily) onExitNonogram else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebNonogramState.Playing -> {
            val livesGuard = LocalWebLives.current.guard
            val transitionAd = LocalWebTransitionAd.current
            Column(Modifier.fillMaxSize()) {
                WebGameplayHeader(
                    puzzleType = PuzzleType.NONOGRAM,
                    isDaily = state.source.isDaily,
                    hasMeaningfulProgress = state.hasMeaningfulProgress || controller.secondChanceOffered,
                    onExit = if (state.source.isDaily) onExitNonogram else controller::showDifficultySelector,
                )
                NonogramGameContent(
                    puzzle = state.puzzle,
                    game = state.game,
                    difficulty = state.source.difficulty,
                    levelNumber = state.source.catalogLevelNumberOrNull,
                    contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
                    selectedTool = state.selectedTool,
                    gameplayEnabled = state.game.status == NonogramGameStatus.IN_PROGRESS,
                    onCell = controller::onCell,
                    onSelectTool = controller::selectTool,
                    onHint = controller::requestHint,
                    modifier = Modifier.weight(1f),
                    hintCount = hintCount,
                )
            }
            // The third mistake waits here for the one ad-paid second chance.
            if (controller.secondChanceOffered) {
                WebSecondChanceDialog(onContinue = controller::continueAfterAd, onDecline = controller::declineSecondChance)
            }
            if (controller.hintsExhaustedNotice) {
                WebHintsExhaustedDialog(
                    onOpenStore = {
                        controller.dismissHintsExhaustedNotice()
                        onOpenStore()
                    },
                    onDismiss = controller::dismissHintsExhaustedNotice,
                )
            }
            if (state.source.isDaily) {
                WebDailyOrdinaryTerminalDialog(
                    puzzleType = PuzzleType.NONOGRAM,
                    visible = state.game.status.isTerminal && !controller.secondChanceOffered,
                    difficulty = state.source.difficulty,
                    mistakesUsed = state.game.mistakesUsed,
                    hintsUsed = state.game.hintsUsed,
                    solved = state.game.status == NonogramGameStatus.SOLVED,
                    completion = controller.dailyCompletionState,
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retryDailySave,
                    onExit = { transitionAd(onExitNonogram) },
                )
            } else {
                WebCatalogSaveErrorBanner(
                    completion = controller.completionState,
                    onRetrySave = controller::retrySave,
                )
                WebOrdinaryCatalogTerminalDialog(
                    puzzleType = PuzzleType.NONOGRAM,
                    visible = state.game.status.isTerminal && !controller.secondChanceOffered,
                    difficulty = state.source.difficulty,
                    mistakesUsed = state.game.mistakesUsed,
                    hintsUsed = state.game.hintsUsed,
                    levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
                    replay = (state.source as? WebGameplaySource.CatalogLevel)?.attempt?.replay == true,
                    solved = state.game.status == NonogramGameStatus.SOLVED,
                    completion = controller.completionState,
                    onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retrySave,
                    onBack = { transitionAd(controller::showDifficultySelector) },
                )
            }
        }
    }
}

@Composable
private fun BlockSudokuFlow(
    state: WebBlockSudokuState,
    controller: WebBlockSudokuController,
    onSolvedNextLevel: (() -> Unit) -> Unit,
    onExit: () -> Unit,
) {
    when (state) {
        WebBlockSudokuState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.BLOCK_SUDOKU,
                onBack = onExit,
                onStart = controller::selectDifficulty,
            )
        is WebBlockSudokuState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack = if (state.launch.isDaily) onExit else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebBlockSudokuState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = if (state.launch.isDaily) onExit else controller::showDifficultySelector,
                isDaily = state.launch.isDaily,
            )
        is WebBlockSudokuState.Playing -> {
            val livesGuard = LocalWebLives.current.guard
            val transitionAd = LocalWebTransitionAd.current
            Column(Modifier.fillMaxSize()) {
                WebGameplayHeader(
                    puzzleType = PuzzleType.BLOCK_SUDOKU,
                    isDaily = state.source.isDaily,
                    hasMeaningfulProgress = state.hasMeaningfulProgress,
                    onExit = if (state.source.isDaily) onExit else controller::showDifficultySelector,
                )
                BlockSudokuContent(
                    state = state.game,
                    difficulty = state.source.difficulty,
                    levelNumber = state.source.catalogLevelNumberOrNull,
                    contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
                    gameplayEnabled = state.game.status == BlockSudokuStatus.IN_PROGRESS,
                    onPlace = controller::place,
                    modifier = Modifier.weight(1f),
                )
            }
            val scoreDetail = "${stringResource(Res.string.block_sudoku_score)}: ${state.game.score}"
            if (state.source.isDaily) {
                WebDailyOrdinaryTerminalDialog(
                    puzzleType = PuzzleType.BLOCK_SUDOKU,
                    visible = state.game.status.isTerminal,
                    difficulty = state.source.difficulty,
                    solved = state.game.status == BlockSudokuStatus.SOLVED,
                    completion = controller.dailyCompletionState,
                    scoreDetail = scoreDetail,
                    onRetry = { livesGuard { transitionAd(controller::retry) } },
                    onRetrySave = controller::retryDailySave,
                    onExit = { transitionAd(onExit) },
                )
                return
            }
            WebCatalogSaveErrorBanner(
                completion = controller.completionState,
                onRetrySave = controller::retrySave,
            )
            WebOrdinaryCatalogTerminalDialog(
                puzzleType = PuzzleType.BLOCK_SUDOKU,
                visible = state.game.status.isTerminal,
                difficulty = state.source.difficulty,
                levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
                solved = state.game.status == BlockSudokuStatus.SOLVED,
                solvedDetail = scoreDetail,
                failedDetail = scoreDetail,
                completion = controller.completionState,
                onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
                onRetry = { livesGuard { transitionAd(controller::retry) } },
                onRetrySave = controller::retrySave,
                onBack = { transitionAd(controller::showDifficultySelector) },
            )
        }
    }
}

@Composable
private fun BalanceFlow(
    state: WebBalanceState,
    controller: WebBalanceController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebBalanceState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.BALANCE,
                onBack = onExitBalance,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebBalanceState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitBalance
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebBalanceState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitBalance
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebBalanceState.Playing ->
            PlayingBalanceContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitBalance = onExitBalance,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun CrownsFlow(
    state: WebCrownsState,
    controller: WebCrownsController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebCrownsState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.CROWNS,
                onBack = onExitCrowns,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebCrownsState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitCrowns
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebCrownsState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitCrowns
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebCrownsState.Playing ->
            PlayingCrownsContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitCrowns = onExitCrowns,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun WordFlow(
    state: WebWordState,
    controller: WebWordController,
    hardwareKeys: Flow<GameKey>,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebWordState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.WORD,
                onBack = onExitWord,
                onStart = controller::selectDifficulty,
            )
        is WebWordState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitWord
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebWordState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitWord
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebWordState.Playing ->
            PlayingWordContent(
                state = state,
                controller = controller,
                hardwareKeys = hardwareKeys,
                onExitWord = onExitWord,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun SudokuFlow(
    state: WebSudokuState,
    controller: WebSudokuController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebSudokuState.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.SUDOKU,
                onBack = onExitSudoku,
                onStart = controller::selectDifficulty,
                onReplay = controller::replayLevel,
            )
        is WebSudokuState.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitSudoku
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebSudokuState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitSudoku
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is WebSudokuState.Playing ->
            PlayingSudokuContent(
                state = state,
                controller = controller,
                hintCount = hintCount,
                onOpenStore = onOpenStore,
                onExitSudoku = onExitSudoku,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun Game2048Flow(
    state: Web2048State,
    controller: Web2048Controller,
    onExitGame2048: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        Web2048State.DifficultySelection ->
            DifficultyContent(
                puzzleType = PuzzleType.GAME_2048,
                onBack = onExitGame2048,
                onStart = controller::selectDifficulty,
            )
        is Web2048State.Loading ->
            WebCatalogLoadingContent(
                difficulty = state.difficulty,
                levelNumber = state.levelNumber?.value,
                onBack =
                    if (state.launch.isDaily) {
                        onExitGame2048
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is Web2048State.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack =
                    if (state.launch.isDaily) {
                        onExitGame2048
                    } else {
                        controller::showDifficultySelector
                    },
                isDaily = state.launch.isDaily,
            )
        is Web2048State.Playing ->
            PlayingGame2048Content(
                state = state,
                controller = controller,
                onExitGame2048 = onExitGame2048,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun DifficultyContent(
    puzzleType: PuzzleType,
    onBack: () -> Unit,
    onStart: (Difficulty) -> Unit,
    gallery: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onReplay: ((Difficulty, Int) -> Unit)? = null,
) {
    val ratingUi = LocalWebRating.current
    var levelsOpen by remember { mutableStateOf(false) }
    var ratingOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    if (ratingOpen) {
        GameRatingSheet(
            puzzleType = puzzleType,
            rating = ratingUi.rating(puzzleType),
            onDismiss = { ratingOpen = false },
            leaderboard = ratingUi.leaderboard?.let { table -> { table(puzzleType) } },
        )
    }
    if (galleryOpen) gallery?.invoke { galleryOpen = false }
    val stars = LocalWebCatalogStars.current
    val livesGuard = LocalWebLives.current
    if (levelsOpen && onReplay != null) {
        LevelMapSheet(
            currentLevels = ratingUi.progress.clearedLevels(puzzleType).mapValues { it.value + 1 },
            starsOf = { difficulty, level ->
                stars.starsOf(WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPackVersion.V1), level)
            },
            onPlayCurrent = { difficulty ->
                levelsOpen = false
                livesGuard.guard {
                    WebLastPlayed.record(puzzleType, difficulty)
                    onStart(difficulty)
                }
            },
            onReplay = { difficulty, level ->
                levelsOpen = false
                livesGuard.guard { onReplay(difficulty, level) }
            },
            onDismiss = { levelsOpen = false },
        )
    }
    val showTutorial = LocalOpenTutorial.current
    val openTutorial = {
        WebTutorialOffers.markOffered(puzzleType)
        showTutorial(puzzleType)
    }
    val lives = LocalWebLives.current
    // The first difficulty tap in a game offers its tutorial once; either answer settles it.
    var offeredDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    offeredDifficulty?.let { difficulty ->
        FirstPlayTutorialDialog(
            puzzleType = puzzleType,
            onOpenTutorial = {
                offeredDifficulty = null
                openTutorial()
            },
            onPlay = {
                offeredDifficulty = null
                WebTutorialOffers.markOffered(puzzleType)
                lives.guard {
                    WebLastPlayed.record(puzzleType, difficulty)
                    onStart(difficulty)
                }
            },
            onDismiss = { offeredDifficulty = null },
        )
    }
    Column(Modifier.fillMaxSize()) {
        // The same bar as gameplay: the way back, the game in the middle, and the wallet.
        WebTopBar(
            backLabel = stringResource(WebRes.string.web_to_games),
            onBack = onBack,
            title = stringResource(puzzleType.catalogTitleResource()),
        )
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = LogicaSpacing.screenVertical,
                    ),
        ) {
            // The wallet chip in the header already shows the lives; the line only adds the countdown.
            val livesState = lives.state?.takeIf { it.nextLifeRestoreAtEpochMs != null }
            val livesHeight = if (livesState != null) LIVES_STATUS_HEIGHT + LogicaSpacing.section else 0.dp
            // The Word game says up front that its words are Russian in every language.
            val showsWordNotice = puzzleType == PuzzleType.WORD
            val noticeHeight = if (showsWordNotice) WORD_NOTICE_HEIGHT + LogicaSpacing.section else 0.dp
            // The wide host shows the four difficulties as a 2x2 grid of taller cards.
            val columns = if (LocalWebWideLayout.current) 2 else 1
            val rows = 4 / columns
            val cardHeight =
                (
                    (
                        maxHeight - TUTORIAL_ACTION_HEIGHT - livesHeight - noticeHeight -
                            LogicaSpacing.section - LogicaSpacing.item * (rows - 1)
                    ) / rows
                ).coerceIn(MIN_DIFFICULTY_CARD_HEIGHT, if (columns > 1) MAX_WIDE_DIFFICULTY_CARD_HEIGHT else MAX_DIFFICULTY_CARD_HEIGHT)
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(TUTORIAL_ACTION_HEIGHT),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DifficultyScreenActions(
                        howToPlayLabel = stringResource(Res.string.how_to_play),
                        onHowToPlay = openTutorial,
                        onRating = { ratingOpen = true },
                        onGallery = gallery?.let { { galleryOpen = true } },
                        // The gallery is the Nonogram's way back into its cleared levels.
                        onLevels = onReplay?.takeIf { gallery == null }?.let { { levelsOpen = true } },
                    )
                }
                if (showsWordNotice) WordLanguageNotice(Modifier.height(WORD_NOTICE_HEIGHT))
                if (livesState != null) {
                    WebLivesStatus(livesState, Modifier.height(LIVES_STATUS_HEIGHT))
                }
                DifficultySelector(
                    onStart = { difficulty ->
                        if (WebTutorialOffers.isPending(puzzleType)) {
                            offeredDifficulty = difficulty
                        } else {
                            lives.guard {
                                WebLastPlayed.record(puzzleType, difficulty)
                                onStart(difficulty)
                            }
                        }
                    },
                    enabled = true,
                    cardHeight = cardHeight,
                    columns = columns,
                    stars = LocalWebCatalogStars.current.starsByDifficulty(puzzleType),
                )
            }
        }
    }
}

@Composable
private fun PlayingBalanceContent(
    state: WebBalanceState.Playing,
    controller: WebBalanceController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.BALANCE,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitBalance else controller::showDifficultySelector,
        )
        BalanceGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == BalanceGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
        )
    }

    // The third mistake waits here for the one ad-paid second chance.
    if (controller.secondChanceOffered) {
        WebSecondChanceDialog(onContinue = controller::continueAfterAd, onDecline = controller::declineSecondChance)
    }
    if (controller.hintsExhaustedNotice) {
        WebHintsExhaustedDialog(
            onOpenStore = {
                controller.dismissHintsExhaustedNotice()
                onOpenStore()
            },
            onDismiss = controller::dismissHintsExhaustedNotice,
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.BALANCE,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitBalance) },
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            puzzleType = PuzzleType.BALANCE,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            replay = (state.source as? WebGameplaySource.CatalogLevel)?.attempt?.replay == true,
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
private fun PlayingCrownsContent(
    state: WebCrownsState.Playing,
    controller: WebCrownsController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.CROWNS,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitCrowns else controller::showDifficultySelector,
        )
        CrownsGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            selectedValue = state.selectedValue,
            isPencilMode = state.isPencilMode,
            isHintLoading = state.isHintLoading,
            gameplayEnabled = state.game.status == CrownsGameStatus.IN_PROGRESS,
            onCellTapped = controller::onCellTapped,
            onSelectValue = controller::selectValue,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
        )
    }

    // The third mistake waits here for the one ad-paid second chance.
    if (controller.secondChanceOffered) {
        WebSecondChanceDialog(onContinue = controller::continueAfterAd, onDecline = controller::declineSecondChance)
    }
    if (controller.hintsExhaustedNotice) {
        WebHintsExhaustedDialog(
            onOpenStore = {
                controller.dismissHintsExhaustedNotice()
                onOpenStore()
            },
            onDismiss = controller::dismissHintsExhaustedNotice,
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.CROWNS,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitCrowns) },
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            puzzleType = PuzzleType.CROWNS,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            replay = (state.source as? WebGameplaySource.CatalogLevel)?.attempt?.replay == true,
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
private fun PlayingWordContent(
    state: WebWordState.Playing,
    controller: WebWordController,
    hardwareKeys: Flow<GameKey>,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.WORD,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress,
            onExit = if (state.source.isDaily) onExitWord else controller::showDifficultySelector,
        )
        WordGameContent(
            puzzle = state.puzzle,
            game = state.game,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            rejection = state.rejection,
            rejectionRevision = state.rejectionRevision,
            acceptedAttemptRevision = state.acceptedAttemptRevision,
            gameplayEnabled = state.game.status == WordGameStatus.IN_PROGRESS,
            onLetter = controller::setLetter,
            onClearLetter = controller::clearLetter,
            onSubmit = controller::submit,
            onDismissRejection = controller::dismissRejection,
            onAcceptedAttemptRevealed = controller::onAcceptedAttemptRevealed,
            modifier = Modifier.weight(1f),
            hardwareKeys = hardwareKeys,
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.WORD,
            visible = state.isTerminalRevealReady,
            difficulty = state.source.difficulty,
            solved = state.game.status == WordGameStatus.SOLVED,
            // Spoiler-free: the Daily dialog never reveals the answer, unlike the Catalog one.
            scoreDetail = pluralStringResource(WebRes.plurals.web_word_guessed, state.game.attempts.size, state.game.attempts.size),
            stars = starsForWordAttempts(state.game.attempts.size),
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitWord) },
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            puzzleType = PuzzleType.WORD,
            visible = state.isTerminalRevealReady,
            difficulty = state.source.difficulty,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == WordGameStatus.SOLVED,
            completion = controller.completionState,
            solvedDetail = pluralStringResource(WebRes.plurals.web_word_level_solved, state.game.attempts.size, state.game.attempts.size),
            stars = starsForWordAttempts(state.game.attempts.size),
            failedDetail = stringResource(WebRes.string.web_word_answer, state.puzzle.answer.uppercase()),
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
private fun PlayingSudokuContent(
    state: WebSudokuState.Playing,
    controller: WebSudokuController,
    hintCount: Int?,
    onOpenStore: () -> Unit,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.SUDOKU,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress = state.game.hasMeaningfulProgress || controller.secondChanceOffered,
            onExit = if (state.source.isDaily) onExitSudoku else controller::showDifficultySelector,
        )
        val selectedStatus = state.selectedCell?.let(state.game::cellAt)?.status
        val gameplayEnabled = state.game.status == SudokuGameStatus.IN_PROGRESS
        val inputEnabled =
            gameplayEnabled &&
                if (state.isPencilMode) {
                    selectedStatus == SudokuCellStatus.EMPTY
                } else {
                    selectedStatus == SudokuCellStatus.EMPTY || selectedStatus == SudokuCellStatus.INCORRECT
                }
        SudokuGameContent(
            puzzle = state.puzzle,
            game = state.game,
            selectedCell = state.selectedCell,
            isPencilMode = state.isPencilMode,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            gameplayEnabled = gameplayEnabled,
            inputEnabled = inputEnabled,
            onCellSelected = controller::selectCell,
            onDigit = controller::inputDigit,
            onTogglePencil = controller::togglePencilMode,
            onErase = controller::eraseSelectedCell,
            canUndo = controller.canUndo,
            onUndo = controller::undo,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
            hintCount = hintCount,
        )
    }

    // The third mistake waits here for the one ad-paid second chance.
    if (controller.secondChanceOffered) {
        WebSecondChanceDialog(onContinue = controller::continueAfterAd, onDecline = controller::declineSecondChance)
    }
    if (controller.hintsExhaustedNotice) {
        WebHintsExhaustedDialog(
            onOpenStore = {
                controller.dismissHintsExhaustedNotice()
                onOpenStore()
            },
            onDismiss = controller::dismissHintsExhaustedNotice,
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.SUDOKU,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitSudoku) },
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            puzzleType = PuzzleType.SUDOKU,
            visible = state.game.status.isTerminal && !controller.secondChanceOffered,
            difficulty = state.source.difficulty,
            mistakesUsed = state.game.mistakesUsed,
            hintsUsed = state.game.hintsUsed,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            replay = (state.source as? WebGameplaySource.CatalogLevel)?.attempt?.replay == true,
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
private fun PlayingGame2048Content(
    state: Web2048State.Playing,
    controller: Web2048Controller,
    onExitGame2048: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    val livesGuard = LocalWebLives.current.guard
    val transitionAd = LocalWebTransitionAd.current
    Column(Modifier.fillMaxSize()) {
        WebGameplayHeader(
            puzzleType = PuzzleType.GAME_2048,
            isDaily = state.source.isDaily,
            hasMeaningfulProgress =
                state.game.hasMeaningfulProgress(
                    levelCleared = !state.source.isDaily && state.game.goalReached,
                    completionSaved = controller.completionState is WebCatalogCompletionState.Saved,
                ),
            onExit = if (state.source.isDaily) onExitGame2048 else controller::showDifficultySelector,
        )
        // Catalog-only: the save banner and cleared marker belong to Catalog progression.
        if (!state.source.isDaily) {
            WebCatalogSaveErrorBanner(
                completion = controller.completionState,
                onRetrySave = controller::retrySave,
            )
        }
        Game2048Content(
            game = state.game,
            difficulty = state.source.difficulty,
            levelNumber = state.source.catalogLevelNumberOrNull,
            contextBadgeLabel = state.source.contextBadgeLabelOrNull(),
            levelCleared = !state.source.isDaily && controller.completionState is WebCatalogCompletionState.Saved,
            motionRevision = state.motionRevision,
            motionTrace = state.motionTrace,
            gameplayEnabled = state.game.status == Game2048Status.IN_PROGRESS,
            canUndo = controller.canUndo,
            onMove = controller::move,
            onUndo = controller::undo,
            onMotionFinished = controller::finishMotion,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            puzzleType = PuzzleType.GAME_2048,
            visible = state.game.status.isTerminal && state.motionTrace == null,
            difficulty = state.source.difficulty,
            solved = state.game.goalReached,
            scoreDetail = stringResource(WebRes.string.web_score_final, formatGame2048Number(state.game.score)),
            completion = controller.dailyCompletionState,
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retryDailySave,
            onExit = { transitionAd(onExitGame2048) },
        )
    } else {
        Web2048CatalogTerminalDialog(
            visible = state.game.status.isTerminal && state.motionTrace == null,
            difficulty = state.source.difficulty,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            goalReached = state.game.goalReached,
            score = formatGame2048Number(state.game.score),
            completion = controller.completionState,
            onNextLevel = { livesGuard { onSolvedNextLevel { controller.nextLevel() } } },
            onRetry = { livesGuard { transitionAd(controller::retry) } },
            onRetrySave = controller::retrySave,
            onBack = { transitionAd(controller::showDifficultySelector) },
        )
    }
}

@Composable
private fun FatalContent(message: String) {
    CenteredColumn {
        Text(
            text = stringResource(WebRes.string.web_fatal_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Compact Daily marker instead of a Catalog level number; Catalog keeps its normal metadata. */
@Composable
private fun WebGameplaySource.contextBadgeLabelOrNull(): String? = if (isDaily) stringResource(Res.string.daily_marker) else null

@Composable
internal fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val LIVES_STATUS_HEIGHT = 24.dp
private val TUTORIAL_ACTION_HEIGHT = 40.dp
private val WORD_NOTICE_HEIGHT = 40.dp
private val PRIMARY_NAVIGATION_HEIGHT = 64.dp
private val WIDE_HOST_MIN_WIDTH = 720.dp

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
private val WIDE_HOST_MAX_WIDTH = 1440.dp
private const val WIDE_HOST_ASPECT = 1.2f

/** The widest phone column, width over height, before a window counts as too wide for it. */
private const val PORTRAIT_COLUMN_MAX_ASPECT = 0.8f
private val MIN_DIFFICULTY_CARD_HEIGHT = 96.dp
private val MAX_DIFFICULTY_CARD_HEIGHT = 152.dp
private val MAX_WIDE_DIFFICULTY_CARD_HEIGHT = 260.dp

private fun GameKey.game2048Direction(): Game2048Direction? =
    when (this) {
        GameKey.Up -> Game2048Direction.UP
        GameKey.Down -> Game2048Direction.DOWN
        GameKey.Left -> Game2048Direction.LEFT
        GameKey.Right -> Game2048Direction.RIGHT
        else -> null
    }
