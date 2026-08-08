package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.balance.BalanceGameUiState
import com.stanisryz.logica.balance.BalanceGameViewModel
import com.stanisryz.logica.balance.BalanceGameViewModelFactory
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceHint
import com.stanisryz.logica.puzzle.core.balance.BalanceHintKind
import com.stanisryz.logica.puzzle.core.balance.BalanceLogicTechnique
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.ui.balance.BalanceBoard

@Composable
fun BalanceGameRoute(
    difficulty: Difficulty,
    seed: PuzzleSeed,
    onBack: () -> Unit,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(difficulty, seed) { BalanceGameViewModelFactory(difficulty, seed) }
    val gameViewModel: BalanceGameViewModel = viewModel(factory = factory)
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()

    BalanceGameScreen(
        uiState = uiState,
        difficulty = difficulty,
        onCellTapped = gameViewModel::onCellTapped,
        onUndo = gameViewModel::undo,
        onHint = gameViewModel::requestHint,
        onReset = gameViewModel::reset,
        onBack = onBack,
        onNewPuzzle = onNewPuzzle,
        onCatalog = onCatalog,
        modifier = modifier,
    )
}

@Composable
private fun BalanceGameScreen(
    uiState: BalanceGameUiState,
    difficulty: Difficulty,
    onCellTapped: (BalancePosition) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        BalanceGameUiState.Loading -> LoadingState(modifier)
        is BalanceGameUiState.Error ->
            ErrorState(
                message = uiState.message,
                onTryAnother = onNewPuzzle,
                onBack = onBack,
                modifier = modifier,
            )
        is BalanceGameUiState.Ready ->
            ReadyState(
                puzzle = uiState.puzzle,
                game = uiState.game,
                difficulty = difficulty,
                isHintLoading = uiState.isHintLoading,
                onCellTapped = onCellTapped,
                onUndo = onUndo,
                onHint = onHint,
                onReset = onReset,
                onNewPuzzle = onNewPuzzle,
                onCatalog = onCatalog,
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Создаём головоломку…", modifier = Modifier.padding(top = 16.dp))
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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onTryAnother, modifier = Modifier.padding(top = 16.dp)) {
            Text("Попробовать другую")
        }
        TextButton(onClick = onBack) {
            Text("Назад")
        }
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
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    modifier: Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Сложность: ${difficulty.russianLabel}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        BalanceBoard(
            puzzle = puzzle,
            game = game,
            onCellTapped = onCellTapped,
        )

        game.currentHint?.let { hint ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
            ) {
                Text(
                    text = hint.presentationText,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = game.moveHistory.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Отменить")
            }
            Button(
                onClick = onHint,
                enabled = !isHintLoading && game.status == BalanceGameStatus.IN_PROGRESS,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isHintLoading) "Ищем…" else "Подсказка")
            }
            OutlinedButton(
                onClick = {
                    if (game.moveHistory.isEmpty()) onReset() else showResetConfirmation = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Сброс")
            }
        }
        if (game.violations.isNotEmpty()) {
            Text(
                text = "Конфликтов: ${game.violations.size}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Все введённые значения будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onReset()
                    },
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (game.status == BalanceGameStatus.SOLVED) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Головоломка решена") },
            text = { Text("Использовано подсказок: ${game.hintsUsed}") },
            confirmButton = {
                TextButton(onClick = onNewPuzzle) {
                    Text("Новая головоломка")
                }
            },
            dismissButton = {
                TextButton(onClick = onCatalog) {
                    Text("В каталог")
                }
            },
        )
    }
}

private val BalanceHint.presentationText: String
    get() =
        when (kind) {
            BalanceHintKind.INCORRECT_VALUE ->
                "Проверьте клетку ${position.row + 1}:${position.column + 1}. " +
                    "Здесь должно быть ${suggestedValue.symbol}."
            BalanceHintKind.LOGICAL_DEDUCTION ->
                "Клетка ${position.row + 1}:${position.column + 1}: ${suggestedValue.symbol}. " +
                    technique.presentationText
        }

private val BalanceLogicTechnique?.presentationText: String
    get() =
        when (this) {
            BalanceLogicTechnique.PREVENT_THREE -> "Нельзя ставить три одинаковых значения подряд."
            BalanceLogicTechnique.COMPLETE_QUOTA -> "В линии уже набрана половина одинаковых значений."
            BalanceLogicTechnique.PRESERVE_UNIQUENESS -> "Завершённые линии не должны совпадать."
            null -> ""
        }

private val BalanceCell.symbol: String
    get() =
        when (this) {
            BalanceCell.EMPTY -> "пусто"
            BalanceCell.ZERO -> "○"
            BalanceCell.ONE -> "●"
        }
