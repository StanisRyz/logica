package com.stanisryz.logica.result

import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyAggregateCompletionTest {
    @Test
    fun firstV2EntryKeepsRunInProgressAndFinalEntryCompletesIdempotently() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))
            val dao = FakeGameCompletionDao(definition)
            val first = definition.entries[0].completion(definition, "daily-0", hintsUsed = 1).toEntity(1_000)
            val final = definition.entries[1].completion(definition, "daily-1", hintsUsed = 2).toEntity(2_000)

            dao.complete(first)

            assertEquals(DailyChallengeStatus.COMPLETED.name, dao.challenge(first).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertNull(dao.run.completedAtEpochMillis)

            val finalResult = dao.complete(final)
            val repeatedResult = dao.complete(final)

            assertEquals(finalResult, repeatedResult)
            assertEquals(DailyRunStatus.COMPLETED.name, dao.run.status)
            assertEquals(2_000L, dao.run.completedAtEpochMillis)
            assertEquals(2, dao.results.size)
        }

    @Test
    fun aFailedAttemptStaysDurableButOnlyASolvedOneCompletesTheEntryAndRun() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))
            val dao = FakeGameCompletionDao(definition)
            val entry = definition.entries[0]
            val failed =
                entry.completion(definition, "daily-0", hintsUsed = 1, outcome = GameOutcome.FAILED).toEntity(1_000)

            dao.complete(failed)

            // The result is durable, the entry stays open for another attempt, nothing else moved.
            assertNotNull(dao.results["daily-0"])
            assertEquals(DailyChallengeStatus.IN_PROGRESS.name, dao.challenge(failed).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)

            val solved = entry.completion(definition, "daily-0-retry", hintsUsed = 0).toEntity(2_000)
            dao.complete(solved)

            assertEquals(2, dao.results.size)
            assertEquals(DailyChallengeStatus.COMPLETED.name, dao.challenge(solved).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)

            dao.complete(definition.entries[1].completion(definition, "daily-1", hintsUsed = 2).toEntity(3_000))

            assertEquals(DailyRunStatus.COMPLETED.name, dao.run.status)
            assertEquals(3, dao.results.size)
        }

    @Test
    fun aCatalogCompletionNeverTouchesDailyLifecycleState() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))
            val dao = FakeGameCompletionDao(definition)
            val catalogCrowns = dao.catalogCompletion(PuzzleType.CROWNS).toEntity(1_000)
            val dailyBalance = definition.entries[0].completion(definition, "daily-0", hintsUsed = 1).toEntity(2_000)

            dao.complete(catalogCrowns)

            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(DailyChallengeStatus.IN_PROGRESS.name, dao.challenge(dailyBalance).status)
            // A Daily result never records Catalog level identity, and never advances progression.
            assertNull(dao.currentLevel(PuzzleType.BALANCE))

            dao.complete(dailyBalance)

            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(2, dao.results.size)
            assertNull(dao.currentLevel(PuzzleType.BALANCE))
        }

    private fun DailyPuzzleEntry.completion(
        definition: DailyChallengeDefinition,
        resultId: String,
        hintsUsed: Int,
        outcome: GameOutcome = GameOutcome.SOLVED,
    ): GameCompletion =
        GameCompletion(
            resultId = resultId,
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = seed,
            generatorVersion = generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = hintsUsed,
            outcome = outcome,
            challengeDate = definition.challengeDate,
            dailyPolicyVersion = definition.policyVersion,
        )
}
