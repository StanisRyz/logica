package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameEngine
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameStatus
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.puzzle.core.nonogram.NonogramTool
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_clues_body
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_clues_title
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_failed
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_play_body
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_play_title
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_rules_body
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_rules_title
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_tutorial_solved
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_back
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_next
import com.stanisryz.logica.ui.nonogram.NonogramBoard
import com.stanisryz.logica.ui.nonogram.NonogramToolBar
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Nonogram onboarding: what the clue numbers mean, a small practice picture to draw (it restarts
 * itself after the third mistake), and how mistakes and hints work. Nothing is persisted.
 */
@Composable
fun NonogramTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = remember { NonogramGameEngine(TUTORIAL_PUZZLE) }
    var step by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf(engine.start()) }
    var tool by remember { mutableStateOf(NonogramTool.FILL) }
    val solved = game.status == NonogramGameStatus.SOLVED

    LaunchedEffect(game.status) {
        if (game.status == NonogramGameStatus.FAILED) {
            delay(RESTART_DELAY_MS)
            game = engine.start()
        }
    }

    TutorialLayout(
        step = step + 1,
        stepCount = STEP_COUNT,
        title =
            stringResource(
                when (step) {
                    CLUES_STEP -> Res.string.nonogram_tutorial_clues_title
                    PLAY_STEP -> Res.string.nonogram_tutorial_play_title
                    else -> Res.string.nonogram_tutorial_rules_title
                },
            ),
        body =
            stringResource(
                when (step) {
                    CLUES_STEP -> Res.string.nonogram_tutorial_clues_body
                    PLAY_STEP -> Res.string.nonogram_tutorial_play_body
                    else -> Res.string.nonogram_tutorial_rules_body
                },
            ),
        feedback =
            when {
                step != PLAY_STEP -> null
                solved -> stringResource(Res.string.nonogram_tutorial_solved)
                game.status == NonogramGameStatus.FAILED -> stringResource(Res.string.nonogram_tutorial_failed)
                else -> null
            },
        modifier = modifier,
        footer = {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) { Text(stringResource(Res.string.tutorial_back)) }
            }
            Button(
                onClick = { if (step == STEP_COUNT - 1) onDone() else step++ },
                enabled = step != PLAY_STEP || solved,
            ) {
                Text(stringResource(if (step == STEP_COUNT - 1) Res.string.tutorial_done else Res.string.tutorial_next))
            }
        },
    ) {
        if (step != RULES_STEP) {
            NonogramBoard(
                puzzle = TUTORIAL_PUZZLE,
                game = if (step == CLUES_STEP) engine.start() else game,
                onCell = { position -> game = engine.mark(game, position, tool) },
                enabled = step == PLAY_STEP,
                modifier = Modifier.widthIn(max = TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth().aspectRatio(1f),
            )
        }
        if (step == PLAY_STEP) {
            NonogramToolBar(selectedTool = tool, onSelectTool = { tool = it }, enabled = !solved)
        }
    }
}

private const val CLUES_STEP = 0
private const val PLAY_STEP = 1
private const val RULES_STEP = 2
private const val STEP_COUNT = 3
private const val RESTART_DELAY_MS = 1_200L

/** A small heart: two full rows make the first moves obvious. */
private val TUTORIAL_PUZZLE =
    NonogramPuzzle(
        id = PuzzleId(PuzzleType.NONOGRAM, Difficulty.EASY, PuzzleSeed(0), GeneratorVersion(1)),
        size = 5,
        solution =
            listOf("01010", "11111", "11111", "01110", "00100")
                .flatMap { row -> row.map { it == '1' } },
    )
