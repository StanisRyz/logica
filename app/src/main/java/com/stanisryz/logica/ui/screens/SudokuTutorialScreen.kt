package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.components.GameAction
import com.stanisryz.logica.ui.components.GameActionBar
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.sudoku.SudokuBoard
import com.stanisryz.logica.ui.sudoku.SudokuNumberPad
import com.stanisryz.logica.ui.sudoku.SudokuPencilToggle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.launch

/** A fixed, repository-free worked example: it cannot create attempts, results, or economy events. */
@Composable
internal fun SudokuTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    SudokuTutorialScreen(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setSudokuTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}

@Composable
private fun SudokuTutorialScreen(
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val puzzle = remember { previewSudokuPuzzle() }
    val engine = remember(puzzle) { SudokuGameEngine(puzzle) }
    var game by remember(engine) { mutableStateOf(engine.start()) }
    var selectedCell by remember { mutableStateOf<SudokuPosition?>(null) }
    var pencil by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    val hasEnteredNumber = game.cells.any { it.status == SudokuCellStatus.CORRECT }
    val hasCandidate = game.cells.any { !it.candidates.isEmpty }
    val hasUsedHint = game.hintsUsed > 0
    val canContinue =
        when (step) {
            0 -> true
            1 -> hasEnteredNumber
            2 -> hasCandidate
            else -> hasUsedHint
        }

    ScreenColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle(stringResource(R.string.sudoku_tutorial_title))
        Text(stringResource(R.string.sudoku_tutorial_progress, step + 1), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(STEP_TITLES[step]), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(STEP_BODIES[step]), style = MaterialTheme.typography.bodyMedium)
        if (step > 0) {
            MistakeIndicator(game.mistakesUsed, SudokuGameState.MAX_MISTAKES)
            SudokuBoard(game, selectedCell, onCellSelected = { selectedCell = it })
            if (step == 2) {
                SudokuPencilToggle(pencil, enabled = true, onToggle = { pencil = !pencil })
            }
            if (step <= 2) {
                val position = selectedCell
                SudokuNumberPad(
                    enabled = position != null,
                    onDigit = { digit ->
                        if (position != null) {
                            game =
                                if (step == 2 && pencil) {
                                    engine.toggleCandidate(game, position, digit)
                                } else {
                                    engine.placeValue(game, position, digit)
                                }
                        }
                    },
                )
            } else {
                GameActionBar(
                    listOf(
                        GameAction(
                            icon = Icons.Filled.Lightbulb,
                            label = stringResource(R.string.hint),
                            enabled = !hasUsedHint,
                            onClick = { game = engine.requestHint(game) },
                        ),
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action, Alignment.End),
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text(stringResource(R.string.back)) }
            }
            Button(
                onClick = { if (step == LAST_STEP) onDone() else step += 1 },
                enabled = canContinue,
            ) {
                Text(stringResource(if (step == LAST_STEP) R.string.done else R.string.continue_game))
            }
        }
    }
}

private val STEP_TITLES =
    listOf(
        R.string.sudoku_tutorial_rules_title,
        R.string.sudoku_tutorial_input_title,
        R.string.sudoku_tutorial_pencil_title,
        R.string.sudoku_tutorial_hint_title,
    )
private val STEP_BODIES =
    listOf(
        R.string.sudoku_tutorial_rules_body,
        R.string.sudoku_tutorial_input_body,
        R.string.sudoku_tutorial_pencil_body,
        R.string.sudoku_tutorial_hint_body,
    )
private const val LAST_STEP = 3
