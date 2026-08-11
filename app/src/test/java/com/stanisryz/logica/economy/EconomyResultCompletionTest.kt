package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * One terminal result must move the wallet exactly zero or one time. Recomposition, a retried save,
 * or a repeated callback all run the very same completion transaction again, so the ledger event —
 * not the caller — is what makes the reward and the penalty happen only once.
 */
class EconomyResultCompletionTest {
    private val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))

    @Test
    fun aSolvedResultGrantsOneGemExactlyOnceHoweverOftenItIsPersisted() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val solved = dao.catalogCompletion(PuzzleType.SUDOKU).toEntity(NOW)

            dao.complete(solved)
            dao.complete(solved)
            dao.complete(solved)

            val wallet = dao.wallet(NOW)
            assertEquals(EconomyRules.SOLVED_GEM_REWARD, wallet.gems)
            assertEquals(EconomyRules.STARTING_LIVES, wallet.lives)
            assertEquals(1, dao.results.size)
            assertEquals(1, dao.economyEvents.size)
            val event = dao.economyEvents.getValue(EconomyEvent.resultEventId(solved.resultId))
            assertEquals(EconomyEventType.SOLVED_REWARD.name, event.eventType)
            assertEquals(1, event.gemDelta)
            assertEquals(0, event.lifeDelta)
        }

    @Test
    fun aFailedResultSpendsOneLifeExactlyOnceAndStartsOneRegenerationCountdown() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val failed = dao.catalogCompletion(PuzzleType.SUDOKU, GameOutcome.FAILED).toEntity(NOW)

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
