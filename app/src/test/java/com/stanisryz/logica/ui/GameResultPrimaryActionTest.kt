package com.stanisryz.logica.ui

import com.stanisryz.logica.ui.components.GameResultPrimaryAction
import com.stanisryz.logica.ui.components.GameResultSaveState
import com.stanisryz.logica.ui.components.gameResultPrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** The result card offers a life instead of Retry or Next level at zero lives, and only then. */
class GameResultPrimaryActionTest {
    @Test
    fun aFailureRetriesWithALifeAndOffersOneWithout() {
        assertEquals(GameResultPrimaryAction.RETRY, action(solved = false, livesOut = false))
        assertEquals(GameResultPrimaryAction.LIFE_OFFER, action(solved = false, livesOut = true))
        assertEquals(GameResultPrimaryAction.LIFE_OFFER, action(solved = false, livesOut = true, isDaily = true))
    }

    @Test
    fun aSolvedLevelMovesOnOnlyWithALife() {
        assertEquals(GameResultPrimaryAction.NEXT_LEVEL, action(solved = true, livesOut = false))
        assertEquals(GameResultPrimaryAction.LIFE_OFFER, action(solved = true, livesOut = true))
        // A solved Daily entry has nothing left but the exit, lives or not.
        assertEquals(GameResultPrimaryAction.NONE, action(solved = true, livesOut = true, isDaily = true))
    }

    @Test
    fun savingAndSaveErrorsComeFirst() {
        assertEquals(GameResultPrimaryAction.SAVING, action(solved = false, livesOut = true, save = GameResultSaveState.SAVING))
        assertEquals(GameResultPrimaryAction.RETRY_SAVE, action(solved = true, livesOut = true, save = GameResultSaveState.ERROR))
    }

    private fun action(
        solved: Boolean,
        livesOut: Boolean,
        isDaily: Boolean = false,
        save: GameResultSaveState = GameResultSaveState.SAVED,
    ) = gameResultPrimaryAction(save, solved, isDaily, livesOut)
}
