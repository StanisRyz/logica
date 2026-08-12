package com.stanisryz.logica.catalog

import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A solved Catalog level records its result, pays its gems, and advances its progression once — all
 * in the same transaction — while a failure costs a life and leaves the level where it was.
 */
class AtomicCatalogCompletionTest {
    private val definition = DailyChallengePolicyV5.definitionFor(LocalDate.of(2026, 8, 12))

    @Test
    fun aSolvedLevelPaysAndAdvancesExactlyOnceHoweverOftenTheCallbackRepeats() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val solved =
                dao
                    .catalogCompletion(PuzzleType.BALANCE, difficulty = Difficulty.MEDIUM, levelNumber = 41)
                    .toEntity(1_000)

            dao.complete(solved)
            dao.complete(solved)
            dao.complete(solved)

            assertEquals(1, dao.results.size)
            assertEquals(EconomyRules.solvedGemReward(Difficulty.MEDIUM), dao.wallet(1_000).gems)
            assertEquals(EconomyRules.STARTING_LIVES, dao.wallet(1_000).lives)
            assertEquals(42, dao.currentLevel(PuzzleType.BALANCE, Difficulty.MEDIUM))
            assertEquals(
                41,
                dao.results.values
                    .single()
                    .catalogLevelNumber,
            )
            // Progression is per game and per difficulty: nothing else moved.
            assertEquals(null, dao.currentLevel(PuzzleType.BALANCE, Difficulty.EASY))
            assertEquals(null, dao.currentLevel(PuzzleType.CROWNS, Difficulty.MEDIUM))
        }

    @Test
    fun aFailedLevelCostsALifeAndKeepsProgressionOnTheSameLevel() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val solved = dao.catalogCompletion(PuzzleType.CROWNS, levelNumber = 1).toEntity(1_000)
            dao.complete(solved)

            val failed =
                dao
                    .catalogCompletion(
                        PuzzleType.CROWNS,
                        outcome = GameOutcome.FAILED,
                        levelNumber = 2,
                        attemptId = "first",
                    ).toEntity(2_000)
            dao.complete(failed)
            dao.complete(failed)

            assertEquals(2, dao.results.size)
            assertEquals(EconomyRules.STARTING_LIVES - 1, dao.wallet(2_000).lives)
            assertEquals(2, dao.currentLevel(PuzzleType.CROWNS))

            // Retrying the same level is a new attempt identity; solving it advances once more.
            val retried = dao.catalogCompletion(PuzzleType.CROWNS, levelNumber = 2, attemptId = "second").toEntity(3_000)
            dao.complete(retried)

            assertEquals(3, dao.results.size)
            assertEquals(3, dao.currentLevel(PuzzleType.CROWNS))
        }
}
