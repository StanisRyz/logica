package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_failed
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_hint_body
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_hint_title
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_input_body
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_input_example
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_input_title
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_pencil_body
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_pencil_title
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_rules_body
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_tutorial_rules_title
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_back
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_next
import com.stanisryz.logica.ui.components.MistakeIndicator
import com.stanisryz.logica.ui.sudoku.SudokuBoard
import com.stanisryz.logica.ui.sudoku.SudokuPencilToggle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Sudoku onboarding on a fixed, repository-free example: it creates no attempts, results, economy
 * events, or hint spending. The input step points at a cell with exactly one possible digit, and a
 * third mistake restarts the example instead of leaving the step unfinishable.
 */
@Composable
fun SudokuTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = remember { SudokuGameEngine(TUTORIAL_PUZZLE) }
    // The first logical deduction on the empty example: its cell has exactly one possible digit.
    val example = remember(engine) { requireNotNull(engine.requestHint(engine.start()).currentHint) }
    var game by remember(engine) { mutableStateOf(engine.start()) }
    var selectedCell by remember { mutableStateOf<SudokuPosition?>(null) }
    var pencil by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var restarted by remember { mutableStateOf(false) }

    fun update(updated: SudokuGameState) {
        restarted = updated.status == SudokuGameStatus.FAILED
        game = if (restarted) engine.start() else updated
    }

    val canContinue =
        when (step) {
            0 -> true
            1 -> game.cells.any { it.status == SudokuCellStatus.CORRECT }
            2 -> game.cells.any { !it.candidates.isEmpty }
            else -> game.hintsUsed > 0
        }
    val body =
        when (step) {
            0 -> stringResource(Res.string.sudoku_tutorial_rules_body)
            1 ->
                stringResource(Res.string.sudoku_tutorial_input_body) + " " +
                    stringResource(Res.string.sudoku_tutorial_input_example, example.value)
            2 -> stringResource(Res.string.sudoku_tutorial_pencil_body)
            else -> stringResource(Res.string.sudoku_tutorial_hint_body)
        }

    TutorialLayout(
        step = step + 1,
        stepCount = STEP_COUNT,
        title =
            stringResource(
                when (step) {
                    0 -> Res.string.sudoku_tutorial_rules_title
                    1 -> Res.string.sudoku_tutorial_input_title
                    2 -> Res.string.sudoku_tutorial_pencil_title
                    else -> Res.string.sudoku_tutorial_hint_title
                },
            ),
        body = body,
        feedback = if (restarted) stringResource(Res.string.sudoku_tutorial_failed) else null,
        modifier = modifier,
        footer = {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text(stringResource(Res.string.tutorial_back)) }
            }
            Button(
                onClick = {
                    if (step == STEP_COUNT - 1) {
                        onDone()
                    } else {
                        step += 1
                        restarted = false
                        // The input step starts on the cell its instruction talks about.
                        if (step == 1 && game.cellAt(example.position).status == SudokuCellStatus.EMPTY) {
                            selectedCell = example.position
                        }
                    }
                },
                enabled = canContinue,
            ) {
                Text(stringResource(if (step == STEP_COUNT - 1) Res.string.tutorial_done else Res.string.tutorial_next))
            }
        },
    ) {
        // The rules step shows the same board read-only, so rows, columns, and blocks are visible.
        if (step > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MistakeIndicator(game.mistakesUsed, SudokuGameState.MAX_MISTAKES)
                // Pencil sits beside the counter so the board and the digits stay on one screen.
                if (step == 2) {
                    Box(Modifier.width(PENCIL_TOGGLE_WIDTH)) {
                        SudokuPencilToggle(pencil, enabled = true, onToggle = { pencil = !pencil })
                    }
                }
                if (step == 3) {
                    Spacer(Modifier.width(LogicaSpacing.action))
                    FilledTonalButton(onClick = { update(engine.requestHint(game)) }, enabled = game.hintsUsed == 0) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null)
                        Text(stringResource(Res.string.hint))
                    }
                }
            }
        }
        SudokuBoard(
            game = game,
            selectedCell = selectedCell,
            onCellSelected = { selectedCell = it },
            modifier = Modifier.widthIn(max = SUDOKU_TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth().aspectRatio(1f),
            enabled = step > 0,
        )
        if (step > 0) {
            if (step <= 2) {
                TutorialDigitRow(
                    enabled = selectedCell != null,
                    onDigit = { digit ->
                        val position = selectedCell ?: return@TutorialDigitRow
                        update(
                            if (step == 2 && pencil) {
                                engine.toggleCandidate(game, position, digit)
                            } else {
                                engine.placeValue(game, position, digit)
                            },
                        )
                    },
                )
            }
        }
    }
}

private const val STEP_COUNT = 4

/** One compact row of digits: the example board and its input fit a phone screen together. */
@Composable
private fun TutorialDigitRow(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = SUDOKU_TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (1..9).forEach { digit ->
            FilledTonalIconButton(
                onClick = { onDigit(digit) },
                enabled = enabled,
                modifier = Modifier.size(DIGIT_BUTTON_SIZE),
            ) { Text(digit.toString(), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private val DIGIT_BUTTON_SIZE = 30.dp

/** Nine columns plus the digit row and Pencil must share one phone screen with the instruction. */
private val SUDOKU_TUTORIAL_BOARD_MAX_WIDTH = 296.dp
private val PENCIL_TOGGLE_WIDTH = 64.dp

private val TUTORIAL_PUZZLE =
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
