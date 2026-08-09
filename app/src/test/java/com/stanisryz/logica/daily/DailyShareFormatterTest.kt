package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV4
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DailyShareFormatterTest {
    private val date: LocalDate = LocalDate.of(2026, 8, 9)

    @Test
    fun solvedDailyFormatsProgressStreakAndHidesTheWordSeed() {
        val definition = DailyChallengePolicyV4.definitionFor(date)
        val results =
            listOf(
                result(definition, PuzzleType.BALANCE, GameOutcome.SOLVED, attemptsUsed = null),
                result(definition, PuzzleType.CROWNS, GameOutcome.SOLVED, attemptsUsed = null),
                result(definition, PuzzleType.WORD, GameOutcome.SOLVED, attemptsUsed = 4),
            )
        val summary =
            DailyResultSummaryBuilder.build(definition, completedRun(definition), results, currentStreak = 8, bestStreak = 12)
        assertNotNull(summary)

        val text = DailyShareFormatter.format(summary!!)

        assertEquals(
            "Логика дня — 9 августа\n\n" +
                "Баланс  ✓\n" +
                "Короны  ✓\n" +
                "Слово   4/6\n\n" +
                "3 из 3\n" +
                "🔥 Серия: 8 дней",
            text,
        )
        val wordSeed =
            definition.entries
                .single { it.puzzleType == PuzzleType.WORD }
                .seed.value
        assertFalse(text.contains(wordSeed.toString()))
    }

    @Test
    fun failedWordStaysThreeOfThreeAndNeverShowsTheAnswer() {
        val definition = DailyChallengePolicyV4.definitionFor(date)
        val results =
            listOf(
                result(definition, PuzzleType.BALANCE, GameOutcome.SOLVED, attemptsUsed = null),
                result(definition, PuzzleType.CROWNS, GameOutcome.SOLVED, attemptsUsed = null),
                result(definition, PuzzleType.WORD, GameOutcome.FAILED, attemptsUsed = 6),
            )
        val summary =
            DailyResultSummaryBuilder.build(definition, completedRun(definition), results, currentStreak = 8, bestStreak = 12)
        assertNotNull(summary)

        val text = DailyShareFormatter.format(summary!!)

        assertTrue(text.contains("Слово   не угадано"))
        assertTrue(text.contains("3 из 3"))
        assertFalse(text.contains("6/6"))
    }

    @Test
    fun v2HistoricalRunOnlyIncludesBalanceAndCrownsAtTwoOfTwo() {
        val definition = DailyChallengePolicyV2.definitionFor(date)
        val results =
            listOf(
                result(definition, PuzzleType.BALANCE, GameOutcome.SOLVED, attemptsUsed = null),
                result(definition, PuzzleType.CROWNS, GameOutcome.SOLVED, attemptsUsed = null),
            )
        val summary =
            DailyResultSummaryBuilder.build(definition, completedRun(definition), results, currentStreak = 2, bestStreak = 5)
        assertNotNull(summary)

        val text = DailyShareFormatter.format(summary!!)

        assertFalse(text.contains("Слово"))
        assertTrue(text.contains("2 из 2"))
    }

    private fun result(
        definition: DailyChallengeDefinition,
        puzzleType: PuzzleType,
        outcome: GameOutcome,
        attemptsUsed: Int?,
    ): GameResult {
        val entry = definition.entries.single { it.puzzleType == puzzleType }
        return GameResult(
            resultId = "result-$puzzleType",
            puzzleType = puzzleType,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = 0,
            completedAt = Instant.EPOCH,
            outcome = outcome,
            attemptsUsed = attemptsUsed,
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
}
