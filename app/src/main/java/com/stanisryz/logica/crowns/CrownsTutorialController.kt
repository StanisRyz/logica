package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.RegionId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

internal enum class CrownsTutorialStage {
    ROW_AND_COLUMN,
    REGION,
    DIAGONAL,
    MARKS_AND_CONTROLS,
    MINI_PUZZLE,
}

internal data class CrownsTutorialUiState(
    val stage: CrownsTutorialStage = CrownsTutorialStage.ROW_AND_COLUMN,
    val puzzle: CrownsPuzzle = CrownsTutorialScenarios.puzzle,
    val game: CrownsGameState = CrownsGameEngine(puzzle).start(),
    val focusedPositions: Set<CrownsPosition> = CrownsTutorialScenarios.focusedPositions(stage),
    val markCycleStep: Int = 0,
    val feedback: CrownsTutorialFeedback? = null,
    val completed: Boolean = false,
)

internal enum class CrownsTutorialFeedback {
    ROW_AND_COLUMN,
    REGION,
    DIAGONAL,
    MARKS_AND_CONTROLS,
    MINI_PUZZLE,
}

/** Crowns-only onboarding state. It deliberately has no session, result, or Room dependency. */
internal class CrownsTutorialController {
    private var engine = CrownsGameEngine(CrownsTutorialScenarios.puzzle)

    var state = CrownsTutorialUiState()
        private set

    fun onCellTapped(position: CrownsPosition) {
        if (state.completed) return

        val updated = cycleCell(state.game, position)
        state =
            when (state.stage) {
                CrownsTutorialStage.ROW_AND_COLUMN,
                CrownsTutorialStage.REGION,
                CrownsTutorialStage.DIAGONAL,
                -> handleCrownStage(position, updated)
                CrownsTutorialStage.MARKS_AND_CONTROLS -> handleMarksStage(position, updated)
                CrownsTutorialStage.MINI_PUZZLE -> handleMiniPuzzle(updated)
            }
    }

    private fun cycleCell(
        game: CrownsGameState,
        position: CrownsPosition,
    ): CrownsGameState =
        when (game.cellAt(position)) {
            CrownsPlayerCell.EMPTY -> engine.placeMark(game, position)
            CrownsPlayerCell.MARKED -> engine.placeCrown(game, position)
            CrownsPlayerCell.CROWN -> engine.clearCell(game, position)
        }

    private fun handleCrownStage(
        position: CrownsPosition,
        updated: CrownsGameState,
    ): CrownsTutorialUiState {
        val target = CrownsTutorialScenarios.targetFor(state.stage)
        if (position == target && updated.cellAt(position) == CrownsPlayerCell.CROWN && updated.violations.isEmpty()) {
            return advance()
        }
        val isExpectedFirstTap = position == target && updated.cellAt(position) == CrownsPlayerCell.MARKED
        return state.copy(
            game = updated,
            feedback = if (isExpectedFirstTap) null else CrownsTutorialScenarios.feedbackFor(state.stage),
        )
    }

    private fun handleMarksStage(
        position: CrownsPosition,
        updated: CrownsGameState,
    ): CrownsTutorialUiState {
        val target = CrownsTutorialScenarios.targetFor(state.stage)
        val expectedCell = CrownsTutorialScenarios.markCycle[state.markCycleStep]
        if (position == target && updated.cellAt(position) == expectedCell) {
            val nextStep = state.markCycleStep + 1
            return if (nextStep == CrownsTutorialScenarios.markCycle.size) {
                advance()
            } else {
                state.copy(game = updated, markCycleStep = nextStep)
            }
        }
        return state.copy(game = updated, feedback = CrownsTutorialFeedback.MARKS_AND_CONTROLS)
    }

    private fun handleMiniPuzzle(updated: CrownsGameState): CrownsTutorialUiState =
        if (updated.status == CrownsGameStatus.SOLVED) {
            state.copy(game = updated, feedback = null, completed = true)
        } else {
            state.copy(
                game = updated,
                feedback = if (updated.violations.isEmpty()) null else CrownsTutorialFeedback.MINI_PUZZLE,
            )
        }

    private fun advance(): CrownsTutorialUiState {
        val nextStage = CrownsTutorialScenarios.nextStage(state.stage) ?: return state.copy(completed = true, feedback = null)
        engine = CrownsGameEngine(CrownsTutorialScenarios.puzzle)
        return CrownsTutorialUiState(stage = nextStage, game = engine.start())
    }
}

internal object CrownsTutorialScenarios {
    private val rows = listOf("AAAB", "ADAB", "CDDD", "DDDD")

    val puzzle: CrownsPuzzle =
        CrownsPuzzle(
            id =
                PuzzleId(
                    type = PuzzleType.CROWNS,
                    difficulty = Difficulty.EASY,
                    seed = PuzzleSeed(21_001L),
                    generatorVersion = GeneratorVersion(1),
                ),
            size = rows.size,
            regionAssignments =
                buildMap {
                    rows.forEachIndexed { row, regions ->
                        regions.forEachIndexed { column, region ->
                            put(CrownsPosition(row, column), RegionId(region - 'A'))
                        }
                    }
                },
        )

    val markCycle = listOf(CrownsPlayerCell.MARKED, CrownsPlayerCell.CROWN, CrownsPlayerCell.EMPTY)

    fun nextStage(stage: CrownsTutorialStage): CrownsTutorialStage? = CrownsTutorialStage.entries.getOrNull(stage.ordinal + 1)

    fun focusedPositions(stage: CrownsTutorialStage): Set<CrownsPosition> =
        if (stage == CrownsTutorialStage.MINI_PUZZLE) emptySet() else setOf(targetFor(stage))

    fun targetFor(stage: CrownsTutorialStage): CrownsPosition =
        when (stage) {
            CrownsTutorialStage.ROW_AND_COLUMN -> CrownsPosition(0, 1)
            CrownsTutorialStage.REGION -> CrownsPosition(1, 3)
            CrownsTutorialStage.DIAGONAL -> CrownsPosition(2, 0)
            CrownsTutorialStage.MARKS_AND_CONTROLS -> CrownsPosition(3, 2)
            CrownsTutorialStage.MINI_PUZZLE -> error("The mini-puzzle has no single target.")
        }

    fun feedbackFor(stage: CrownsTutorialStage): CrownsTutorialFeedback =
        when (stage) {
            CrownsTutorialStage.ROW_AND_COLUMN -> CrownsTutorialFeedback.ROW_AND_COLUMN
            CrownsTutorialStage.REGION -> CrownsTutorialFeedback.REGION
            CrownsTutorialStage.DIAGONAL -> CrownsTutorialFeedback.DIAGONAL
            CrownsTutorialStage.MARKS_AND_CONTROLS -> CrownsTutorialFeedback.MARKS_AND_CONTROLS
            CrownsTutorialStage.MINI_PUZZLE -> CrownsTutorialFeedback.MINI_PUZZLE
        }
}
