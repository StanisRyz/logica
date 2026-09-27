package com.stanisryz.logica.ui.tutorial

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

enum class CrownsTutorialStage {
    ROW_AND_COLUMN,
    REGION,
    DIAGONAL,
    MARKS_AND_CONTROLS,
    MINI_PUZZLE,
}

data class CrownsTutorialUiState(
    val stage: CrownsTutorialStage = CrownsTutorialStage.ROW_AND_COLUMN,
    val puzzle: CrownsPuzzle = CrownsTutorialScenarios.puzzle,
    val game: CrownsGameState = CrownsTutorialScenarios.startStateFor(stage),
    val focusedPositions: Set<CrownsPosition> = CrownsTutorialScenarios.focusedPositions(stage),
    val selectedValue: CrownsPlayerCell = CrownsPlayerCell.CROWN,
    val isPencilMode: Boolean = false,
    val feedback: CrownsTutorialFeedback? = null,
    val completed: Boolean = false,
)

enum class CrownsTutorialFeedback {
    ROW_AND_COLUMN,
    REGION,
    DIAGONAL,
    MARKS_AND_CONTROLS,
    MINI_PUZZLE,
}

/** Crowns-only onboarding state. It deliberately has no attempt, result, or Room dependency. */
class CrownsTutorialController {
    private val engine = CrownsGameEngine(CrownsTutorialScenarios.puzzle)

    var state = CrownsTutorialUiState()
        private set

    fun selectValue(value: CrownsPlayerCell) {
        if (value == CrownsPlayerCell.EMPTY) return
        state = state.copy(selectedValue = value)
    }

    fun togglePencilMode() {
        state = state.copy(isPencilMode = !state.isPencilMode)
    }

    fun onCellTapped(position: CrownsPosition) {
        if (state.completed) return

        // A pencil note is a hypothesis, so it never passes or fails a tutorial step.
        if (state.isPencilMode) {
            state = state.copy(game = engine.togglePencilMark(state.game, position, state.selectedValue))
            return
        }

        val updated = engine.placeValue(state.game, position, state.selectedValue)
        // Onboarding teaches the three-mistake rule by living through it: a failed practice attempt
        // simply restarts the same stage instead of dead-ending the tutorial.
        if (updated.status == CrownsGameStatus.FAILED) {
            state =
                state.copy(
                    game = CrownsTutorialScenarios.startStateFor(state.stage),
                    feedback = CrownsTutorialScenarios.feedbackFor(state.stage),
                )
            return
        }
        state =
            when (state.stage) {
                CrownsTutorialStage.ROW_AND_COLUMN,
                CrownsTutorialStage.REGION,
                CrownsTutorialStage.DIAGONAL,
                -> handleExpectedValueStage(position, updated, CrownsPlayerCell.CROWN)
                CrownsTutorialStage.MARKS_AND_CONTROLS ->
                    handleExpectedValueStage(position, updated, CrownsPlayerCell.MARKED)
                CrownsTutorialStage.MINI_PUZZLE -> handleMiniPuzzle(updated)
            }
    }

    private fun handleExpectedValueStage(
        position: CrownsPosition,
        updated: CrownsGameState,
        expected: CrownsPlayerCell,
    ): CrownsTutorialUiState {
        val target = CrownsTutorialScenarios.targetFor(state.stage)
        if (position == target && updated.cellAt(position) == expected && updated.violations.isEmpty()) {
            return advance()
        }
        return state.copy(game = updated, feedback = CrownsTutorialScenarios.feedbackFor(state.stage))
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
        return CrownsTutorialUiState(
            stage = nextStage,
            game = CrownsTutorialScenarios.startStateFor(nextStage),
            // The mini puzzle is about placing crowns, so it never starts on the mark tool.
            selectedValue = if (nextStage == CrownsTutorialStage.MINI_PUZZLE) CrownsPlayerCell.CROWN else state.selectedValue,
            isPencilMode = state.isPencilMode,
        )
    }
}

object CrownsTutorialScenarios {
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

    /**
     * Guided steps build on each other: the crowns placed in earlier steps stay on the board, so the
     * region and diagonal steps can actually be reasoned out from what is already there. The mini
     * puzzle starts empty. A stage restarted after three mistakes returns to this same position.
     */
    fun startStateFor(stage: CrownsTutorialStage): CrownsGameState {
        val engine = CrownsGameEngine(puzzle)
        if (stage == CrownsTutorialStage.MINI_PUZZLE) return engine.start()
        return guidedCrownStages
            .filter { it.ordinal < stage.ordinal }
            .fold(engine.start()) { game, earlier -> engine.placeValue(game, targetFor(earlier), CrownsPlayerCell.CROWN) }
    }

    private val guidedCrownStages =
        listOf(CrownsTutorialStage.ROW_AND_COLUMN, CrownsTutorialStage.REGION, CrownsTutorialStage.DIAGONAL)

    fun nextStage(stage: CrownsTutorialStage): CrownsTutorialStage? = CrownsTutorialStage.entries.getOrNull(stage.ordinal + 1)

    fun focusedPositions(stage: CrownsTutorialStage): Set<CrownsPosition> =
        if (stage == CrownsTutorialStage.MINI_PUZZLE) emptySet() else setOf(targetFor(stage))

    fun targetFor(stage: CrownsTutorialStage): CrownsPosition =
        when (stage) {
            CrownsTutorialStage.ROW_AND_COLUMN -> CrownsPosition(0, 1)
            CrownsTutorialStage.REGION -> CrownsPosition(1, 3)
            CrownsTutorialStage.DIAGONAL -> CrownsPosition(2, 0)
            // A cell the answer definitely leaves empty, so the blocked mark placed here is correct.
            CrownsTutorialStage.MARKS_AND_CONTROLS -> CrownsPosition(0, 0)
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
