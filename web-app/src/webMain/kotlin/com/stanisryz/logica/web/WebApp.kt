package com.stanisryz.logica.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_marker
import com.stanisryz.logica.shared.ui.generated.resources.daily_start_error
import com.stanisryz.logica.shared.ui.generated.resources.primary_games
import com.stanisryz.logica.shared.ui.generated.resources.primary_profile
import com.stanisryz.logica.shared.ui.generated.resources.primary_store
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameHubContent
import com.stanisryz.logica.ui.crowns.CrownsGameContent
import com.stanisryz.logica.ui.daily.DailyHubResultRow
import com.stanisryz.logica.ui.daily.DailyHubSection
import com.stanisryz.logica.ui.daily.DailyHubUiState
import com.stanisryz.logica.ui.daily.DailyShareFormatter
import com.stanisryz.logica.ui.game2048.Game2048Content
import com.stanisryz.logica.ui.game2048.formatGame2048Number
import com.stanisryz.logica.ui.profile.DailyProfileMetrics
import com.stanisryz.logica.ui.profile.ProfileContent
import com.stanisryz.logica.ui.profile.ProfileEconomyMetrics
import com.stanisryz.logica.ui.profile.ProfileUiState
import com.stanisryz.logica.ui.sudoku.SudokuGameContent
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.ui.word.WordGameContent
import org.jetbrains.compose.resources.stringResource

private sealed interface WebRoute {
    data object GameHub : WebRoute

    data object Profile : WebRoute

    data object Store : WebRoute

    data object Balance : WebRoute

    data object Crowns : WebRoute

    data object Word : WebRoute

    data object Sudoku : WebRoute

    data object Game2048 : WebRoute
}

private fun routeHasActivePuzzle(
    route: WebRoute,
    balanceState: WebBalanceState,
    crownsState: WebCrownsState,
    wordState: WebWordState,
    sudokuState: WebSudokuState,
    game2048State: Web2048State,
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
    lifecycle: WebHostLifecycle,
    playerSession: WebPlayerSessionController,
    dailyCoordinator: WebDailyGameplayCoordinator,
    storeProcessor: WebStoreProcessor,
    rewardedHintsController: WebStoreRewardedHintsController,
    interstitialController: WebInterstitialContinuationController,
    fullscreenAdGate: WebFullscreenAdGate,
    stickyBannerController: WebStickyBannerController,
) {
    val lifecycleState by lifecycle.state.collectAsState()

    LogicaTheme(darkTheme = false) {
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
            playerSession,
        ) {
            onDispose {
                controller.setGameplayActive(false)
                balanceController.dispose()
                crownsController.dispose()
                wordController.dispose()
                sudokuController.dispose()
                game2048Controller.dispose()
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
                        playerSession = playerSession,
                        dailyCoordinator = dailyCoordinator,
                        storeProcessor = storeProcessor,
                        rewardedHintsController = rewardedHintsController,
                        interstitialController = interstitialController,
                        fullscreenAdGate = fullscreenAdGate,
                        stickyBannerController = stickyBannerController,
                        onRendered = controller::onInitialHostUiReady,
                    )
                is WebBootstrapState.FatalError -> FatalContent(state.message)
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
        val widthLimited = maxWidth * 16f <= maxHeight * 9f
        val portraitWidth = if (widthLimited) maxWidth else maxHeight * 9f / 16f
        val portraitHeight = if (widthLimited) maxWidth * 16f / 9f else maxHeight

        Surface(
            modifier = Modifier.width(portraitWidth).height(portraitHeight),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun LoadingContent() {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Логика загружается",
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
    playerSession: WebPlayerSessionController,
    dailyCoordinator: WebDailyGameplayCoordinator,
    storeProcessor: WebStoreProcessor,
    rewardedHintsController: WebStoreRewardedHintsController,
    interstitialController: WebInterstitialContinuationController,
    fullscreenAdGate: WebFullscreenAdGate,
    stickyBannerController: WebStickyBannerController,
    onRendered: () -> Unit,
) {
    var route by remember { mutableStateOf<WebRoute>(WebRoute.GameHub) }
    val balanceState = balanceController.state
    val crownsState = crownsController.state
    val wordState = wordController.state
    val sudokuState = sudokuController.state
    val game2048State = game2048Controller.state
    val accountChangeRevision = playerSession.accountChangeRevision

    // Fullscreen ads force the host inactive through one seam; closing always re-evaluates the
    // real browser visibility/focus/Yandex state instead of blindly forcing ACTIVE again.
    LaunchedEffect(fullscreenAdGate, lifecycleState) { fullscreenAdGate.refresh() }
    val hostActive by fullscreenAdGate.isActive.collectAsState()

    // A passed midnight must re-render the new day's Daily definition; gameplay attempts keep
    // their own captured challenge date, so this only affects hub presentation.
    var dateRefreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == PlatformLifecycleState.ACTIVE) dateRefreshKey++
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
            route = WebRoute.GameHub
        }
    }
    val hasActivePuzzle =
        routeHasActivePuzzle(route, balanceState, crownsState, wordState, sudokuState, game2048State)
    LaunchedEffect(route, hasActivePuzzle, hostActive) {
        controller.setGameplayActive(hasActivePuzzle && hostActive)
    }

    // Sticky-banner visibility is platform-side (rendered by Yandex, never drawn in Compose).
    // Transitions are suppressed while a fullscreen ad owns the screen and reapplied right
    // after it closes; repeated identical requests never reach the bridge again.
    val adShowing by fullscreenAdGate.adShowing.collectAsState()
    val bannerVisible = stickyBannerVisible(route, hasActivePuzzle)
    LaunchedEffect(bannerVisible, adShowing) {
        if (!adShowing) stickyBannerController.applyVisibility(bannerVisible)
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
                                else -> error("$puzzleType has no Web game flow.")
                            }
                    },
                    headerContent = {
                        WebDailyHubRoute(
                            playerSession = playerSession,
                            coordinator = dailyCoordinator,
                            dateRefreshKey = dateRefreshKey,
                            onStartDaily = { puzzleType ->
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
                                                else -> error("$puzzleType has no Daily gameplay.")
                                            }
                                    }
                                    else -> Unit // surfaced as a start error by the shared hub section
                                }
                            },
                        )
                    },
                )
            }
        WebRoute.Profile ->
            PrimaryDestinationShell(
                selected = WebRoute.Profile,
                onSelect = { route = it },
            ) {
                WebProfileRoute(
                    playerSession = playerSession,
                    binding = playerSession.statisticsBinding.collectAsState().value,
                    onRetry = playerSession::retryCurrentContext,
                )
            }
        WebRoute.Store ->
            PrimaryDestinationShell(
                selected = WebRoute.Store,
                onSelect = { route = it },
            ) {
                WebStoreScreen(
                    playerSession = playerSession,
                    storeProcessor = storeProcessor,
                    rewardedHintsController = rewardedHintsController,
                )
            }
        WebRoute.Balance ->
            BalanceFlow(
                state = balanceState,
                controller = balanceController,
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
    }
}

@Composable
private fun PrimaryDestinationShell(
    selected: WebRoute,
    onSelect: (WebRoute) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        NavigationBar(modifier = Modifier.fillMaxWidth().height(PRIMARY_NAVIGATION_HEIGHT)) {
            NavigationBarItem(
                selected = selected == WebRoute.GameHub,
                onClick = { onSelect(WebRoute.GameHub) },
                icon = { Icon(Icons.Outlined.SportsEsports, contentDescription = null) },
                label = { Text(stringResource(Res.string.primary_games)) },
            )
            NavigationBarItem(
                selected = selected == WebRoute.Profile,
                onClick = { onSelect(WebRoute.Profile) },
                icon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                label = { Text(stringResource(Res.string.primary_profile)) },
            )
            NavigationBarItem(
                selected = selected == WebRoute.Store,
                onClick = { onSelect(WebRoute.Store) },
                icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                label = { Text(stringResource(Res.string.primary_store)) },
            )
        }
    }
}

/**
 * Reactive Web Daily Hub presentation over the current Player-scoped binding. It performs no
 * cloud read and mutates nothing: the durable run is created only when gameplay actually starts.
 */
@Composable
private fun WebDailyHubRoute(
    playerSession: WebPlayerSessionController,
    coordinator: WebDailyGameplayCoordinator,
    dateRefreshKey: Int,
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
                // Re-read the calendar date only on resume ticks, so a passed midnight re-renders
                // the new day's definition without polling while the hub simply stays visible.
                val currentDate = remember(dateRefreshKey) { BrowserLocalWebDailyDateProvider.currentDate() }
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
                                            WebDailyTextSharer.share(DailyShareFormatter.format(sharePayload))
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
    binding: WebStatisticsBinding,
    onRetry: () -> Unit,
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
                val snapshot by binding.repository.snapshot.collectAsState()
                // Real Daily metrics come from the currently bound Player's Daily repository and
                // update locally after gameplay; opening Profile never triggers a cloud read.
                val dailyMetrics =
                    webDailyProfileMetricsOrNull(
                        dailyBinding = playerSession.dailyBinding.collectAsState().value,
                        statisticsToken = binding.token,
                    )
                val economyMetrics =
                    webEconomyMetricsOrNull(
                        economyBinding = playerSession.economyBinding.collectAsState().value,
                        statisticsToken = binding.token,
                    )
                ProfileContent(
                    uiState =
                        WebStatisticsAggregator
                            .aggregate(snapshot)
                            .toProfileStatistics()
                            .copy(dailyMetrics = dailyMetrics, economy = economyMetrics)
                            .toUiState(),
                    onRetry = onRetry,
                )
            }
    }
}

/** Daily metrics from the Daily repository bound to exactly this Player context, else absent. */
@Composable
private fun webDailyProfileMetricsOrNull(
    dailyBinding: WebDailyBinding,
    statisticsToken: WebPlayerContextToken,
): DailyProfileMetrics? =
    when {
        dailyBinding is WebDailyBinding.Ready && dailyBinding.token == statisticsToken -> {
            val snapshot by dailyBinding.repository.snapshot.collectAsState()
            snapshot.dailyProfileMetrics(BrowserLocalWebDailyDateProvider.currentDate())
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
private fun BalanceFlow(
    state: WebBalanceState,
    controller: WebBalanceController,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebBalanceState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Баланс",
                onBack = onExitBalance,
                onStart = controller::selectDifficulty,
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
                onExitBalance = onExitBalance,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun CrownsFlow(
    state: WebCrownsState,
    controller: WebCrownsController,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebCrownsState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Короны",
                onBack = onExitCrowns,
                onStart = controller::selectDifficulty,
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
                onExitCrowns = onExitCrowns,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun WordFlow(
    state: WebWordState,
    controller: WebWordController,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebWordState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Слово",
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
                onExitWord = onExitWord,
                onSolvedNextLevel = onSolvedNextLevel,
            )
    }
}

@Composable
private fun SudokuFlow(
    state: WebSudokuState,
    controller: WebSudokuController,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    when (state) {
        WebSudokuState.DifficultySelection ->
            DifficultyContent(
                gameTitle = "Судоку",
                onBack = onExitSudoku,
                onStart = controller::selectDifficulty,
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
                gameTitle = "2048",
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
    gameTitle: String,
    onBack: () -> Unit,
    onStart: (Difficulty) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
    ) {
        val cardHeight =
            ((maxHeight - DIFFICULTY_HEADER_HEIGHT - LogicaSpacing.section - LogicaSpacing.item * 3) / 4)
                .coerceIn(MIN_DIFFICULTY_CARD_HEIGHT, MAX_DIFFICULTY_CARD_HEIGHT)
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(DIFFICULTY_HEADER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Назад") }
                Text(
                    text = "$gameTitle · выберите сложность",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            DifficultySelector(
                onStart = onStart,
                enabled = true,
                cardHeight = cardHeight,
            )
        }
    }
}

@Composable
private fun PlayingBalanceContent(
    state: WebBalanceState.Playing,
    controller: WebBalanceController,
    onExitBalance: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (state.source.isDaily) onExitBalance else controller::showDifficultySelector) {
                Text(if (state.source.isDaily) "К играм" else "К сложности")
            }
            Spacer(Modifier.weight(1f))
            Text("Баланс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
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
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            visible = state.game.status.isTerminal,
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = controller::retry,
            onRetrySave = controller::retryDailySave,
            onExit = onExitBalance,
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            visible = state.game.status.isTerminal,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == BalanceGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { onSolvedNextLevel { controller.nextLevel() } },
            onRetry = controller::retry,
            onRetrySave = controller::retrySave,
            onBack = controller::showDifficultySelector,
        )
    }
}

@Composable
private fun PlayingCrownsContent(
    state: WebCrownsState.Playing,
    controller: WebCrownsController,
    onExitCrowns: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (state.source.isDaily) onExitCrowns else controller::showDifficultySelector) {
                Text(if (state.source.isDaily) "К играм" else "К сложности")
            }
            Spacer(Modifier.weight(1f))
            Text("Короны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
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
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            visible = state.game.status.isTerminal,
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = controller::retry,
            onRetrySave = controller::retryDailySave,
            onExit = onExitCrowns,
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            visible = state.game.status.isTerminal,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == CrownsGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { onSolvedNextLevel { controller.nextLevel() } },
            onRetry = controller::retry,
            onRetrySave = controller::retrySave,
            onBack = controller::showDifficultySelector,
        )
    }
}

@Composable
private fun PlayingWordContent(
    state: WebWordState.Playing,
    controller: WebWordController,
    onExitWord: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (state.source.isDaily) onExitWord else controller::showDifficultySelector) {
                Text(if (state.source.isDaily) "К играм" else "К сложности")
            }
            Spacer(Modifier.weight(1f))
            Text("Слово", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
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
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            visible = state.isTerminalRevealReady,
            solved = state.game.status == WordGameStatus.SOLVED,
            // Spoiler-free: the Daily dialog never reveals the answer, unlike the Catalog one.
            scoreDetail = "Отгадано за ${state.game.attempts.size} попыток.",
            completion = controller.dailyCompletionState,
            onRetry = controller::retry,
            onRetrySave = controller::retryDailySave,
            onExit = onExitWord,
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            visible = state.isTerminalRevealReady,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == WordGameStatus.SOLVED,
            completion = controller.completionState,
            solvedDetail = "Уровень пройден за ${state.game.attempts.size} попыток.",
            failedDetail = "Загаданное слово: ${state.puzzle.answer.uppercase()}",
            onNextLevel = { onSolvedNextLevel { controller.nextLevel() } },
            onRetry = controller::retry,
            onRetrySave = controller::retrySave,
            onBack = controller::showDifficultySelector,
        )
    }
}

@Composable
private fun PlayingSudokuContent(
    state: WebSudokuState.Playing,
    controller: WebSudokuController,
    onExitSudoku: () -> Unit,
    onSolvedNextLevel: (() -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (state.source.isDaily) onExitSudoku else controller::showDifficultySelector) {
                Text(if (state.source.isDaily) "К играм" else "К сложности")
            }
            Spacer(Modifier.weight(1f))
            Text("Судоку", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
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
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            visible = state.game.status.isTerminal,
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.dailyCompletionState,
            onRetry = controller::retry,
            onRetrySave = controller::retryDailySave,
            onExit = onExitSudoku,
        )
    } else {
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        WebOrdinaryCatalogTerminalDialog(
            visible = state.game.status.isTerminal,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            solved = state.game.status == SudokuGameStatus.SOLVED,
            completion = controller.completionState,
            onNextLevel = { onSolvedNextLevel { controller.nextLevel() } },
            onRetry = controller::retry,
            onRetrySave = controller::retrySave,
            onBack = controller::showDifficultySelector,
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
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = if (state.source.isDaily) onExitGame2048 else controller::showDifficultySelector) {
                Text(if (state.source.isDaily) "К играм" else "К сложности")
            }
            Spacer(Modifier.weight(1f))
            Text("2048", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
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
            onMove = controller::move,
            onMotionFinished = controller::finishMotion,
            modifier = Modifier.weight(1f),
        )
    }

    if (state.source.isDaily) {
        WebDailyOrdinaryTerminalDialog(
            visible = state.game.status.isTerminal && state.motionTrace == null,
            solved = state.game.goalReached,
            scoreDetail = "Итоговый счёт: ${formatGame2048Number(state.game.score)}.",
            completion = controller.dailyCompletionState,
            onRetry = controller::retry,
            onRetrySave = controller::retryDailySave,
            onExit = onExitGame2048,
        )
    } else {
        Web2048CatalogTerminalDialog(
            visible = state.game.status.isTerminal && state.motionTrace == null,
            levelNumber = requireNotNull(state.source.catalogLevelNumberOrNull),
            goalReached = state.game.goalReached,
            score = formatGame2048Number(state.game.score),
            completion = controller.completionState,
            onNextLevel = { onSolvedNextLevel { controller.nextLevel() } },
            onRetry = controller::retry,
            onRetrySave = controller::retrySave,
            onBack = controller::showDifficultySelector,
        )
    }
}

@Composable
private fun FatalContent(message: String) {
    CenteredColumn {
        Text(
            text = "Не удалось запустить Web-версию",
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

private fun Difficulty.webLabel(): String =
    when (this) {
        Difficulty.EASY -> "Легко"
        Difficulty.MEDIUM -> "Средне"
        Difficulty.HARD -> "Сложно"
        Difficulty.EXPERT -> "Эксперт"
    }

private val DIFFICULTY_HEADER_HEIGHT = 48.dp
private val PRIMARY_NAVIGATION_HEIGHT = 64.dp
private val MIN_DIFFICULTY_CARD_HEIGHT = 96.dp
private val MAX_DIFFICULTY_CARD_HEIGHT = 152.dp
private val GAME_HEADER_HEIGHT = 52.dp
private val GAME_HEADER_TITLE_SPACER = 92.dp
