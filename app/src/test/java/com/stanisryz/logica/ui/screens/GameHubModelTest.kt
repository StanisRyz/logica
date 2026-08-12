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
    fun `catalog lists five games and every card simply opens its difficulty screen`() {
        val opened = mutableListOf<PuzzleType>()
        val catalog = gameCatalogEntries(onPlay = opened::add)

        assertEquals(
            listOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.WORD,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
            ),
            catalog.map { it.puzzleType },
        )
        catalog.single { it.puzzleType == PuzzleType.GAME_2048 }.onPlay()
        catalog.single { it.puzzleType == PuzzleType.WORD }.onPlay()
        assertEquals(listOf(PuzzleType.GAME_2048, PuzzleType.WORD), opened)
    }

    @Test
    fun `every daily state maps to one card action and respects the zero-life gate`() {
        assertEquals(R.string.start, DailyEntryState.AVAILABLE.primaryActionResource())
        assertEquals(R.string.retry_puzzle, DailyEntryState.RETRY.primaryActionResource())
        assertNull(DailyEntryState.COMPLETED.primaryActionResource())

        // With lives, everything but a finished entry acts.
        assertTrue(DailyEntryState.AVAILABLE.isActionable(gameplayAllowed = true))
        assertTrue(DailyEntryState.RETRY.isActionable(gameplayAllowed = true))
        assertFalse(DailyEntryState.COMPLETED.isActionable(gameplayAllowed = true))

        // At zero lives starting an attempt waits for a life; there is no saved attempt to reopen.
        assertFalse(DailyEntryState.AVAILABLE.isActionable(gameplayAllowed = false))
        assertFalse(DailyEntryState.RETRY.isActionable(gameplayAllowed = false))
    }
}
