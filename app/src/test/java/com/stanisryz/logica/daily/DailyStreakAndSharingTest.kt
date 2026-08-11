package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV4
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.statistics.StatisticsAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Streak qualification and full Daily completion stopped being the same thing in V5: one solved
 * entry keeps the streak, five completed entries finish the Daily, and only the second unlocks
 * sharing. The historical V1–V4 rule must survive that change untouched.
 */
class DailyStreakAndSharingTest {
    private val today: LocalDate = LocalDate.of(2026, 8, 11)

    @Test
    fun historicalV4DatesKeepTheirFullCompletionStreakRule() {
        val partialDate = today.minusDays(3)
        val completedDate = today.minusDays(2)
        val v4Partial = DailyChallengePolicyV4.definitionFor(partialDate)
        val v4Completed = DailyChallengePolicyV4.definitionFor(completedDate)
        val results =
            listOf(
                dailyResult(v4Partial, PuzzleType.BALANCE, GameOutcome.SOLVED),
                dailyResult(v4Partial, PuzzleType.CROWNS, GameOutcome.FAILED),
            ) + v4Completed.entries.map { entry -> dailyResult(v4Completed, entry.puzzleType, GameOutcome.SOLVED) }

        // Only the completed run's date is reported by `daily_runs`; the partial one never is.
        val qualified = DailyStreakQualification.qualifiedDates(listOf(completedDate), results)

        assertEquals(setOf(completedDate), qualified)
        assertFalse(partialDate in qualified)
        val snapshot = StatisticsAggregator.aggregate(today, results, listOf(completedDate))
        assertEquals(1, snapshot.statistics.completedDailyCount)
        assertEquals(0, snapshot.statistics.currentDailyStreak)
        assertEquals(1, snapshot.statistics.bestDailyStreak)
    }

    @Test
    fun oneSolvedV5EntryQualifiesItsDateExactlyOnceWithoutCompletingTheDaily() {
        val yesterday = today.minusDays(1)
        val yesterdayDefinition = DailyChallengePolicyV5.definitionFor(yesterday)
        val todayDefinition = DailyChallengePolicyV5.definitionFor(today)
        val onlyBalance = listOf(dailyResult(yesterdayDefinition, PuzzleType.BALANCE, GameOutcome.SOLVED))
        val threeToday =
            listOf(PuzzleType.CROWNS, PuzzleType.SUDOKU, PuzzleType.GAME_2048).map { puzzleType ->
                dailyResult(todayDefinition, puzzleType, GameOutcome.SOLVED)
            }
        val failedOnly = listOf(dailyResult(todayDefinition, PuzzleType.WORD, GameOutcome.FAILED))

        // No run has completed, so nothing is a fully completed Daily. Yesterday's single solve keeps
        // the streak alive; today's failed attempt qualifies nothing by itself.
        val oneEach = StatisticsAggregator.aggregate(today, onlyBalance + failedOnly, emptyList())
        assertEquals(0, oneEach.statistics.completedDailyCount)
        assertEquals(1, oneEach.statistics.currentDailyStreak)
        assertFalse(DailyStreakQualification.qualifiedDates(emptyList(), failedOnly).contains(today))

        val twoDays = StatisticsAggregator.aggregate(today, onlyBalance + threeToday + failedOnly, emptyList())
        assertEquals(setOf(yesterday, today), DailyStreakQualification.qualifiedDates(emptyList(), onlyBalance + threeToday))
        assertEquals(2, twoDays.statistics.currentDailyStreak)
        assertEquals(2, twoDays.statistics.bestDailyStreak)
        // Three solved games on one date still contribute one single streak day.
        assertEquals(0, twoDays.statistics.completedDailyCount)

        val full =
            StatisticsAggregator.aggregate(
                today,
                onlyBalance + todayDefinition.entries.map { dailyResult(todayDefinition, it.puzzleType, GameOutcome.SOLVED) },
                listOf(today),
            )
        assertEquals(2, full.statistics.currentDailyStreak)
        assertEquals(1, full.statistics.completedDailyCount)
    }

    @Test
    fun theFullV5ShareListsAllFiveEntriesAndStaysSpoilerFree() {
        val definition = DailyChallengePolicyV5.definitionFor(today)
        val results = definition.entries.map { entry -> dailyResult(definition, entry.puzzleType, GameOutcome.SOLVED) }
        val summary =
            DailyResultSummaryBuilder.build(definition, completedRun(definition), results, currentStreak = 4, bestStreak = 9)
        assertNotNull(summary)
        assertEquals(5, summary!!.totalCount)
        assertEquals(5, summary.completedCount)

        val text = DailyShareFormatter.format(summary)

        assertEquals(
            "Логика дня — 11 августа\n\n" +
                "Баланс  ✓\n" +
                "Короны  ✓\n" +
                "Слово   4/6\n" +
                "Судоку  ✓\n" +
                "2048    ✓\n\n" +
                "5 из 5\n" +
                "🔥 Серия: 4 дня",
            text,
        )
        // No seed, no board, no fingerprint, no answer: the share carries outcomes only.
        definition.entries.forEach { entry -> assertFalse(text.contains(entry.seed.value.toString())) }

        // A streak-qualified 1/5 has no full-Daily summary at all, so it cannot be shared.
        assertNull(
            DailyResultSummaryBuilder.build(
                definition,
                completedRun(definition),
                listOf(dailyResult(definition, PuzzleType.SUDOKU, GameOutcome.SOLVED)),
                currentStreak = 4,
                bestStreak = 9,
            ),
        )
        assertTrue(DailyStreakQualification.qualifiedDates(emptyList(), results).contains(today))
    }

    private fun dailyResult(
        definition: DailyChallengeDefinition,
        puzzleType: PuzzleType,
        outcome: GameOutcome,
    ): GameResult {
        val entry = definition.entries.single { it.puzzleType == puzzleType }
        return GameResult(
            resultId = "${definition.policyVersion.value}-${definition.challengeDate}-$puzzleType-$outcome",
            puzzleType = puzzleType,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = 0,
            completedAt = Instant.EPOCH,
            outcome = outcome,
            attemptsUsed = if (puzzleType == PuzzleType.WORD) WORD_ATTEMPTS else null,
            challengeDate = definition.challengeDate,
            dailyPolicyVersion = definition.policyVersion,
        )
    }

    private fun completedRun(definition: DailyChallengeDefinition): SavedDailyRun =
        SavedDailyRun(
            challengeDate = definition.challengeDate,
            policyVersion = definition.policyVersion,
            status = DailyRunStatus.COMPLETED,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            completedAt = Instant.EPOCH,
        )

    private companion object {
        const val WORD_ATTEMPTS = 4
    }
}
