package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalanceValidator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTutorialControllerTest {
    @Test
    fun progressesOnlyAfterCorrectMovesAndCompletesIndependentPuzzle() {
        BalanceTutorialStage.entries.dropLast(1).forEach { stage ->
            val puzzle = BalanceTutorialScenarios.puzzleFor(stage)
            val expectedMove = requireNotNull(BalanceTutorialScenarios.expectedMove(stage))
            val engine = BalanceGameEngine(puzzle)
            val guidedGame =
                engine.setValue(
                    engine.start(),
                    expectedMove.position,
                    expectedMove.value,
                )
            assertFalse(BalanceValidator().validate(puzzle, guidedGame.board) is ValidationResult.Invalid)
        }

        val controller = BalanceTutorialController()
        val firstTarget = BalancePosition(0, 2)

        controller.onCellTapped(firstTarget)

        assertEquals(BalanceTutorialStage.PREVENT_THREE, controller.state.stage)
        assertEquals(BalanceTutorialFeedback.TRY_AGAIN, controller.state.feedback)

        controller.onCellTapped(firstTarget)
        controller.onCellTapped(BalancePosition(0, 3))
        controller.onCellTapped(BalancePosition(0, 3))
        assertRuleConsistent(controller)
        controller.onCellTapped(BalancePosition(1, 0))
        controller.onCellTapped(BalancePosition(1, 0))
        assertRuleConsistent(controller)

        assertEquals(BalanceTutorialStage.INDEPENDENT_PUZZLE, controller.state.stage)
        assertFalse(controller.state.completed)

        listOf(
            BalancePosition(0, 1) to BalanceCell.ZERO,
            BalancePosition(0, 2) to BalanceCell.ONE,
            BalancePosition(1, 0) to BalanceCell.ZERO,
            BalancePosition(1, 2) to BalanceCell.ZERO,
            BalancePosition(2, 1) to BalanceCell.ZERO,
            BalancePosition(2, 3) to BalanceCell.ZERO,
            BalancePosition(3, 1) to BalanceCell.ONE,
            BalancePosition(3, 2) to BalanceCell.ZERO,
        ).forEach { (position, value) ->
            while (controller.state.game.board
                    .cellAt(position) != value
            ) {
                controller.onCellTapped(position)
            }
        }

        assertTrue(controller.state.completed)
        assertRuleConsistent(controller)
    }

    private fun assertRuleConsistent(controller: BalanceTutorialController) {
        assertFalse(
            BalanceValidator().validate(controller.state.puzzle, controller.state.game.board) is
                ValidationResult.Invalid,
        )
    }
}
