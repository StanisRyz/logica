package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.toEntity
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * One terminal result must move the wallet exactly zero or one time. Recomposition, a retried save,
 * or a repeated callback all run the very same completion transaction again, so the ledger event —
 * not the caller — is what makes the reward and the penalty happen only once.
 *
 * The reward itself is difficulty-based and puzzle-agnostic; the penalty is flat.
 */
class EconomyResultCompletionTest {
    private val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))

    @Test
    fun everyDifficultyGrantsItsOwnGemRewardOnceHoweverOftenItIsPersisted() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val rewards =
                mapOf(
                    PuzzleType.BALANCE to Difficulty.EASY,
                    PuzzleType.CROWNS to Difficulty.MEDIUM,
                    PuzzleType.WORD to Difficulty.HARD,
                    PuzzleType.SUDOKU to Difficulty.EXPERT,
                )

            rewards.forEach { (puzzleType, difficulty) ->
                val solved = dao.catalogCompletion(puzzleType, difficulty = difficulty).toEntity(NOW)
                dao.complete(solved)
                dao.complete(solved)
                dao.complete(solved)
                val event = dao.economyEvents.getValue(EconomyEvent.resultEventId(solved.resultId))
                assertEquals(EconomyEventType.SOLVED_REWARD.name, event.eventType)
                assertEquals(EconomyRules.solvedGemReward(difficulty), event.gemDelta)
                assertEquals(0, event.lifeDelta)
            }

            // 1 + 2 + 3 + 4, and repeating each completion three times changed nothing.
            assertEquals(10, dao.wallet(NOW).gems)
            assertEquals(EconomyRules.STARTING_LIVES, dao.wallet(NOW).lives)
            assertEquals(rewards.size, dao.results.size)
            assertEquals(rewards.size, dao.economyEvents.size)
        }

    /** The reward depends on difficulty alone: 2048 in Catalog and a Daily entry pay the same. */
    @Test
    fun theDailyScopeUsesTheSameDifficultyRewardAsTheCatalog() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val entry = definition.entries[0]
            val daily =
                GameCompletion(
                    resultId = "daily-0",
                    puzzleType = entry.puzzleType,
                    difficulty = entry.difficulty,
                    puzzleSeed = entry.seed,
                    generatorVersion = entry.generatorVersion,
                    sessionScope = GameSessionScope.DAILY,
                    hintsUsed = 1,
                    challengeDate = definition.challengeDate,
                    dailyPolicyVersion = definition.policyVersion,
                ).toEntity(NOW)
            val catalog =
                dao
                    .catalogCompletion(PuzzleType.GAME_2048, difficulty = entry.difficulty)
                    .toEntity(NOW)

            dao.complete(daily)
            dao.complete(catalog)

            val expected = EconomyRules.solvedGemReward(entry.difficulty)
            assertEquals(expected, dao.economyEvents.getValue(EconomyEvent.resultEventId("daily-0")).gemDelta)
            assertEquals(
                expected,
                dao.economyEvents.getValue(EconomyEvent.resultEventId(catalog.resultId)).gemDelta,
            )
            assertEquals(expected * 2, dao.wallet(NOW).gems)
        }

    /** Failure is flat: the hardest attempt costs exactly the same single life as the easiest. */
    @Test
    fun aFailedResultSpendsOneLifeAtEveryDifficultyExactlyOnce() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val failed =
                dao
                    .catalogCompletion(PuzzleType.GAME_2048, GameOutcome.FAILED, Difficulty.EXPERT)
                    .toEntity(NOW)

            dao.complete(failed)
            dao.complete(failed)

            val wallet = dao.wallet(NOW)
            assertEquals(EconomyRules.STARTING_LIVES - 1, wallet.lives)
            assertEquals(EconomyRules.STARTING_GEMS, wallet.gems)
            assertEquals(NOW + EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS, wallet.nextLifeAtEpochMillis)
            assertEquals(1, dao.economyEvents.size)
            val event = dao.economyEvents.getValue(EconomyEvent.resultEventId(failed.resultId))
            assertEquals(EconomyEventType.FAILED_PENALTY.name, event.eventType)
            assertEquals(-1, event.lifeDelta)
            assertEquals(0, event.gemDelta)
        }

    /** The last life: the result stays durable and the wallet stops at zero rather than going negative. */
    @Test
    fun failingOnTheLastLifeLeavesZeroLivesAndADurableResult() =
        runBlocking {
            val dao =
                FakeGameCompletionDao(
                    definition,
                    PlayerEconomy(gems = 2, lives = 1, nextLifeAtEpochMillis = NOW + 1_000),
                )
            val failed = dao.catalogCompletion(PuzzleType.CROWNS, GameOutcome.FAILED).toEntity(NOW)

            dao.complete(failed)

            val wallet = dao.wallet(NOW)
            assertEquals(0, wallet.lives)
            assertEquals(2, wallet.gems)
            // The countdown that was already running is kept rather than restarted.
            assertEquals(NOW + 1_000, wallet.nextLifeAtEpochMillis)
            assertFalse(wallet.isGameplayAllowed)
            assertEquals(1, dao.results.size)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
