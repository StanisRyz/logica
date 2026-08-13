package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuHint
import com.stanisryz.logica.puzzle.core.sudoku.SudokuHintTechnique
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.sudoku.SudokuGameError
import com.stanisryz.logica.sudoku.SudokuGameUiState
import com.stanisryz.logica.sudoku.SudokuGameViewModel
import com.stanisryz.logica.sudoku.SudokuGameViewModelFactory
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.sudoku.SudokuBoard
import com.stanisryz.logica.ui.sudoku.SudokuNumberPad
import com.stanisryz.logica.ui.sudoku.SudokuToolBar
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun SudokuGameRoute(
    launch: GameAttemptLaunch,
    attemptFactory: GameAttemptFactory,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    exitGuard: GameplayExitGuard,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
    onTerminalAction: (() -> Unit) -> Unit = { it() },
) {
    val assets = LocalContext.current.assets
    val factory =
        remember(launch, assets, attemptFactory, completionRepository, economyRepository) {
            SudokuGameViewModelFactory(
                launch,
                assets,
                attemptFactory,
                completionRepository,
                economyRepository,
            )
        }
    val gameViewModel: SudokuGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    LeaveLevelGuard(exitGuard, (uiState as? SudokuGameUiState.Ready)?.hasMeaningfulProgress == true)
    SudokuGameScreen(
        uiState = uiState,
        economy = economy,
        isDaily = launch is GameAttemptLaunch.Daily,
        levelNumber = launch.levelNumberOrNull(),
        onSelectCell = gameViewModel::selectCell,
        onDigit = gameViewModel::inputDigit,
        onTogglePencil = gameViewModel::togglePencilMode,
        onHint = gameViewModel::requestHint,
        onRetryLevel = { onTerminalAction(gameViewModel::retry) },
        onRetryCompletion = gameViewModel::retryCompletion,
        onRetryLoad = gameViewModel::reload,
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNextLevel = { onTerminalAction(onNextLevel) },
        onGameHub = { onTerminalAction(onGameHub) },
        modifier = modifier,
    )
}

@Composable
private fun SudokuGameScreen(
    uiState: SudokuGameUiState,
    economy: PlayerEconomy,
    isDaily: Boolean,
    levelNumber: Int?,
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryLoad: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        SudokuGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.sudoku_loading))
        is SudokuGameUiState.Error -> {
            val canReload =
                uiState.reason == SudokuGameError.MISSING_DATASET ||
                    uiState.reason == SudokuGameError.CORRUPT_DATASET ||
                    uiState.reason == SudokuGameError.PUZZLE_NOT_FOUND
            RetryableErrorState(
                message = uiState.reason.message(),
                retryLabel = stringResource(if (canReload) R.string.retry else R.string.to_games),
                onRetry = if (canReload) onRetryLoad else onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        }
        is SudokuGameUiState.Ready ->
            SudokuReadyState(
                uiState = uiState,
                economy = economy,
                isDaily = isDaily,
                levelNumber = levelNumber,
                onSelectCell = onSelectCell,
                onDigit = onDigit,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onRetryLevel = onRetryLevel,
                onRetryCompletion = onRetryCompletion,
                onRestoreLife = onRestoreLife,
                hapticsEnabled = hapticsEnabled,
                onNextLevel = onNextLevel,
                onGameHub = onGameHub,
                modifier = modifier,
            )
    }
}

@Composable
private fun SudokuReadyState(
    uiState: SudokuGameUiState.Ready,
    economy: PlayerEconomy,
    isDaily: Boolean,
    levelNumber: Int?,
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = uiState.game
    val view = LocalView.current
    var previousMistakes by remember { mutableStateOf(game.mistakesUsed) }
    var previousStatus by remember { mutableStateOf(game.status) }
    LaunchedEffect(game.mistakesUsed) {
        if (hapticsEnabled && game.mistakesUsed > previousMistakes) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        previousMistakes = game.mistakesUsed
    }
    LaunchedEffect(game.status) {
        if (hapticsEnabled && game.status != previousStatus) {
            when (game.status) {
                SudokuGameStatus.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                SudokuGameStatus.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                SudokuGameStatus.IN_PROGRESS -> Unit
            }
        }
        previousStatus = game.status
    }

    val selectedStatus = uiState.selectedCell?.let(game::cellAt)?.status
    val inputEnabled =
        game.status == SudokuGameStatus.IN_PROGRESS &&
            economy.isGameplayAllowed &&
            if (uiState.isPencilMode) {
                selectedStatus == SudokuCellStatus.EMPTY
            } else {
                selectedStatus == SudokuCellStatus.EMPTY || selectedStatus == SudokuCellStatus.INCORRECT
            }
    val boardInteraction: (SudokuPosition) -> Unit = {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onSelectCell(it)
    }
    val hintEnabled = game.status == SudokuGameStatus.IN_PROGRESS && economy.isGameplayAllowed
    val compactPlayable = hintEnabled && game.currentHint == null
    if (compactPlayable) {
        CompactSudokuGameplay(
            game = game,
            selectedCell = uiState.selectedCell,
            isPencilMode = uiState.isPencilMode,
            difficultyLabel = difficultyLabel(PuzzleType.SUDOKU, uiState.puzzle.id.difficulty.toDifficulty()),
            levelNumber = levelNumber,
            mistakesUsed = game.mistakesUsed,
            inputEnabled = inputEnabled,
            onSelectCell = boardInteraction,
            onTogglePencil = onTogglePencil,
            onHint = onHint,
            onDigit = onDigit,
            modifier = modifier,
        )
    } else {
        ScreenColumn(
            modifier = modifier,
            verticalSpacing = LogicaSpacing.item,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGameplayContent(
                game = game,
                selectedCell = uiState.selectedCell,
                isPencilMode = uiState.isPencilMode,
                difficultyLabel = difficultyLabel(PuzzleType.SUDOKU, uiState.puzzle.id.difficulty.toDifficulty()),
                levelNumber = levelNumber,
                mistakesUsed = game.mistakesUsed,
                inputEnabled = inputEnabled,
                onSelectCell = boardInteraction,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onDigit = onDigit,
                hintEnabled = hintEnabled,
                economy = economy,
                onRestoreLife = onRestoreLife,
            )
        }
    }
    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == SudokuGameStatus.SOLVED,
            completionPersistence = uiState.completionPersistence,
            hintsUsed = game.hintsUsed,
            maxMistakes = SudokuGameState.MAX_MISTAKES,
            lives = economy.lives,
            difficulty =
                uiState.puzzle.id.difficulty
                    .toDifficulty(),
            isRetryAllowed = economy.isGameplayAllowed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onRetryLevel = onRetryLevel,
            onNextLevel = onNextLevel,
            onGameHub = onGameHub,
        )
    }
}

/** The normal game uses every available pixel for a square board; expanded states may scroll. */
@Composable
private fun CompactSudokuGameplay(
    game: SudokuGameState,
    selectedCell: SudokuPosition?,
    isPencilMode: Boolean,
    difficultyLabel: String,
    levelNumber: Int?,
    mistakesUsed: Int,
    inputEnabled: Boolean,
    onSelectCell: (SudokuPosition) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = LogicaSpacing.gameplayHorizontal, vertical = LogicaSpacing.text),
    ) {
        val boardSize = minOf(maxWidth, (maxHeight - COMPACT_CONTROLS_HEIGHT).coerceAtLeast(0.dp))
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SudokuGameplayContent(
                game = game,
                selectedCell = selectedCell,
                isPencilMode = isPencilMode,
                difficultyLabel = difficultyLabel,
                levelNumber = levelNumber,
                mistakesUsed = mistakesUsed,
                inputEnabled = inputEnabled,
                onSelectCell = onSelectCell,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onDigit = onDigit,
                hintEnabled = true,
                boardModifier = Modifier.size(boardSize),
            )
        }
    }
}

@Composable
private fun SudokuGameplayContent(
    game: SudokuGameState,
    selectedCell: SudokuPosition?,
    isPencilMode: Boolean,
    difficultyLabel: String,
    levelNumber: Int?,
    mistakesUsed: Int,
    inputEnabled: Boolean,
    onSelectCell: (SudokuPosition) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onDigit: (Int) -> Unit,
    hintEnabled: Boolean,
    economy: PlayerEconomy? = null,
    onRestoreLife: (() -> Unit)? = null,
    boardModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameHeaderBadges(difficultyLabel, levelNumber)
        MistakeIndicator(mistakesUsed, SudokuGameState.MAX_MISTAKES)
    }
    if (economy != null && onRestoreLife != null) ZeroLivesCard(economy, onRestoreLife)
    SudokuBoard(
        game = game,
        selectedCell = selectedCell,
        enabled = economy?.isGameplayAllowed ?: true,
        onCellSelected = onSelectCell,
        modifier = boardModifier,
    )
    SudokuToolBar(
        isPencilMode = isPencilMode,
        onToggle = onTogglePencil,
        onHint = onHint,
        hintEnabled = hintEnabled,
        enabled = economy?.isGameplayAllowed ?: true,
    )
    SudokuNumberPad(enabled = inputEnabled, onDigit = onDigit)
    game.currentHint?.let { SudokuHintCard(it) }
}

@Composable
private fun SudokuHintCard(hint: SudokuHint) {
    LogicaCard(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        verticalSpacing = LogicaSpacing.text,
    ) {
        BodyText(hint.presentationText())
    }
}

@Composable
private fun SudokuHint.presentationText(): String =
    when (technique) {
        SudokuHintTechnique.NAKED_SINGLE ->
            stringResource(R.string.sudoku_hint_naked_single, position.row + 1, position.column + 1, value)
        SudokuHintTechnique.HIDDEN_SINGLE_ROW ->
            stringResource(R.string.sudoku_hint_hidden_row, value, checkNotNull(unitIndex) + 1, position.column + 1)
        SudokuHintTechnique.HIDDEN_SINGLE_COLUMN ->
            stringResource(R.string.sudoku_hint_hidden_column, value, checkNotNull(unitIndex) + 1, position.row + 1)
        SudokuHintTechnique.HIDDEN_SINGLE_BLOCK ->
            stringResource(
                R.string.sudoku_hint_hidden_block,
                value,
                checkNotNull(unitIndex) + 1,
                position.row + 1,
                position.column + 1,
            )
        SudokuHintTechnique.FALLBACK_REVEAL ->
            stringResource(R.string.sudoku_hint_fallback, position.row + 1, position.column + 1, value)
    }

@Composable
private fun SudokuGameError.message(): String =
    stringResource(
        when (this) {
            SudokuGameError.LEVEL_UNAVAILABLE -> R.string.level_content_error
            SudokuGameError.MISSING_DATASET -> R.string.sudoku_dataset_missing
            SudokuGameError.CORRUPT_DATASET -> R.string.sudoku_dataset_corrupt
            SudokuGameError.PUZZLE_NOT_FOUND -> R.string.sudoku_puzzle_missing
        },
    )

private fun SudokuDifficulty.toDifficulty(): Difficulty = Difficulty.valueOf(name)

private val COMPACT_CONTROLS_HEIGHT = 144.dp

@Preview(name = "Sudoku", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun SudokuGamePreview() {
    val puzzle = previewPuzzle()
    val engine = SudokuGameEngine(puzzle)
    var game = engine.start()
    game = engine.toggleCandidate(game, SudokuPosition(0, 2), 1)
    game = engine.toggleCandidate(game, SudokuPosition(0, 2), 2)
    game = engine.placeValue(game, SudokuPosition(0, 0), 1)
    game = engine.placeValue(game, SudokuPosition(0, 4), 9)
    LogicaTheme(ThemeMode.LIGHT) {
        SudokuGameScreen(
            uiState = SudokuGameUiState.Ready(puzzle, game, selectedCell = SudokuPosition(0, 4)),
            economy = PlayerEconomy(),
            isDaily = false,
            levelNumber = 12,
            onSelectCell = {},
            onDigit = {},
            onTogglePencil = {},
            onHint = {},
            onRetryLevel = {},
            onRetryCompletion = {},
            onRetryLoad = {},
            onRestoreLife = {},
            hapticsEnabled = true,
            onBack = {},
            onNextLevel = {},
            onGameHub = {},
        )
    }
}

internal fun previewSudokuPuzzle(): SudokuPuzzle =
    SudokuPuzzle(
        id =
            SudokuPuzzleId(
                SudokuDatasetVersion.V1,
                SudokuDifficulty.EASY,
                "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d",
            ),
        givens = "050703060007000800000816000000030000005000100730040086906000204840572093000409000",
        solution = "158723469367954821294816375619238547485697132732145986976381254841572693523469718",
        upstreamRatingTenths = 12,
    )

private fun previewPuzzle(): SudokuPuzzle = previewSudokuPuzzle()
