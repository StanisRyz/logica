package com.stanisryz.logica.daily

import com.stanisryz.logica.economy.EconomyEvent
import com.stanisryz.logica.economy.EconomyEventType
import com.stanisryz.logica.economy.FakeEconomyDao
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.economy.StreakRestoreOutcome
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV8
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.statistics.StatisticsAggregator
import com.stanisryz.logica.statistics.StreakRestoreOffer
import com.stanisryz.logica.statistics.toProfileStatistics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A streak broken yesterday is saved once by a ledger row keyed by that day, for gems or after an ad;
 * the saved day keeps the streak and its achievements alive but is no played or completed Daily.
 */
class StreakRestoreTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 20)
    private val yesterday = today.minusDays(1).toEpochDay()

    @Test
    fun aDaySavedForGemsIsChargedOnceAndARepeatChargesNothing() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 20))

            assertEquals(StreakRestoreOutcome.Restored, dao.restoreStreak(yesterday, withGems = true, nowEpochMillis = 0))
            assertEquals(StreakRestoreOutcome.AlreadyRestored, dao.restoreStreak(yesterday, withGems = true, nowEpochMillis = 0))
            assertEquals(StreakRestoreOutcome.AlreadyRestored, dao.restoreStreak(yesterday, withGems = false, nowEpochMillis = 0))

            assertEquals(5, dao.wallet(0).gems)
            val row = dao.events.getValue(EconomyEvent.streakRestoreEventId(yesterday))
            assertEquals(EconomyEventType.STREAK_RESTORE.name, row.eventType)
            assertEquals(-15, row.gemDelta)
        }

    @Test
    fun aShortBalanceSavesNothingAndAnAdSavesForFree() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 10))

            assertEquals(StreakRestoreOutcome.NotEnoughGems(5), dao.restoreStreak(yesterday, withGems = true, nowEpochMillis = 0))
            assertTrue(dao.events.isEmpty())

            assertEquals(StreakRestoreOutcome.Restored, dao.restoreStreak(yesterday, withGems = false, nowEpochMillis = 0))
            assertEquals(10, dao.wallet(0).gems)
            assertEquals(
                EconomyEvent.STREAK_RESTORE_REWARDED_SOURCE,
                dao.events.getValue(EconomyEvent.streakRestoreEventId(yesterday)).sourceId,
            )
        }

    @Test
    fun theSavedDayKeepsTheStreakButCountsNoDaily() {
        val results = listOf(-4L, -3L, -2L).map { offset -> solvedDaily(today.plusDays(offset)) }

        val broken = StatisticsAggregator.aggregate(today, results, emptyList())
        assertEquals(StreakRestoreOffer(yesterday, streakLength = 3), broken.streakRestore)
        assertEquals(0, broken.statistics.currentDailyStreak)

        val saved = StatisticsAggregator.aggregate(today, results, emptyList(), restoredStreakDays = setOf(yesterday))
        assertNull(saved.streakRestore)
        assertEquals(4, saved.statistics.currentDailyStreak)
        assertEquals(4, saved.statistics.bestDailyStreak)
        assertEquals(0, saved.statistics.completedDailyCount)
        assertEquals(setOf(today.dayOfMonth - 1), saved.statistics.dailyMonth?.savedDays)
        // Streak achievements read the same statistics, so they see the saved day too.
        assertEquals(
            4L,
            saved.statistics
                .toProfileStatistics()
                .dailyMetrics
                ?.currentStreak,
        )
    }

    private fun solvedDaily(date: LocalDate): GameResult {
        val definition = DailyChallengePolicyV8.definitionFor(date)
        val entry = definition.entries.single { it.puzzleType == PuzzleType.BALANCE }
        return GameResult(
            resultId = "daily-$date",
            puzzleType = PuzzleType.BALANCE,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            resultScope = GameResultScope.DAILY,
            hintsUsed = 0,
            completedAt = Instant.EPOCH,
            outcome = GameOutcome.SOLVED,
            challengeDate = date,
            dailyPolicyVersion = definition.policyVersion,
        )
    }
}
