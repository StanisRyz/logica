package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockCell
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_tutorial_play_body
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_tutorial_play_title
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_tutorial_rules_title
import com.stanisryz.logica.shared.ui.generated.resources.block_sudoku_tutorial_solved
import com.stanisryz.logica.shared.ui.generated.resources.rules_block_sudoku_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_block_sudoku_3
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_back
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_next
import com.stanisryz.logica.ui.blocksudoku.BlockSudokuContent
import org.jetbrains.compose.resources.stringResource

/**
 * Block Sudoku onboarding: drag one piece to complete a prepared row and watch it clear, then how
 * boxes, the score, and a stuck tray work. Nothing is persisted and no result is recorded.
 */
@Composable
fun BlockSudokuTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = remember { BlockSudokuEngine(PuzzleSeed(TUTORIAL_SEED), Difficulty.EASY) }
    var step by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf(practiceStart(engine)) }
    val cleared = game.placements > 0 && game.score > PRACTICE_PIECE.size

    TutorialLayout(
        step = step + 1,
        stepCount = STEP_COUNT,
        title =
            stringResource(
                if (step ==
                    PLAY_STEP
                ) {
                    Res.string.block_sudoku_tutorial_play_title
                } else {
                    Res.string.block_sudoku_tutorial_rules_title
                },
            ),
        body =
            if (step == PLAY_STEP) {
                stringResource(Res.string.block_sudoku_tutorial_play_body)
            } else {
                stringResource(Res.string.rules_block_sudoku_2) + "\n\n" + stringResource(Res.string.rules_block_sudoku_3)
            },
        feedback = if (step == PLAY_STEP && cleared) stringResource(Res.string.block_sudoku_tutorial_solved) else null,
        modifier = modifier,
        footer = {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) { Text(stringResource(Res.string.tutorial_back)) }
            }
            Button(
                onClick = { if (step == STEP_COUNT - 1) onDone() else step++ },
                enabled = step != PLAY_STEP || cleared,
            ) {
                Text(stringResource(if (step == STEP_COUNT - 1) Res.string.tutorial_done else Res.string.tutorial_next))
            }
        },
    ) {
        if (step == PLAY_STEP) {
            // The practice keeps its board near the full width: the height is the board's side plus
            // what the score header and the tray need, instead of a fixed box they would share.
            BoxWithConstraints(Modifier.widthIn(max = PRACTICE_MAX_WIDTH).fillMaxWidth()) {
                BlockSudokuContent(
                    state = game,
                    difficulty = Difficulty.EASY,
                    levelNumber = null,
                    gameplayEnabled = !cleared,
                    onPlace = { index, row, column -> game = engine.place(game, index, row, column) },
                    modifier = Modifier.fillMaxWidth().height(maxWidth + PRACTICE_CHROME_HEIGHT),
                )
            }
        }
    }
}

/** A board whose middle row misses exactly the three cells the one tray piece fills. */
private fun practiceStart(engine: BlockSudokuEngine) =
    engine.start().copy(
        board = List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { index -> index / 9 == 4 && index % 9 !in 3..5 },
        tray = listOf(null, PRACTICE_PIECE, null),
        targetScore = PRACTICE_TARGET,
    )

private val PRACTICE_PIECE = BlockPiece(listOf(BlockCell(0, 0), BlockCell(0, 1), BlockCell(0, 2)))
private val PRACTICE_MAX_WIDTH = 360.dp

/** The score header, the tray, and the spacing around the board in the portrait layout. */
private val PRACTICE_CHROME_HEIGHT = 220.dp
private const val TUTORIAL_SEED = 7L

/** Above the one clear (3 + 9 x 2 = 21), so the practice board never ends by itself. */
private const val PRACTICE_TARGET = 30
private const val PLAY_STEP = 0
private const val STEP_COUNT = 2
