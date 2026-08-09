package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.crowns.CrownsGameContext
import com.stanisryz.logica.crowns.CrownsGameError
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.crowns.CrownsGameUiState
import com.stanisryz.logica.crowns.CrownsGameViewModel
import com.stanisryz.logica.crowns.CrownsGameViewModelFactory
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsHint
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintAction
import com.stanisryz.logica.puzzle.core.crowns.CrownsLogicTechnique
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.CrownsViolationType
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.ui.crowns.CrownsBoard

@Composable
internal fun CrownsGameRoute(
    launch: CrownsGameLaunch,
    sessionRepository: GameSessionRepository,
    completionRepository: GameCompletionRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, completionRepository) {
            CrownsGameViewModelFactory(launch, sessionRepository, completionRepository)
        }
    val gameViewModel: CrownsGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()

    CrownsGameScreen(
        uiState = uiState,
        onCellTapped = gameViewModel::onCellTapped,
        onUndo = gameViewModel::undo,
        onHint = gameViewModel::requestHint,
        onReset = gameViewModel::reset,
        onRetryCompletion = gameViewModel::retryCompletion,
        hapticsEnabled = hapticsEnabled,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onStartNew = onStartNew,
        onCatalog = onCatalog,
        onToday = onToday,
        isDaily = launch.context is CrownsGameContext.Daily,
        modifier = modifier,
    )
}

@Composable
private fun CrownsGameScreen(
    uiState: CrownsGameUiState,
    onCellTapped: (CrownsPosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    when (uiState) {
        CrownsGameUiState.Loading -> CrownsLoadingState(modifier)
        is CrownsGameUiState.Error ->
            CrownsErrorState(uiState.reason, if (isDaily) onToday else onStartNew, onBack, isDaily, modifier)
        is CrownsGameUiState.Ready ->
            CrownsReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                difficulty = uiState.puzzle.id.difficulty,
                isHintLoading = uiState.isHintLoading,
                completionPersistence = uiState.completionPersistence,
                onCellTapped = onCellTapped,
                onUndo = onUndo,
                onHint = onHint,
                onReset = onReset,
                onRetryCompletion = onRetryCompletion,
                hapticsEnabled = hapticsEnabled,
                onNewPuzzle = { onNewPuzzle(uiState.puzzle.id.difficulty) },
                onCatalog = onCatalog,
                onToday = onToday,
                isDaily = isDaily,
                modifier = modifier,
            )
    }
}

@Composable
private fun CrownsLoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.creating_puzzle), Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun CrownsErrorState(
    reason: CrownsGameError,
    onTryAnother: () -> Unit,
    onBack: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(
            stringResource(
                when (reason) {
                    CrownsGameError.MISSING_SAVED_SESSION -> R.string.missing_saved_game
                    CrownsGameError.INVALID_SAVED_SESSION -> R.string.invalid_saved_game
                    CrownsGameError.GENERATION -> R.string.puzzle_generation_error
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onTryAnother, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(if (isDaily) R.string.to_today else R.string.try_another))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun CrownsReadyState(
    puzzle: CrownsPuzzle,
    game: CrownsGameState,
    difficulty: Difficulty,
    isHintLoading: Boolean,
    completionPersistence: CompletionPersistence,
    onCellTapped: (CrownsPosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onRetryCompletion: () -> Unit,
    hapticsEnabled: Boolean,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    val view = LocalView.current
    var previouslyConflicted by remember { mutableStateOf(game.violations.isNotEmpty()) }
    var previousStatus by remember { mutableStateOf(game.status) }

    LaunchedEffect(game.violations) {
        if (hapticsEnabled && !previouslyConflicted && game.violations.isNotEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        previouslyConflicted = game.violations.isNotEmpty()
    }
    LaunchedEffect(game.status) {
        if (hapticsEnabled && previousStatus != CrownsGameStatus.SOLVED && game.status == CrownsGameStatus.SOLVED) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        previousStatus = game.status
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.difficulty_value, difficulty.russianLabel()),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        CrownsBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = { position ->
                if (hapticsEnabled) {
                    val feedback =
                        when (game.cellAt(position)) {
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.EMPTY -> HapticFeedbackConstants.KEYBOARD_TAP
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.MARKED -> HapticFeedbackConstants.CLOCK_TICK
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.CROWN -> HapticFeedbackConstants.KEYBOARD_TAP
                        }
                    view.performHapticFeedback(feedback)
                }
                onCellTapped(position)
            },
        )
        Text(
            stringResource(R.string.crowns_tap_policy),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        game.currentHint?.let { hint -> CrownsHintCard(hint) }
        val firstViolation = game.violations.firstOrNull()
        AnimatedVisibility(firstViolation != null) {
            firstViolation?.let { violation ->
                Text(
                    crownsViolationText(violation.type),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onUndo, enabled = game.moveHistory.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
            }
            IconButton(onClick = onHint, enabled = !isHintLoading && game.status == CrownsGameStatus.IN_PROGRESS) {
                Icon(Icons.Filled.Lightbulb, stringResource(R.string.hint))
            }
            IconButton(onClick = { if (game.moveHistory.isEmpty()) onReset() else showResetConfirmation = true }) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.reset))
            }
        }
        if (isHintLoading) Text(stringResource(R.string.searching_hint), Modifier.padding(top = 4.dp))
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onReset()
                    },
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (game.status == CrownsGameStatus.SOLVED) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.TaskAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    stringResource(
                        if (completionPersistence == CompletionPersistence.Error) {
                            R.string.completion_save_error_title
                        } else {
                            R.string.puzzle_solved
                        },
                    ),
                )
            },
            text = {
                Text(
                    when (completionPersistence) {
                        CompletionPersistence.NotRequired,
                        CompletionPersistence.Saving,
                        -> stringResource(R.string.saving_completion)
                        CompletionPersistence.Saved -> stringResource(R.string.hints_used, game.hintsUsed)
                        CompletionPersistence.Error -> stringResource(R.string.completion_save_error_body)
                    },
                )
            },
            confirmButton = {
                when (completionPersistence) {
                    CompletionPersistence.NotRequired,
                    CompletionPersistence.Saving,
                    -> TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                    CompletionPersistence.Error ->
                        TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                    CompletionPersistence.Saved ->
                        if (isDaily) {
                            TextButton(onClick = onToday) { Text(stringResource(R.string.to_today)) }
                        } else {
                            TextButton(onClick = onNewPuzzle) { Text(stringResource(R.string.new_puzzle)) }
                        }
                }
            },
            dismissButton = {
                if (!isDaily && completionPersistence == CompletionPersistence.Saved) {
                    TextButton(onClick = onCatalog) { Text(stringResource(R.string.to_catalog)) }
                }
            },
        )
    }
}

@Composable
private fun CrownsHintCard(hint: CrownsHint) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(hint.crownsPresentationText(), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.crowns_hint_legend),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CrownsHint.crownsPresentationText(): String {
    val firstTarget = targetPositions.sortedWith(compareBy(CrownsPosition::row, CrownsPosition::column)).first()
    return when (action) {
        CrownsHintAction.CLEAR_CROWN ->
            stringResource(R.string.crowns_hint_incorrect_crown, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.CLEAR_MARK ->
            stringResource(R.string.crowns_hint_incorrect_mark, firstTarget.row + 1, firstTarget.column + 1)
        CrownsHintAction.PLACE_CROWN ->
            stringResource(
                R.string.crowns_hint_place,
                firstTarget.row + 1,
                firstTarget.column + 1,
                technique.crownsPresentationText(),
            )
        CrownsHintAction.MARK_POSITIONS ->
            stringResource(
                R.string.crowns_hint_mark,
                targetPositions.size,
                technique.crownsPresentationText(),
            )
    }
}

@Composable
private fun CrownsLogicTechnique?.crownsPresentationText(): String =
    stringResource(
        when (this) {
            CrownsLogicTechnique.SINGLE_CANDIDATE_ROW -> R.string.crowns_technique_single_row
            CrownsLogicTechnique.SINGLE_CANDIDATE_COLUMN -> R.string.crowns_technique_single_column
            CrownsLogicTechnique.SINGLE_CANDIDATE_REGION -> R.string.crowns_technique_single_region
            CrownsLogicTechnique.REGION_LOCKED_TO_ROW -> R.string.crowns_technique_region_row
            CrownsLogicTechnique.REGION_LOCKED_TO_COLUMN -> R.string.crowns_technique_region_column
            null -> R.string.empty
        },
    )

@Composable
private fun crownsViolationText(type: CrownsViolationType): String =
    stringResource(
        when (type) {
            CrownsViolationType.POSITION_OUTSIDE_BOARD -> R.string.crowns_violation_position
            CrownsViolationType.ROW_CONFLICT -> R.string.crowns_violation_row
            CrownsViolationType.COLUMN_CONFLICT -> R.string.crowns_violation_column
            CrownsViolationType.REGION_CONFLICT -> R.string.crowns_violation_region
            CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT -> R.string.crowns_violation_diagonal
        },
    )
