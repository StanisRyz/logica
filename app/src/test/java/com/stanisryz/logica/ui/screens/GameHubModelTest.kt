package com.stanisryz.logica.ui.screens

import com.stanisryz.logica.R
import com.stanisryz.logica.daily.DailyEntryState
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Game hub is made of: the Daily entries on top and the regular catalog below them. */
class GameHubModelTest {
    @Test
    fun `catalog lists four games and keeps continue primary while a Sudoku save exists`() {
        val continued = mutableListOf<PuzzleType>()
        val started = mutableListOf<PuzzleType>()
        val catalog =
            gameCatalogEntries(
                hasActiveSession = { it == PuzzleType.SUDOKU },
                onContinue = continued::add,
                onNew = started::add,
            )

        assertEquals(
            listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD, PuzzleType.SUDOKU),
            catalog.map { it.puzzleType },
        )
        val sudoku = catalog.single { it.puzzleType == PuzzleType.SUDOKU }
        assertTrue(sudoku.hasActiveSession)
        sudoku.onContinue()
        assertEquals(listOf(PuzzleType.SUDOKU), continued)

        val word = catalog.single { it.puzzleType == PuzzleType.WORD }
        assertFalse(word.hasActiveSession)
        word.onNew()
        assertEquals(listOf(PuzzleType.WORD), started)
    }

    @Test
    fun `every daily state maps to one card action and respects the zero-life gate`() {
        assertEquals(R.string.start, DailyEntryState.AVAILABLE.primaryActionResource())
        assertEquals(R.string.continue_game, DailyEntryState.IN_PROGRESS.primaryActionResource())
        assertEquals(R.string.retry_puzzle, DailyEntryState.RETRY.primaryActionResource())
        assertNull(DailyEntryState.COMPLETED.primaryActionResource())

        // With lives, everything but a finished entry acts.
        assertTrue(DailyEntryState.AVAILABLE.isActionable(gameplayAllowed = true))
        assertTrue(DailyEntryState.RETRY.isActionable(gameplayAllowed = true))
        assertFalse(DailyEntryState.COMPLETED.isActionable(gameplayAllowed = true))

        // At zero lives a saved puzzle still opens; starting an attempt waits for a life.
        assertTrue(DailyEntryState.IN_PROGRESS.isActionable(gameplayAllowed = false))
        assertFalse(DailyEntryState.AVAILABLE.isActionable(gameplayAllowed = false))
        assertFalse(DailyEntryState.RETRY.isActionable(gameplayAllowed = false))
    }
}
