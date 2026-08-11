package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
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
import com.stanisryz.logica.settings.ThemeMode
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
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.sudoku.SudokuBoard
import com.stanisryz.logica.ui.sudoku.SudokuNumberPad
import com.stanisryz.logica.ui.sudoku.SudokuPencilToggle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

/** Standalone Stage 34 route. It is intentionally not registered in production navigation yet. */
@Composable
internal fun SudokuGameRoute(
    launch: SudokuGameLaunch,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val factory = remember(launch, assets) { SudokuGameViewModelFactory(launch, assets) }
    val gameViewModel: SudokuGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    SudokuGameScreen(
        uiState = uiState,
        onSelectCell = gameViewModel::selectCell,
        onDigit = gameViewModel::inputDigit,
        onTogglePencil = gameViewModel::togglePencilMode,
        onHint = gameViewModel::requestHint,
        onRetry = gameViewModel::retry,
        onRetryLoad = gameViewModel::reload,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
private fun SudokuGameScreen(
    uiState: SudokuGameUiState,
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetry: () -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        SudokuGameUiState.Loading -> LoadingState(modifier, stringResource(R.string.sudoku_loading))
        is SudokuGameUiState.Error ->
            RetryableErrorState(
                message = uiState.reason.message(),
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetryLoad,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.close),
                onSecondary = onClose,
            )
        is SudokuGameUiState.Ready ->
            SudokuReadyState(
                uiState = uiState,
                onSelectCell = onSelectCell,
                onDigit = onDigit,
                onTogglePencil = onTogglePencil,
                onHint = onHint,
                onRetry = onRetry,
                onClose = onClose,
                modifier = modifier,
            )
    }
}

@Composable
private fun SudokuReadyState(
    uiState: SudokuGameUiState.Ready,
    onSelectCell: (SudokuPosition) -> Unit,
    onDigit: (Int) -> Unit,
    onTogglePencil: () -> Unit,
    onHint: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = uiState.game
    val selectedStatus = uiState.selectedCell?.let(game::cellAt)?.status
    val inputEnabled =
        game.status == SudokuGameStatus.IN_PROGRESS &&
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
        PuzzleTitle(stringResource(R.string.sudoku))
        DifficultyBadge(
            uiState.puzzle.id.difficulty
                .label(),
        )
        MistakeIndicator(game.mistakesUsed, SudokuGameState.MAX_MISTAKES)
        SudokuBoard(game, uiState.selectedCell, onSelectCell)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SudokuPencilToggle(
                isPencilMode = uiState.isPencilMode,
                enabled = game.status == SudokuGameStatus.IN_PROGRESS,
                onToggle = onTogglePencil,
            )
            Text(
                text =
                    stringResource(
                        if (uiState.isPencilMode) R.string.sudoku_pencil_on_help else R.string.sudoku_select_cell_help,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SudokuNumberPad(enabled = inputEnabled, onDigit = onDigit)
        game.currentHint?.let { SudokuHintCard(it) }
        GameActionBar(
            listOf(
                GameAction(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.hint),
                    enabled = game.status == SudokuGameStatus.IN_PROGRESS,
                    onClick = onHint,
                ),
            ),
        )
    }
    if (game.status.isTerminal) {
        SudokuTerminalDialog(game, onRetry, onClose)
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
private fun SudokuTerminalDialog(
    game: SudokuGameState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val solved = game.status == SudokuGameStatus.SOLVED
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(if (solved) R.string.puzzle_solved else R.string.puzzle_failed)) },
        text = {
            Text(
                if (solved) {
                    stringResource(R.string.hints_used, game.hintsUsed)
                } else {
                    stringResource(R.string.puzzle_failed_body, SudokuGameState.MAX_MISTAKES)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_puzzle)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
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
            SudokuGameError.MISSING_DATASET -> R.string.sudoku_dataset_missing
            SudokuGameError.CORRUPT_DATASET -> R.string.sudoku_dataset_corrupt
            SudokuGameError.PUZZLE_NOT_FOUND -> R.string.sudoku_puzzle_missing
            SudokuGameError.INVALID_SESSION -> R.string.sudoku_session_invalid
        },
    )

@Composable
private fun SudokuDifficulty.label(): String =
    stringResource(
        when (this) {
            SudokuDifficulty.EASY -> R.string.difficulty_easy
            SudokuDifficulty.MEDIUM -> R.string.difficulty_medium
            SudokuDifficulty.HARD -> R.string.difficulty_hard
            SudokuDifficulty.EXPERT -> R.string.difficulty_expert
        },
    )

@Preview(name = "Sudoku standalone", widthDp = 360, heightDp = 800, showBackground = true)
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
            onSelectCell = {},
            onDigit = {},
            onTogglePencil = {},
            onHint = {},
            onRetry = {},
            onRetryLoad = {},
            onClose = {},
        )
    }
}

private fun previewPuzzle(): SudokuPuzzle =
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
