package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleRating
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PuzzleRatingTest {
    @Test
    fun levelsWeighByDifficulty() {
        val cleared = mapOf(Difficulty.EASY to 10L, Difficulty.MEDIUM to 5L, Difficulty.HARD to 2L, Difficulty.EXPERT to 1L)
        assertEquals(10L + 10L + 6L + 4L, PuzzleRating.levelPoints(cleared))
        assertEquals(0L, PuzzleRating.levelPoints(emptyMap()))
    }

    @Test
    fun only2048IsRatedByScore() {
        assertTrue(PuzzleRating.isScoreRated(PuzzleType.GAME_2048))
        listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD, PuzzleType.SUDOKU, PuzzleType.NONOGRAM)
            .forEach { assertFalse(PuzzleRating.isScoreRated(it)) }
    }
}
