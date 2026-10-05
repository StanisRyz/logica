package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_goal_body
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_goal_title
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_merge_after
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_merge_before
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_merge_body
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_merge_title
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_swipe_body
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_tutorial_swipe_title
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_back
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_next
import com.stanisryz.logica.ui.components.GameKey
import com.stanisryz.logica.ui.game2048.Game2048Board
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/**
 * Interactive 2048 onboarding on a real mini board: the first step asks for any move, the second
 * for a merge of a prepared row of twos, and the last explains the goal. Nothing is persisted.
 */
@Composable
fun Game2048Tutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    hardwareKeys: Flow<GameKey>? = null,
) {
    val engine = remember { Game2048Engine(TUTORIAL_PUZZLE_ID) }
    var step by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf(boardFor(0)) }
    var moved by remember { mutableStateOf(false) }
    var motionTrace by remember { mutableStateOf<Game2048MoveTrace?>(null) }
    var motionRevision by remember { mutableLongStateOf(0L) }

    fun openStep(target: Int) {
        step = target
        game = boardFor(target)
        moved = false
        motionTrace = null
    }

    fun move(direction: Game2048Direction) {
        if (step == GOAL_STEP || motionTrace != null) return
        val transition = engine.moveWithTrace(game, direction)
        transition.trace?.let { trace ->
            game = transition.state
            moved = true
            motionRevision += 1
            motionTrace = trace
        }
    }

    val canContinue =
        when (step) {
            SWIPE_STEP -> moved
            MERGE_STEP -> game.score > 0
            else -> true
        }

    TutorialLayout(
        step = step + 1,
        stepCount = STEP_COUNT,
        title =
            stringResource(
                when (step) {
                    SWIPE_STEP -> Res.string.game_2048_tutorial_swipe_title
                    MERGE_STEP -> Res.string.game_2048_tutorial_merge_title
                    else -> Res.string.game_2048_tutorial_goal_title
                },
            ),
        body =
            stringResource(
                when (step) {
                    SWIPE_STEP -> Res.string.game_2048_tutorial_swipe_body
                    MERGE_STEP -> Res.string.game_2048_tutorial_merge_body
                    else -> Res.string.game_2048_tutorial_goal_body
                },
            ),
        modifier = modifier,
        footer = {
            if (step > 0) {
                OutlinedButton(onClick = { openStep(step - 1) }) { Text(stringResource(Res.string.tutorial_back)) }
            }
            Button(
                onClick = { if (step == STEP_COUNT - 1) onDone() else openStep(step + 1) },
                enabled = canContinue,
            ) {
                Text(stringResource(if (step == STEP_COUNT - 1) Res.string.tutorial_done else Res.string.tutorial_next))
            }
        },
    ) {
        if (step == MERGE_STEP) {
            // The arrow is an icon: the Web font has no glyph for "→".
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                Text(stringResource(Res.string.game_2048_tutorial_merge_before), style = MaterialTheme.typography.titleLarge)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                Text(stringResource(Res.string.game_2048_tutorial_merge_after), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (step != GOAL_STEP) {
            Game2048Board(
                game = game,
                motionRevision = motionTrace?.let { motionRevision },
                motionTrace = motionTrace,
                onMove = ::move,
                onMotionFinished = { revision -> if (revision == motionRevision) motionTrace = null },
                modifier = Modifier.widthIn(max = TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth().aspectRatio(1f),
                // Arrow keys move exactly like a swipe, as the instruction promises on a computer.
                hardwareKeys = hardwareKeys,
            )
        }
    }
}

private const val SWIPE_STEP = 0
private const val MERGE_STEP = 1
private const val GOAL_STEP = 2
private const val STEP_COUNT = 3

private val TUTORIAL_PUZZLE_ID =
    Game2048PuzzleId(PuzzleSeed(2048L), Difficulty.EASY, Game2048GeneratorVersion.V2)

/** A few scattered tiles for the free move, then a single row of four twos to merge. */
private fun boardFor(step: Int): Game2048State {
    val board =
        when (step) {
            MERGE_STEP -> listOf(2, 2, 2, 2) + List(12) { 0 }
            else ->
                List(Game2048State.CELL_COUNT) { index ->
                    if (index == 5) {
                        2
                    } else if (index == 10) {
                        4
                    } else {
                        0
                    }
                }
        }
    return Game2048State(
        puzzleId = TUTORIAL_PUZZLE_ID,
        board = board,
        score = 0L,
        nextSpawnIndex = 2L,
        status = TUTORIAL_PUZZLE_ID.rules.status(board, 0L),
    )
}
