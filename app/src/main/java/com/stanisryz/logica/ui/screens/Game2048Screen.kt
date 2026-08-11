package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.game2048.Game2048UiState
import com.stanisryz.logica.game2048.Game2048ViewModel
import com.stanisryz.logica.game2048.Game2048ViewModelFactory
import com.stanisryz.logica.puzzle.core.game2048.EncodedGame2048Session
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.Metric
import com.stanisryz.logica.ui.components.MetricGrid
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.russianLabel
import com.stanisryz.logica.ui.game2048.Game2048Board
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun Game2048Route(
    puzzleId: Game2048PuzzleId,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    restoredSession: EncodedGame2048Session? = null,
) {
    val factory = remember(puzzleId, restoredSession) { Game2048ViewModelFactory(puzzleId, restoredSession) }
    val gameViewModel: Game2048ViewModel =
        viewModel(
            key =
                "2048:${puzzleId.seed.value}:${puzzleId.difficulty.name}:" +
                    puzzleId.generatorVersion.value,
            factory = factory,
        )
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    Game2048Screen(
        uiState = uiState,
        onMove = gameViewModel::move,
        onRetry = gameViewModel::retry,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun Game2048Screen(
    uiState: Game2048UiState,
    onMove: (Game2048Direction) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = uiState.game
    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        PuzzleTitle(stringResource(R.string.game_2048_title))
        DifficultyBadge(game.puzzleId.difficulty.russianLabel())
        MetricGrid(
            listOf(
                Metric(stringResource(R.string.game_2048_target), game.puzzleId.targetTile.toString()),
                Metric(stringResource(R.string.game_2048_score), game.score.toString()),
            ),
        )
        Game2048Board(game = game, onMove = onMove)
        Text(
            text = stringResource(R.string.game_2048_swipe_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (game.status.isTerminal) {
        Game2048TerminalDialog(game, onRetry, onClose)
    }
}

@Composable
private fun Game2048TerminalDialog(
    game: Game2048State,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val solved = game.status == Game2048Status.SOLVED
    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            Icon(
                imageVector = if (solved) Icons.Filled.TaskAlt else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (solved) LocalLogicaPalette.current.success else MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                stringResource(
                    if (solved) R.string.game_2048_solved_title else R.string.game_2048_failed_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    stringResource(
                        if (solved) R.string.game_2048_solved_body else R.string.game_2048_failed_body,
                        if (solved) game.puzzleId.targetTile else game.maximumTile,
                    ),
                )
                Text(stringResource(R.string.game_2048_final_score, game.score))
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_puzzle)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}

@Preview(name = "2048", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun Game2048Preview() {
    val puzzleId = Game2048PuzzleId(PuzzleSeed(36L), Difficulty.MEDIUM)
    val engine = Game2048Engine(puzzleId)
    LogicaTheme(ThemeMode.LIGHT) {
        Game2048Screen(
            uiState = Game2048UiState(engine.start()),
            onMove = {},
            onRetry = {},
            onClose = {},
        )
    }
}
