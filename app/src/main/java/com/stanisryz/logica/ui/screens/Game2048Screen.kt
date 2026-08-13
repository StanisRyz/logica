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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.catalog.levelNumberOrNull
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.game2048.Game2048GameError
import com.stanisryz.logica.game2048.Game2048UiState
import com.stanisryz.logica.game2048.Game2048ViewModel
import com.stanisryz.logica.game2048.Game2048ViewModelFactory
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.settings.ThemeMode
import com.stanisryz.logica.ui.components.EconomyResultFeedback
import com.stanisryz.logica.ui.components.GameHeaderBadges
import com.stanisryz.logica.ui.components.GameplayExitGuard
import com.stanisryz.logica.ui.components.LeaveLevelGuard
import com.stanisryz.logica.ui.components.LoadingState
import com.stanisryz.logica.ui.components.Metric
import com.stanisryz.logica.ui.components.MetricGrid
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.RetryableErrorState
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.StatusChip
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.russianLabel
import com.stanisryz.logica.ui.game2048.Game2048Board
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.theme.LogicaTheme

@Composable
internal fun Game2048Route(
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
    val factory =
        remember(launch, attemptFactory, completionRepository, economyRepository) {
            Game2048ViewModelFactory(launch, attemptFactory, completionRepository, economyRepository)
        }
    val gameViewModel: Game2048ViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val economy by gameViewModel.economy.collectAsStateWithLifecycle()
    val ready = uiState as? Game2048UiState.Ready
    val backBehavior = game2048BackBehavior(launch, ready)
    LeaveLevelGuard(
        guard = exitGuard,
        hasProgress = ready?.hasMeaningfulProgress == true,
        exitBlocked = backBehavior == Game2048BackBehavior.BLOCKED_SAVING,
        onDurableCompletionExit =
            onTerminalAction.takeIf { backBehavior == Game2048BackBehavior.DEFERRED_TERMINAL_ACTION },
    )
    Game2048Screen(
        uiState = uiState,
        economy = economy,
        isDaily = launch is GameAttemptLaunch.Daily,
        levelNumber = launch.levelNumberOrNull(),
        onMove = gameViewModel::move,
        onMotionFinished = gameViewModel::finishMotion,
        onRetryLevel = { onTerminalAction(gameViewModel::retry) },
        onRetryCompletion = gameViewModel::retryCompletion,
        onBack = onBack,
        onNextLevel = { onTerminalAction(onNextLevel) },
        onGameHub = { onTerminalAction(onGameHub) },
        onRestoreLife = onRestoreLife,
        hapticsEnabled = hapticsEnabled,
        modifier = modifier,
    )
}

/** Only a durably cleared Catalog freeplay turns Back into an eligible terminal action. */
internal fun game2048BackBehavior(
    launch: GameAttemptLaunch,
    ready: Game2048UiState.Ready?,
): Game2048BackBehavior =
    when {
        ready?.levelCleared == true && ready.completionPersistence == CompletionPersistence.Saving ->
            Game2048BackBehavior.BLOCKED_SAVING
        launch is GameAttemptLaunch.Level &&
            ready?.levelCleared == true &&
            ready.completionPersistence == CompletionPersistence.Saved ->
            Game2048BackBehavior.DEFERRED_TERMINAL_ACTION
        else -> Game2048BackBehavior.NORMAL
    }

internal enum class Game2048BackBehavior {
    NORMAL,
    BLOCKED_SAVING,
    DEFERRED_TERMINAL_ACTION,
}

@Composable
private fun Game2048Screen(
    uiState: Game2048UiState,
    economy: PlayerEconomy,
    isDaily: Boolean,
    levelNumber: Int?,
    onMove: (Game2048Direction) -> Unit,
    onMotionFinished: (Long) -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onBack: () -> Unit,
    onNextLevel: () -> Unit,
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
                retryLabel = stringResource(R.string.to_games),
                onRetry = onGameHub,
                modifier = modifier,
                secondaryLabel = stringResource(R.string.back),
                onSecondary = onBack,
            )
        is Game2048UiState.Ready ->
            Game2048ReadyState(
                uiState = uiState,
                economy = economy,
                isDaily = isDaily,
                levelNumber = levelNumber,
                onMove = onMove,
                onMotionFinished = onMotionFinished,
                onRetryLevel = onRetryLevel,
                onRetryCompletion = onRetryCompletion,
                onNextLevel = onNextLevel,
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
    isDaily: Boolean,
    levelNumber: Int?,
    onMove: (Game2048Direction) -> Unit,
    onMotionFinished: (Long) -> Unit,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
    onRestoreLife: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier,
) {
    val game = uiState.game
    val motionEvent = uiState.motionEvent
    val view = LocalView.current
    LaunchedEffect(motionEvent?.revision) {
        if (hapticsEnabled && motionEvent != null && !game.status.isTerminal) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PuzzleTitle(stringResource(R.string.game_2048_title), puzzleType = PuzzleType.GAME_2048)
        // The gameplay badge is the plain difficulty plus the level: what the goal actually is lives
        // in the metrics below, because a V1 attempt targets a tile while a V2 attempt targets a score.
        GameHeaderBadges(game.puzzleId.difficulty.russianLabel(), levelNumber)
        MetricGrid(
            listOf(
                Metric(stringResource(R.string.game_2048_target), game.targetMetricValue()),
                Metric(stringResource(R.string.game_2048_score), formatGame2048Number(game.score)),
            ),
        )
        // A cleared Catalog level stays visible while freeplay continues; it never blocks the board.
        if (uiState.levelCleared) {
            StatusChip(
                icon = Icons.Filled.TaskAlt,
                label =
                    levelNumber
                        ?.let { stringResource(R.string.game_2048_level_cleared, it) }
                        ?: stringResource(R.string.game_2048_solved_title),
                containerColor = LocalLogicaPalette.current.successContainer,
                contentColor = LocalLogicaPalette.current.onSuccessContainer,
            )
            Text(
                text = stringResource(R.string.game_2048_level_cleared_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (uiState.completionPersistence) {
                CompletionPersistence.Saving -> Text(stringResource(R.string.saving_completion))
                CompletionPersistence.Error -> {
                    Text(stringResource(R.string.completion_save_error_body))
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                }
                CompletionPersistence.NotRequired,
                CompletionPersistence.Saved,
                -> Unit
            }
        }
        ZeroLivesCard(economy, onRestoreLife)
        Game2048Board(
            game = game,
            motionEvent = motionEvent,
            onMove = onMove,
            onMotionFinished = { revision ->
                if (hapticsEnabled && game.status.isTerminal) {
                    view.performHapticFeedback(
                        if (game.status == Game2048Status.SOLVED) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.REJECT
                        },
                    )
                }
                onMotionFinished(revision)
            },
            inputEnabled = economy.isGameplayAllowed,
        )
        Text(
            text = stringResource(R.string.game_2048_swipe_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (game.status.isTerminal && motionEvent == null) {
        Game2048TerminalDialog(
            game = game,
            completionPersistence = uiState.completionPersistence,
            economy = economy,
            isDaily = isDaily,
            levelCleared = uiState.levelCleared,
            onRetryLevel = onRetryLevel,
            onRetryCompletion = onRetryCompletion,
            onNextLevel = onNextLevel,
            onGameHub = onGameHub,
        )
    }
}

@Composable
private fun Game2048TerminalDialog(
    game: Game2048State,
    completionPersistence: CompletionPersistence,
    economy: PlayerEconomy,
    isDaily: Boolean,
    levelCleared: Boolean,
    onRetryLevel: () -> Unit,
    onRetryCompletion: () -> Unit,
    onNextLevel: () -> Unit,
    onGameHub: () -> Unit,
) {
    // A Catalog level that was already cleared at its score target is never a failure afterwards,
    // however the freeplay board ends.
    val solved = levelCleared || game.status == Game2048Status.SOLVED
    val isSaved = completionPersistence == CompletionPersistence.Saved
    // A solved Daily entry is done for the day: the only way on is back to the hub.
    val isDailySolved = isDaily && solved
    val targetScore = game.puzzleId.rules.targetScore
    val targetTile = game.puzzleId.rules.targetTile
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
            Text(
                stringResource(
                    when {
                        levelCleared -> R.string.game_2048_cleared_title
                        solved -> R.string.game_2048_solved_title
                        else -> R.string.game_2048_failed_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    // A V2 attempt is judged on its final score; a restored V1 attempt still reports
                    // the target tile it was actually playing for.
                    if (levelCleared) {
                        stringResource(R.string.game_2048_cleared_body)
                    } else if (targetScore != null) {
                        stringResource(
                            if (solved) R.string.game_2048_solved_body_score else R.string.game_2048_failed_body_score,
                            formatGame2048Number(targetScore),
                        )
                    } else {
                        stringResource(
                            if (solved) R.string.game_2048_solved_body else R.string.game_2048_failed_body,
                            if (solved) requireNotNull(targetTile) else game.maximumTile,
                        )
                    },
                )
                Text(stringResource(R.string.game_2048_final_score, formatGame2048Number(game.score)))
                when (completionPersistence) {
                    CompletionPersistence.Error -> Text(stringResource(R.string.completion_save_error_body))
                    CompletionPersistence.Saving -> Text(stringResource(R.string.saving_completion))
                    CompletionPersistence.Saved ->
                        EconomyResultFeedback(solved, economy.lives, game.puzzleId.difficulty)
                    CompletionPersistence.NotRequired -> Unit
                }
            }
        },
        confirmButton = {
            when {
                completionPersistence == CompletionPersistence.Error ->
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                completionPersistence != CompletionPersistence.Saved ->
                    TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                isDailySolved -> TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
                solved -> TextButton(onClick = onNextLevel) { Text(stringResource(R.string.next_level)) }
                else ->
                    TextButton(onClick = onRetryLevel, enabled = economy.isGameplayAllowed) {
                        Text(stringResource(R.string.retry_puzzle))
                    }
            }
        },
        dismissButton = {
            if (isSaved && !isDailySolved) TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
        },
    )
}

/**
 * The goal tile for a restored V1 attempt, or the V2 score target. Once a V2 target is reached the
 * value is marked as met, which is informational only: the game keeps running until the last move.
 */
@Composable
private fun Game2048State.targetMetricValue(): String {
    val targetScore = puzzleId.rules.targetScore ?: return requireNotNull(puzzleId.rules.targetTile).toString()
    val target = formatGame2048Number(targetScore)
    return if (goalReached) stringResource(R.string.game_2048_target_reached, target) else target
}

/** Grouped thousands, so a six-digit score stays readable at a glance. */
private fun formatGame2048Number(value: Long): String =
    value
        .toString()
        .reversed()
        .chunked(GROUP_SIZE)
        .joinToString(GROUP_SEPARATOR)
        .reversed()

@Composable
private fun Game2048GameError.message(): String =
    stringResource(
        when (this) {
            Game2048GameError.LEVEL_UNAVAILABLE -> R.string.level_content_error
            Game2048GameError.START -> R.string.game_2048_start_error
            Game2048GameError.NO_LIVES -> R.string.economy_no_lives_short
        },
    )

private const val GROUP_SIZE = 3
private const val GROUP_SEPARATOR = "\u00A0"

@Preview(name = "2048", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun Game2048Preview() {
    val puzzleId = Game2048PuzzleId(PuzzleSeed(36L), Difficulty.MEDIUM, Game2048GeneratorVersion.V2)
    val engine = Game2048Engine(puzzleId)
    LogicaTheme(ThemeMode.LIGHT) {
        Game2048Screen(
            uiState = Game2048UiState.Ready(engine.start()),
            economy = PlayerEconomy(),
            isDaily = false,
            levelNumber = 7,
            onMove = {},
            onMotionFinished = {},
            onRetryLevel = {},
            onRetryCompletion = {},
            onBack = {},
            onNextLevel = {},
            onGameHub = {},
            onRestoreLife = {},
            hapticsEnabled = true,
        )
    }
}
