package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceClue
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

internal enum class BalanceTutorialStage {
    PREVENT_THREE,
    COMPLETE_QUOTA,
    PRESERVE_UNIQUENESS,
    INDEPENDENT_PUZZLE,
}

internal data class BalanceTutorialUiState(
    val stage: BalanceTutorialStage = BalanceTutorialStage.PREVENT_THREE,
    val puzzle: BalancePuzzle = BalanceTutorialScenarios.puzzleFor(BalanceTutorialStage.PREVENT_THREE),
    val game: BalanceGameState = BalanceGameEngine(puzzle).start(),
    val interactivePositions: Set<BalancePosition> =
        BalanceTutorialScenarios.interactivePositions(stage, puzzle),
    val feedback: BalanceTutorialFeedback? = null,
    val completed: Boolean = false,
)

internal enum class BalanceTutorialFeedback {
    TRY_AGAIN,
}

/** A small Balance-only state controller; it deliberately has no session or persistence dependency. */
internal class BalanceTutorialController {
    private var engine = BalanceGameEngine(BalanceTutorialScenarios.puzzleFor(BalanceTutorialStage.PREVENT_THREE))

    var state = BalanceTutorialUiState()
        private set

    fun onCellTapped(position: BalancePosition) {
        if (state.completed) return

        val updated = engine.cycleValue(state.game, position)
        if (updated == state.game) return

        val expectedMove = BalanceTutorialScenarios.expectedMove(state.stage)
        if (expectedMove != null) {
            state =
                if (
                    position == expectedMove.position &&
                    updated.board.cellAt(position) == expectedMove.value &&
                    updated.violations.isEmpty()
                ) {
                    advance()
                } else {
                    state.copy(game = updated, feedback = BalanceTutorialFeedback.TRY_AGAIN)
                }
            return
        }

        state =
            if (updated.status == com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus.SOLVED) {
                state.copy(game = updated, feedback = null, completed = true)
            } else {
                state.copy(game = updated, feedback = null)
            }
    }

    private fun advance(): BalanceTutorialUiState {
        val nextStage = BalanceTutorialScenarios.nextStage(state.stage)
        if (nextStage == null) return state.copy(completed = true, feedback = null)
        val puzzle = BalanceTutorialScenarios.puzzleFor(nextStage)
        engine = BalanceGameEngine(puzzle)
        return BalanceTutorialUiState(stage = nextStage, puzzle = puzzle, game = engine.start())
    }
}

internal data class ExpectedTutorialMove(
    val position: BalancePosition,
    val value: BalanceCell,
)

internal object BalanceTutorialScenarios {
    private val firstTarget = BalancePosition(0, 2)
    private val secondTarget = BalancePosition(0, 3)
    private val thirdTarget = BalancePosition(1, 0)

    fun nextStage(stage: BalanceTutorialStage): BalanceTutorialStage? =
        when (stage) {
            BalanceTutorialStage.PREVENT_THREE -> BalanceTutorialStage.COMPLETE_QUOTA
            BalanceTutorialStage.COMPLETE_QUOTA -> BalanceTutorialStage.PRESERVE_UNIQUENESS
            BalanceTutorialStage.PRESERVE_UNIQUENESS -> BalanceTutorialStage.INDEPENDENT_PUZZLE
            BalanceTutorialStage.INDEPENDENT_PUZZLE -> null
        }

    fun expectedMove(stage: BalanceTutorialStage): ExpectedTutorialMove? =
        when (stage) {
            BalanceTutorialStage.PREVENT_THREE -> ExpectedTutorialMove(firstTarget, BalanceCell.ONE)
            BalanceTutorialStage.COMPLETE_QUOTA -> ExpectedTutorialMove(secondTarget, BalanceCell.ONE)
            BalanceTutorialStage.PRESERVE_UNIQUENESS -> ExpectedTutorialMove(thirdTarget, BalanceCell.ONE)
            BalanceTutorialStage.INDEPENDENT_PUZZLE -> null
        }

    fun puzzleFor(stage: BalanceTutorialStage): BalancePuzzle =
        BalancePuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.BALANCE,
                    difficulty = Difficulty.EASY,
                    seed = PuzzleSeed(10_000L + stage.ordinal),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = 4,
            fixedClues = cluesFor(stage),
        )

    fun interactivePositions(
        stage: BalanceTutorialStage,
        puzzle: BalancePuzzle,
    ): Set<BalancePosition> =
        expectedMove(stage)?.let { setOf(it.position) } ?: buildSet {
            repeat(puzzle.size) { row ->
                repeat(puzzle.size) { column ->
                    val position = BalancePosition(row, column)
                    if (position !in puzzle.fixedClues) add(position)
                }
            }
        }

    private fun cluesFor(stage: BalanceTutorialStage): List<BalanceClue> =
        when (stage) {
            BalanceTutorialStage.PREVENT_THREE ->
                clues(
                    "00.1",
                    "1100",
                    "0110",
                    "1001",
                )
            BalanceTutorialStage.COMPLETE_QUOTA ->
                clues(
                    "001.",
                    "1100",
                    "0110",
                    "1001",
                )
            BalanceTutorialStage.PRESERVE_UNIQUENESS ->
                clues(
                    "0011",
                    ".01.",
                    "....",
                    "....",
                )
            BalanceTutorialStage.INDEPENDENT_PUZZLE ->
                clues(
                    "0..1",
                    ".1.1",
                    "1.1.",
                    "1..0",
                )
        }

    private fun clues(vararg rows: String): List<BalanceClue> =
        rows.flatMapIndexed { row, values ->
            values.mapIndexedNotNull { column, value ->
                val cell =
                    when (value) {
                        '0' -> BalanceCell.ZERO
                        '1' -> BalanceCell.ONE
                        else -> null
                    }
                cell?.let { BalanceClue(BalancePosition(row, column), it) }
            }
        }
}
