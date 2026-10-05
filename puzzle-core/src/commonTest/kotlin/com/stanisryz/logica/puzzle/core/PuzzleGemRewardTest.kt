package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleGemReward
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals

class PuzzleGemRewardTest {
    @Test
    fun onlyAnExpertLevelFirstReachingThreeStarsPaysOneGem() {
        STARRED_GAMES.forEach { type ->
            // stars of this solve to previous best (null: never solved) to gems.
            val table =
                listOf(
                    Triple(3, null, 1),
                    Triple(2, null, 0),
                    Triple(1, null, 0),
                    Triple(3, 2, 1),
                    Triple(3, 1, 1),
                    Triple(3, 0, 1),
                    Triple(3, 3, 0),
                    Triple(2, 3, 0),
                    Triple(2, 1, 0),
                )
            table.forEach { (stars, previous, gems) ->
                assertEquals(gems, PuzzleGemReward.forSolved(type, Difficulty.EXPERT, stars, previous), "$type $stars after $previous")
            }
            Difficulty.entries.filter { it != Difficulty.EXPERT }.forEach { difficulty ->
                assertEquals(0, PuzzleGemReward.forSolved(type, difficulty, 3, null), "$type $difficulty")
            }
        }
    }

    @Test
    fun gamesWithoutStarsCountTheirFirstExpertClearAsThreeStars() {
        listOf(PuzzleType.GAME_2048, PuzzleType.BLOCK_SUDOKU).forEach { type ->
            assertEquals(1, PuzzleGemReward.forSolved(type, Difficulty.EXPERT, stars = null, previousBestStars = null))
            // Solved before (recorded without stars): no second gem.
            assertEquals(0, PuzzleGemReward.forSolved(type, Difficulty.EXPERT, stars = null, previousBestStars = 0))
            Difficulty.entries.filter { it != Difficulty.EXPERT }.forEach { difficulty ->
                assertEquals(0, PuzzleGemReward.forSolved(type, difficulty, stars = null, previousBestStars = null))
            }
        }
    }

    private companion object {
        val STARRED_GAMES = listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD, PuzzleType.SUDOKU, PuzzleType.NONOGRAM)
    }
}
