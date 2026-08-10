package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsTutorialControllerTest {
    @Test
    fun advancesThroughGuidedRulesAndCompletesFixedMiniPuzzle() {
        val controller = CrownsTutorialController()

        controller.onCellTapped(CrownsPosition(0, 1))
        assertEquals(CrownsTutorialStage.REGION, controller.state.stage)
        controller.onCellTapped(CrownsPosition(1, 3))
        assertEquals(CrownsTutorialStage.DIAGONAL, controller.state.stage)
        controller.onCellTapped(CrownsPosition(2, 0))
        assertEquals(CrownsTutorialStage.MARKS_AND_CONTROLS, controller.state.stage)

        // The blocked-mark step needs the other committed tool, not another crown.
        controller.onCellTapped(CrownsPosition(0, 0))
        assertEquals(CrownsTutorialStage.MARKS_AND_CONTROLS, controller.state.stage)
        assertEquals(CrownsTutorialFeedback.MARKS_AND_CONTROLS, controller.state.feedback)
        controller.selectValue(CrownsPlayerCell.MARKED)
        controller.onCellTapped(CrownsPosition(0, 0))
        assertEquals(CrownsTutorialStage.MINI_PUZZLE, controller.state.stage)

        controller.selectValue(CrownsPlayerCell.CROWN)
        listOf(
            CrownsPosition(0, 1),
            CrownsPosition(1, 3),
            CrownsPosition(2, 0),
            CrownsPosition(3, 2),
        ).forEach { position -> controller.onCellTapped(position) }

        assertTrue(controller.state.completed)
        assertFalse(
            controller.state.game.violations
                .isNotEmpty(),
        )
    }
}
