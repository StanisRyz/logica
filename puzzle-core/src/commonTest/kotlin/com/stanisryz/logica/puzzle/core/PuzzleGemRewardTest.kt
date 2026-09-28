package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleGemReward
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals

class PuzzleGemRewardTest {
    @Test
    fun onlyHardGamesAtHighDifficultiesPayGems() {
        val expected =
            mapOf(
                PuzzleType.GAME_2048 to listOf(0, 0, 1, 2),
                PuzzleType.SUDOKU to listOf(0, 0, 1, 2),
                PuzzleType.CROWNS to listOf(0, 0, 0, 1),
                PuzzleType.BALANCE to listOf(0, 0, 0, 0),
                PuzzleType.WORD to listOf(0, 0, 0, 0),
            )
        expected.forEach { (type, rewards) ->
            assertEquals(rewards, Difficulty.entries.map { PuzzleGemReward.forSolved(type, it) }, type.name)
        }
    }
}
