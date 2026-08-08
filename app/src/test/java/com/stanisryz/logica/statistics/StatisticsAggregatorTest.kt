package com.stanisryz.logica.statistics

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StatisticsAggregatorTest {
    @Test
    fun aggregatesCatalogDailyHintsStreaksAndBalanceDifficulties() {
        val today = LocalDate.of(2026, 8, 8)
        val results =
            listOf(
                result("easy", Difficulty.EASY, GameSessionScope.CATALOG, hintsUsed = 1),
                result(
                    id = "daily",
                    difficulty = Difficulty.MEDIUM,
                    scope = GameSessionScope.DAILY,
                    hintsUsed = 2,
                    challengeDate = today.minusDays(1),
                ),
                result("hard", Difficulty.HARD, GameSessionScope.CATALOG, hintsUsed = 0),
            )
        val completedDailyDates =
            listOf(
                today.minusDays(7),
                today.minusDays(6),
                today.minusDays(5),
                today.minusDays(2),
                today.minusDays(1),
                today.minusDays(1),
            )

        val snapshot = StatisticsAggregator.aggregate(today, results, completedDailyDates)
        val statistics = snapshot.statistics

        assertEquals(3, statistics.totalCompletedResults)
        assertEquals(5, statistics.completedDailyCount)
        assertEquals(3, statistics.totalHintsUsed)
        assertEquals(2, statistics.currentDailyStreak)
        assertEquals(3, statistics.bestDailyStreak)
        assertEquals(3, statistics.totalCompletedBalance)
        assertEquals(
            mapOf(
                Difficulty.EASY to 1,
                Difficulty.MEDIUM to 1,
                Difficulty.HARD to 1,
                Difficulty.EXPERT to 0,
            ),
            statistics.balanceCountsByDifficulty,
        )
        assertEquals(2, snapshot.dailyHintsUsedByDate[today.minusDays(1)])
    }

    private fun result(
        id: String,
        difficulty: Difficulty,
        scope: GameSessionScope,
        hintsUsed: Int,
        challengeDate: LocalDate? = null,
    ): GameResult =
        GameResult(
            resultId = id,
            puzzleType = PuzzleType.BALANCE,
            difficulty = difficulty,
            puzzleSeed = PuzzleSeed(id.hashCode().toLong()),
            generatorVersion = GeneratorVersion(1),
            sessionScope = scope,
            hintsUsed = hintsUsed,
            completedAt = Instant.ofEpochMilli(1_000),
            challengeDate = challengeDate,
            dailyPolicyVersion = challengeDate?.let { DailyPolicyVersion(1) },
        )
}
