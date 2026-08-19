package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.puzzle.core.sudoku.toPlatformDifficulty
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.sudoku.SudokuGameError
import com.stanisryz.logica.sudoku.SudokuGameUiState
import com.stanisryz.logica.sudoku.SudokuGameViewModel
import com.stanisryz.logica.sudoku.SudokuGameViewModelFactory
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.sudoku.SudokuGameContent

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

    val gameplayEnabled = game.status == SudokuGameStatus.IN_PROGRESS && economy.isGameplayAllowed
    val selectedStatus = uiState.selectedCell?.let(game::cellAt)?.status
    val inputEnabled =
        gameplayEnabled &&
            if (uiState.isPencilMode) {
                selectedStatus == SudokuCellStatus.EMPTY
            } else {
                selectedStatus == SudokuCellStatus.EMPTY || selectedStatus == SudokuCellStatus.INCORRECT
            }

    SudokuGameContent(
        puzzle = uiState.puzzle,
        game = game,
        selectedCell = uiState.selectedCell,
        isPencilMode = uiState.isPencilMode,
        levelNumber = levelNumber,
        gameplayEnabled = gameplayEnabled,
        inputEnabled = inputEnabled,
        onCellSelected = { position ->
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onSelectCell(position)
        },
        onDigit = onDigit,
        onTogglePencil = onTogglePencil,
        onHint = onHint,
        modifier = modifier,
        hostStatusContent = {
            ZeroLivesCard(economy, onRestoreLife)
        },
    )

    if (game.status.isTerminal) {
        PuzzleTerminalDialog(
            isSolved = game.status == SudokuGameStatus.SOLVED,
            completionPersistence = uiState.completionPersistence,
            hintsUsed = game.hintsUsed,
            maxMistakes = SudokuGameState.MAX_MISTAKES,
            lives = economy.lives,
            difficulty =
                uiState.puzzle.id.difficulty
                    .toPlatformDifficulty(),
            isRetryAllowed = economy.isGameplayAllowed,
            isDaily = isDaily,
            onRetryCompletion = onRetryCompletion,
            onRetryLevel = onRetryLevel,
            onNextLevel = onNextLevel,
            onGameHub = onGameHub,
        )
    }
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
