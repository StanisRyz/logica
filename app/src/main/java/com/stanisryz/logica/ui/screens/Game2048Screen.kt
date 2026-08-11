package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.game2048.Game2048GameError
import com.stanisryz.logica.game2048.Game2048Launch
import com.stanisryz.logica.game2048.Game2048UiState
import com.stanisryz.logica.game2048.Game2048ViewModel
import com.stanisryz.logica.game2048.Game2048ViewModelFactory
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.ui.components.DifficultyBadge
import com.stanisryz.logica.ui.components.EconomyResultFeedback
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.Metric
import com.stanisryz.logica.ui.components.MetricGrid
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.difficultyLabel
import com.stanisryz.logica.ui.game2048.Game2048Board
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun Game2048Route(
    launch: Game2048Launch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    economyRepository: EconomyRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository, economyRepository) {
            Game2048ViewModelFactory(launch, sessionRepository, completionRepository, economyRepository)
        }
    val gameViewModel: Game2048ViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    Game2048Screen(
        uiState = uiState,
        economy = economy,
        onMove = gameViewModel::move,
        onRetry = gameViewModel::retry,
        onRetryCompletion = gameViewModel::retryCompletion,
        onBack = onBack,
        onStartNew = onStartNew,
        onGameHub = onGameHub,
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        modifier = modifier,
    )
}

@Composable
private fun Game2048Screen(
    uiState: Game2048UiState,
    economy: PlayerEconomy,
    onMove: (Game2048Direction) -> Unit,
    onRetry: () -> Unit,
    onRetryCompletion: () -> Unit,
    onBack: () -> Unit,
    onStartNew: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        Game2048UiState.Loading -> LoadingState(modifier, stringResource(R.string.game_2048_loading))
        is Game2048UiState.Error ->
            RetryableErrorState(
                message = uiState.reason.message(),
                retryLabel = stringResource(R.string.new_game),
                onRetry = onStartNew,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is Game2048UiState.Ready ->
            Game2048ReadyState(
                uiState = uiState,
                economy = economy,
                onMove = onMove,
                onRetry = onRetry,
                onRetryCompletion = onRetryCompletion,
                onGameHub = onGameHub,
                onRestoreLife = onRestoreLife,
                hapticsEnabled = hapticsEnabled,
                modifier = modifier,
            )
    }
}

@Composable
private fun Game2048ReadyState(
    uiState: Game2048UiState.Ready,
    economy: PlayerEconomy,
    onMove: (Game2048Direction) -> Unit,
    onRetry: () -> Unit,
    onRetryCompletion: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier,
) {
    val game = uiState.game
    val view = LocalView.current
    var previousGame by remember(game.puzzleId) { mutableStateOf(game) }
    LaunchedEffect(game) {
        if (hapticsEnabled && game != previousGame) {
            when {
                game.status == Game2048Status.SOLVED -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                game.status == Game2048Status.FAILED -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                game.board != previousGame.board -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        previousGame = game
    }

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleTitle(stringResource(R.string.game_2048_title), puzzleType = PuzzleType.GAME_2048)
        DifficultyBadge(difficultyLabel(PuzzleType.GAME_2048, game.puzzleId.difficulty))
        MetricGrid(
            listOf(
                Metric(stringResource(R.string.game_2048_target), game.puzzleId.targetTile.toString()),
                Metric(stringResource(R.string.game_2048_score), game.score.toString()),
            ),
        )
        ZeroLivesCard(economy, onRestoreLife)
        Game2048Board(
            game = game,
            onMove = onMove,
            inputEnabled = economy.isGameplayAllowed,
        )
        Text(
            text = stringResource(R.string.game_2048_swipe_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (game.status.isTerminal) {
        Game2048TerminalDialog(
            game = game,
            completionPersistence = uiState.completionPersistence,
            economy = economy,
            onRetry = onRetry,
            onRetryCompletion = onRetryCompletion,
            onGameHub = onGameHub,
        )
    }
}

@Composable
private fun Game2048TerminalDialog(
    game: Game2048State,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    onRetry: () -> Unit,
    onRetryCompletion: () -> Unit,
    onGameHub: () -> Unit,
) {
    val solved = game.status == Game2048Status.SOLVED
    val isSaved = completionPersistence == CompletionPersistence.Saved
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = if (solved) Icons.Filled.TaskAlt else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (solved) LocalLogicaPalette.current.success else MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(stringResource(if (solved) R.string.game_2048_solved_title else R.string.game_2048_failed_title))
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
                when (completionPersistence) {
                    CompletionPersistence.Error -> Text(stringResource(R.string.completion_save_error_body))
                    CompletionPersistence.Saving -> Text(stringResource(R.string.saving_completion))
                    CompletionPersistence.Saved -> EconomyResultFeedback(solved, economy.lives)
                    CompletionPersistence.NotRequired -> Unit
                }
            }
        },
        confirmButton = {
            when (completionPersistence) {
                CompletionPersistence.Error ->
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                CompletionPersistence.Saved ->
                    TextButton(onClick = onRetry, enabled = economy.isGameplayAllowed) {
                        Text(stringResource(R.string.retry_puzzle))
                    }
                else -> TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
            }
        },
        dismissButton = {
            if (isSaved) TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
        },
    )
}

@Composable
private fun Game2048GameError.message(): String =
    stringResource(
        when (this) {
            Game2048GameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
            Game2048GameError.INVALID_SAVED_SESSION -> R.string.game_2048_session_invalid
            Game2048GameError.NO_LIVES -> R.string.economy_no_lives_short
        },
    )

@Preview(name = "2048", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun Game2048Preview() {
    val puzzleId = Game2048PuzzleId(PuzzleSeed(36L), Difficulty.MEDIUM)
    val engine = Game2048Engine(puzzleId)
    LogicaTheme(ThemeMode.LIGHT) {
        Game2048Screen(
            uiState = Game2048UiState.Ready(engine.start()),
            economy = PlayerEconomy(),
            onMove = {},
            onRetry = {},
            onRetryCompletion = {},
            onBack = {},
            onStartNew = {},
            onGameHub = {},
            onRestoreLife = {},
            hapticsEnabled = true,
        )
    }
}
