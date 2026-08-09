package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownsTutorialControllerTest {
    @Test
    fun advancesThroughGuidedRulesAndCompletesFixedMiniPuzzle() {
        val controller = CrownsTutorialController()

        tap(controller, CrownsPosition(0, 1), times = 2)
        assertEquals(CrownsTutorialStage.REGION, controller.state.stage)
        tap(controller, CrownsPosition(1, 3), times = 2)
        assertEquals(CrownsTutorialStage.DIAGONAL, controller.state.stage)
        tap(controller, CrownsPosition(2, 0), times = 2)
        assertEquals(CrownsTutorialStage.MARKS_AND_CONTROLS, controller.state.stage)
        tap(controller, CrownsPosition(3, 2), times = 3)
        assertEquals(CrownsTutorialStage.MINI_PUZZLE, controller.state.stage)

        listOf(
            CrownsPosition(0, 1),
            CrownsPosition(1, 3),
            CrownsPosition(2, 0),
            CrownsPosition(3, 2),
        ).forEach { position ->
            tap(controller, position, times = 2)
        }

        assertTrue(controller.state.completed)
        assertFalse(
            controller.state.game.violations
                .isNotEmpty(),
        )
    }

    private fun tap(
        controller: CrownsTutorialController,
        position: CrownsPosition,
        times: Int,
    ) {
        repeat(times) { controller.onCellTapped(position) }
    }
}
