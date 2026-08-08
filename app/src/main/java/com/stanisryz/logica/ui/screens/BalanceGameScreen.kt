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
import com.stanisryz.logica.balance.BalanceGameContext
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.balance.BalanceGameUiState
import com.stanisryz.logica.balance.BalanceGameViewModel
import com.stanisryz.logica.balance.BalanceGameViewModelFactory
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceHint
import com.stanisryz.logica.puzzle.core.balance.BalanceHintKind
import com.stanisryz.logica.puzzle.core.balance.BalanceLogicTechnique
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.balance.BalanceViolationType
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.ui.balance.BalanceBoard

@Composable
internal fun BalanceGameRoute(
    launch: BalanceGameLaunch,
    sessionRepository: GameSessionRepository,
    dailyChallengeRepository: DailyChallengeRepository,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(launch, sessionRepository, dailyChallengeRepository) {
            BalanceGameViewModelFactory(launch, sessionRepository, dailyChallengeRepository)
        }
    val gameViewModel: BalanceGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    BalanceGameScreen(
        uiState,
        gameViewModel::onCellTapped,
        gameViewModel::undo,
        gameViewModel::requestHint,
        gameViewModel::reset,
        hapticsEnabled,
        onBack,
        onNewPuzzle,
        onStartNew,
        onCatalog,
        onToday,
        launch.context is BalanceGameContext.Daily,
        modifier,
    )
}

@Composable
private fun BalanceGameScreen(
    uiState: BalanceGameUiState,
    onCellTapped: (BalancePosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onNewPuzzle: (Difficulty) -> Unit,
    onStartNew: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
    isDaily: Boolean,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        BalanceGameUiState.Loading -> LoadingState(modifier)
        is BalanceGameUiState.Error -> ErrorState(uiState.message, if (isDaily) onToday else onStartNew, onBack, modifier)
        is BalanceGameUiState.Ready ->
            ReadyState(
                uiState.puzzle,
                uiState.game,
                uiState.puzzle.id.difficulty,
                uiState.isHintLoading,
                onCellTapped,
                onUndo,
                onHint,
                onReset,
                hapticsEnabled,
                { onNewPuzzle(uiState.puzzle.id.difficulty) },
                onCatalog,
                onToday,
                isDaily,
                modifier,
            )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.creating_puzzle), Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onTryAnother: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onTryAnother, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.try_another)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun ReadyState(
    puzzle: BalancePuzzle,
    game: BalanceGameState,
    difficulty: Difficulty,
    isHintLoading: Boolean,
    onCellTapped: (BalancePosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
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
        if (hapticsEnabled && previousStatus != BalanceGameStatus.SOLVED && game.status == BalanceGameStatus.SOLVED) {
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
        BalanceBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = {
                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCellTapped(it)
            },
        )
        game.currentHint?.let { hint -> HintCard(hint) }
        AnimatedVisibility(game.violations.isNotEmpty()) {
            Text(
                violationText(game.violations.first().type),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onUndo, enabled = game.moveHistory.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
            }
            IconButton(onClick = onHint, enabled = !isHintLoading && game.status == BalanceGameStatus.IN_PROGRESS) {
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
                TextButton(onClick = {
                    showResetConfirmation = false
                    onReset()
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (game.status == BalanceGameStatus.SOLVED) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.TaskAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.puzzle_solved)) },
            text = { Text(stringResource(R.string.hints_used, game.hintsUsed)) },
            confirmButton = {
                if (isDaily) {
                    TextButton(onClick = onToday) { Text(stringResource(R.string.to_today)) }
                } else {
                    TextButton(onClick = onNewPuzzle) { Text(stringResource(R.string.new_puzzle)) }
                }
            },
            dismissButton = {
                if (!isDaily) {
                    TextButton(onClick = onCatalog) { Text(stringResource(R.string.to_catalog)) }
                }
            },
        )
    }
}

@Composable
private fun HintCard(hint: BalanceHint) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(hint.presentationText(), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.hint_legend), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun BalanceHint.presentationText(): String =
    when (kind) {
        BalanceHintKind.INCORRECT_VALUE ->
            stringResource(
                R.string.hint_incorrect_value,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
            )
        BalanceHintKind.LOGICAL_DEDUCTION ->
            stringResource(
                R.string.hint_logical_deduction,
                position.row + 1,
                position.column + 1,
                suggestedValue.symbol(),
                technique.presentationText(),
            )
    }

@Composable
private fun BalanceLogicTechnique?.presentationText(): String =
    stringResource(
        when (this) {
            BalanceLogicTechnique.PREVENT_THREE -> R.string.rule_prevent_three
            BalanceLogicTechnique.COMPLETE_QUOTA -> R.string.rule_complete_quota
            BalanceLogicTechnique.PRESERVE_UNIQUENESS -> R.string.rule_preserve_uniqueness
            null -> R.string.empty
        },
    )

@Composable
private fun violationText(type: BalanceViolationType): String =
    stringResource(
        when (type) {
            BalanceViolationType.UNBALANCED_ROW -> R.string.violation_unbalanced_row
            BalanceViolationType.UNBALANCED_COLUMN -> R.string.violation_unbalanced_column
            BalanceViolationType.THREE_EQUAL_HORIZONTAL -> R.string.violation_three_horizontal
            BalanceViolationType.THREE_EQUAL_VERTICAL -> R.string.violation_three_vertical
            BalanceViolationType.DUPLICATE_ROWS -> R.string.violation_duplicate_rows
            BalanceViolationType.DUPLICATE_COLUMNS -> R.string.violation_duplicate_columns
            BalanceViolationType.FIXED_CLUE_CONFLICT -> R.string.violation_fixed_clue
            BalanceViolationType.BOARD_SIZE_MISMATCH -> R.string.violation_board_size
        },
    )

private fun BalanceCell.symbol(): String =
    when (this) {
        BalanceCell.EMPTY -> ""
        BalanceCell.ZERO -> "○"
        BalanceCell.ONE -> "●"
    }
