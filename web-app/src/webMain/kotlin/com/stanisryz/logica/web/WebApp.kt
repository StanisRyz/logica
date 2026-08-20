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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.primary_games
import com.stanisryz.logica.shared.ui.generated.resources.primary_profile
import com.stanisryz.logica.ui.balance.BalanceGameContent
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.components.GAME_CATALOG_PUZZLE_TYPES
import com.stanisryz.logica.ui.components.GameHubContent
import com.stanisryz.logica.ui.crowns.CrownsGameContent
import com.stanisryz.logica.ui.game2048.Game2048Content
import com.stanisryz.logica.ui.game2048.formatGame2048Number
import com.stanisryz.logica.ui.profile.ProfileContent
import com.stanisryz.logica.ui.profile.ProfileUiState
import com.stanisryz.logica.ui.sudoku.SudokuGameContent
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme
import com.stanisryz.logica.ui.word.WordGameContent
import org.jetbrains.compose.resources.stringResource

private sealed interface WebRoute {
    data object GameHub : WebRoute

    data object Profile : WebRoute

    data object Balance : WebRoute

    data object Crowns : WebRoute

    data object Word : WebRoute

    data object Sudoku : WebRoute

    data object Game2048 : WebRoute
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
    onRendered: () -> Unit,
) {
    var route by remember { mutableStateOf<WebRoute>(WebRoute.GameHub) }
    val balanceState = balanceController.state
    val crownsState = crownsController.state
    val wordState = wordController.state
    val sudokuState = sudokuController.state
    val game2048State = game2048Controller.state
    val accountChangeRevision = playerSession.accountChangeRevision

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
    LaunchedEffect(route, balanceState, crownsState, wordState, sudokuState, game2048State, lifecycleState) {
        controller.setGameplayActive(
            when (route) {
                WebRoute.GameHub, WebRoute.Profile -> false
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
            } &&
                lifecycleState == PlatformLifecycleState.ACTIVE,
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
                )
            }
        WebRoute.Profile ->
            PrimaryDestinationShell(
                selected = WebRoute.Profile,
                onSelect = { route = it },
            ) {
                WebProfileRoute(
                    binding = playerSession.statisticsBinding.collectAsState().value,
                    onRetry = playerSession::retryCurrentContext,
                )
            }
        WebRoute.Balance ->
            BalanceFlow(
                state = balanceState,
                controller = balanceController,
                onExitBalance = {
                    balanceController.showDifficultySelector()
                    route = WebRoute.GameHub
                },
            )
        WebRoute.Crowns ->
            CrownsFlow(
                state = crownsState,
                controller = crownsController,
                onExitCrowns = {
                    crownsController.showDifficultySelector()
                    route = WebRoute.GameHub
                },
            )
        WebRoute.Word ->
            WordFlow(
                state = wordState,
                controller = wordController,
                onExitWord = {
                    wordController.showDifficultySelector()
                    route = WebRoute.GameHub
                },
            )
        WebRoute.Sudoku ->
            SudokuFlow(
                state = sudokuState,
                controller = sudokuController,
                onExitSudoku = {
                    sudokuController.showDifficultySelector()
                    route = WebRoute.GameHub
                },
            )
        WebRoute.Game2048 ->
            Game2048Flow(
                state = game2048State,
                controller = game2048Controller,
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
        }
    }
}

@Composable
private fun WebProfileRoute(
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
                ProfileContent(
                    uiState =
                        WebStatisticsAggregator
                            .aggregate(snapshot)
                            .toProfileStatistics()
                            .toUiState(),
                    onRetry = onRetry,
                )
            }
    }
}

@Composable
private fun BalanceFlow(
    state: WebBalanceState,
    controller: WebBalanceController,
    onExitBalance: () -> Unit,
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
                onBack = controller::showDifficultySelector,
            )
        is WebBalanceState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = controller::showDifficultySelector,
            )
        is WebBalanceState.Playing ->
            PlayingBalanceContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun CrownsFlow(
    state: WebCrownsState,
    controller: WebCrownsController,
    onExitCrowns: () -> Unit,
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
                onBack = controller::showDifficultySelector,
            )
        is WebCrownsState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = controller::showDifficultySelector,
            )
        is WebCrownsState.Playing ->
            PlayingCrownsContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun WordFlow(
    state: WebWordState,
    controller: WebWordController,
    onExitWord: () -> Unit,
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
                onBack = controller::showDifficultySelector,
            )
        is WebWordState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = controller::showDifficultySelector,
            )
        is WebWordState.Playing ->
            PlayingWordContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun SudokuFlow(
    state: WebSudokuState,
    controller: WebSudokuController,
    onExitSudoku: () -> Unit,
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
                onBack = controller::showDifficultySelector,
            )
        is WebSudokuState.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = controller::showDifficultySelector,
            )
        is WebSudokuState.Playing ->
            PlayingSudokuContent(
                state = state,
                controller = controller,
            )
    }
}

@Composable
private fun Game2048Flow(
    state: Web2048State,
    controller: Web2048Controller,
    onExitGame2048: () -> Unit,
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
                onBack = controller::showDifficultySelector,
            )
        is Web2048State.Error ->
            WebCatalogLevelErrorContent(
                levelNumber = state.levelNumber?.value,
                detail = state.detail,
                onRetry = controller::retryLoading,
                onBack = controller::showDifficultySelector,
            )
        is Web2048State.Playing ->
            PlayingGame2048Content(
                state = state,
                controller = controller,
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
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Баланс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        BalanceGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.definition.difficulty,
            levelNumber = state.definition.levelNumber.value,
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

    WebOrdinaryCatalogTerminalDialog(
        visible = state.game.status.isTerminal,
        levelNumber = state.definition.levelNumber.value,
        solved = state.game.status == BalanceGameStatus.SOLVED,
        completion = controller.completionState,
        onNextLevel = controller::nextLevel,
        onRetry = controller::retry,
        onRetrySave = controller::retrySave,
        onBack = controller::showDifficultySelector,
    )
}

@Composable
private fun PlayingCrownsContent(
    state: WebCrownsState.Playing,
    controller: WebCrownsController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Короны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        CrownsGameContent(
            puzzle = state.puzzle,
            game = state.game,
            difficulty = state.definition.difficulty,
            levelNumber = state.definition.levelNumber.value,
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

    WebOrdinaryCatalogTerminalDialog(
        visible = state.game.status.isTerminal,
        levelNumber = state.definition.levelNumber.value,
        solved = state.game.status == CrownsGameStatus.SOLVED,
        completion = controller.completionState,
        onNextLevel = controller::nextLevel,
        onRetry = controller::retry,
        onRetrySave = controller::retrySave,
        onBack = controller::showDifficultySelector,
    )
}

@Composable
private fun PlayingWordContent(
    state: WebWordState.Playing,
    controller: WebWordController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("Слово", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        WordGameContent(
            puzzle = state.puzzle,
            game = state.game,
            levelNumber = state.definition.levelNumber.value,
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

    WebOrdinaryCatalogTerminalDialog(
        visible = state.isTerminalRevealReady,
        levelNumber = state.definition.levelNumber.value,
        solved = state.game.status == WordGameStatus.SOLVED,
        completion = controller.completionState,
        solvedDetail = "Уровень пройден за ${state.game.attempts.size} попыток.",
        failedDetail = "Загаданное слово: ${state.puzzle.answer.uppercase()}",
        onNextLevel = controller::nextLevel,
        onRetry = controller::retry,
        onRetrySave = controller::retrySave,
        onBack = controller::showDifficultySelector,
    )
}

@Composable
private fun PlayingSudokuContent(
    state: WebSudokuState.Playing,
    controller: WebSudokuController,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
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
            levelNumber = state.definition.levelNumber.value,
            gameplayEnabled = gameplayEnabled,
            inputEnabled = inputEnabled,
            onCellSelected = controller::selectCell,
            onDigit = controller::inputDigit,
            onTogglePencil = controller::togglePencilMode,
            onHint = controller::requestHint,
            modifier = Modifier.weight(1f),
        )
    }

    WebOrdinaryCatalogTerminalDialog(
        visible = state.game.status.isTerminal,
        levelNumber = state.definition.levelNumber.value,
        solved = state.game.status == SudokuGameStatus.SOLVED,
        completion = controller.completionState,
        onNextLevel = controller::nextLevel,
        onRetry = controller::retry,
        onRetrySave = controller::retrySave,
        onBack = controller::showDifficultySelector,
    )
}

@Composable
private fun PlayingGame2048Content(
    state: Web2048State.Playing,
    controller: Web2048Controller,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = controller::showDifficultySelector) { Text("К сложности") }
            Spacer(Modifier.weight(1f))
            Text("2048", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(GAME_HEADER_TITLE_SPACER))
        }
        WebCatalogSaveErrorBanner(
            completion = controller.completionState,
            onRetrySave = controller::retrySave,
        )
        Game2048Content(
            game = state.game,
            difficulty = state.definition.difficulty,
            levelNumber = state.definition.levelNumber.value,
            levelCleared = controller.completionState is WebCatalogCompletionState.Saved,
            motionRevision = state.motionRevision,
            motionTrace = state.motionTrace,
            gameplayEnabled = state.game.status == Game2048Status.IN_PROGRESS,
            onMove = controller::move,
            onMotionFinished = controller::finishMotion,
            modifier = Modifier.weight(1f),
        )
    }

    Web2048CatalogTerminalDialog(
        visible = state.game.status.isTerminal && state.motionTrace == null,
        levelNumber = state.definition.levelNumber.value,
        goalReached = state.game.goalReached,
        score = formatGame2048Number(state.game.score),
        completion = controller.completionState,
        onNextLevel = controller::nextLevel,
        onRetry = controller::retry,
        onRetrySave = controller::retrySave,
        onBack = controller::showDifficultySelector,
    )
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
