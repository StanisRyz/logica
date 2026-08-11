package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
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
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.sudoku.SudokuGameContext
import com.stanisryz.logica.sudoku.SudokuGameError
import com.stanisryz.logica.sudoku.SudokuGameLaunch
import com.stanisryz.logica.sudoku.SudokuGameUiState
import com.stanisryz.logica.sudoku.SudokuGameViewModel
import com.stanisryz.logica.sudoku.SudokuGameViewModelFactory
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.GameAction
import com.stanisryz.logica.ui.components.GameActionBar
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.PuzzleTerminalDialog
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.sudoku.SudokuBoard
import com.stanisryz.logica.ui.sudoku.SudokuNumberPad
import com.stanisryz.logica.ui.sudoku.SudokuPencilToggle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun SudokuGameRoute(
    launch: SudokuGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val factory =
        remember(launch, assets, sessionRepository, completionRepository, economyRepository) {
            SudokuGameViewModelFactory(
                launch,
                assets,
                sessionRepository,
                completionRepository,
                economyRepository,
            )
        }
    val gameViewModel: SudokuGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    SudokuGameScreen(
        uiState = uiState,
        economy = economy,
        isDaily = launch.context is SudokuGameContext.Daily,
        onSelectCell = gameViewModel::selectCell,
        onDigit = gameViewModel::inputDigit,
        onTogglePencil = gameViewModel::togglePencilMode,
        onHint = gameViewModel::requestHint,
        onRetry = gameViewModel::retry,
        onRetryCompletion = gameViewModel::retryCompletion,
        onRetryLoad = gameViewModel::reload,
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onStartNew = onStartNew,
        onGameHub = onGameHub,
        modifier = modifier,
    )
}

@Composable
private fun SudokuGameScreen(
    uiState: SudokuGameUiState,
    economy: PlayerEconomy,
    isDaily: Boolean,
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetry: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRetryLoad: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
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
                retryLabel =
                    stringResource(
                        when {
                            canReload -> R.string.retry
                            isDaily -> R.string.to_games
                            else -> R.string.try_another
                        },
                    ),
                onRetry =
                    when {
                        canReload -> onRetryLoad
                        isDaily -> onGameHub
                        else -> onStartNew
                    },
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
                onSelectCell = onSelectCell,
                onDigit = onDigit,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onRetry = onRetry,
                onRetryCompletion = onRetryCompletion,
                onRestoreLife = onRestoreLife,
                hapticsEnabled = hapticsEnabled,
                onNewPuzzle = {
                    onNewPuzzle(
                        uiState.puzzle.id.difficulty
                            .toDifficulty(),
                    )
                },
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
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetry: () -> Unit,
    onRetryCompletion: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
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
    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleTitle(stringResource(R.string.sudoku), puzzleType = PuzzleType.SUDOKU)
        DifficultyBadge(
            difficultyLabel(
                PuzzleType.SUDOKU,
                uiState.puzzle.id.difficulty
                    .toDifficulty(),
            ),
        )
        MistakeIndicator(game.mistakesUsed, SudokuGameState.MAX_MISTAKES)
        ZeroLivesCard(economy, onRestoreLife)
        SudokuBoard(
            game = game,
            selectedCell = uiState.selectedCell,
            enabled = economy.isGameplayAllowed,
            onCellSelected = {
                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSelectCell(it)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SudokuPencilToggle(
                isPencilMode = uiState.isPencilMode,
                enabled = game.status == SudokuGameStatus.IN_PROGRESS && economy.isGameplayAllowed,
                onToggle = onTogglePencil,
            )
            Text(
                text =
                    stringResource(
                        if (uiState.isPencilMode) R.string.sudoku_pencil_on_help else R.string.sudoku_select_cell_help,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        SudokuNumberPad(enabled = inputEnabled, onDigit = onDigit)
        game.currentHint?.let { SudokuHintCard(it) }
        GameActionBar(
            listOf(
                GameAction(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.hint),
                    enabled = game.status == SudokuGameStatus.IN_PROGRESS && economy.isGameplayAllowed,
                    onClick = onHint,
                ),
            ),
        )
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
            onRetryPuzzle = onRetry,
            onNewPuzzle = onNewPuzzle,
            onGameHub = onGameHub,
            // A solved Daily entry is finished for the day; only the catalog replays the same puzzle.
            preferSamePuzzleRetry = !isDaily,
        )
    }
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
            SudokuGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
            SudokuGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
            SudokuGameError.MISSING_DATASET -> R.string.sudoku_dataset_missing
            SudokuGameError.CORRUPT_DATASET -> R.string.sudoku_dataset_corrupt
            SudokuGameError.PUZZLE_NOT_FOUND -> R.string.sudoku_puzzle_missing
        },
    )

private fun SudokuDifficulty.toDifficulty(): Difficulty = Difficulty.valueOf(name)

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
            onSelectCell = {},
            onDigit = {},
            onTogglePencil = {},
            onHint = {},
            onRetry = {},
            onRetryCompletion = {},
            onRetryLoad = {},
            onRestoreLife = {},
            hapticsEnabled = true,
            onBack = {},
            onNewPuzzle = {},
            onStartNew = {},
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
