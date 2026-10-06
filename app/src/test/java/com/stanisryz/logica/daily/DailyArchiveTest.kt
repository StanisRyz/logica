package com.stanisryz.logica.daily

import com.stanisryz.logica.economy.DailyArchivePayment
import com.stanisryz.logica.economy.DailyArchiveUnlockOutcome
import com.stanisryz.logica.economy.EconomyEvent
import com.stanisryz.logica.economy.EconomyEventType
import com.stanisryz.logica.economy.FakeEconomyDao
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV8
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.statistics.StatisticsAggregator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * An archive day opens once through a `daily_archive:<day>` ledger row. What is solved there fills
 * the calendar and the full-Daily count, but only what was solved on the day itself keeps the streak.
 */
class DailyArchiveTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 20)
    private val day = today.minusDays(3)
    private val openedAt: Instant = Instant.parse("2026-10-20T09:00:00Z")

    @Test
    fun aDayOpensOnceAndARepeatChargesNothing() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 12))
            val epochDay = day.toEpochDay()

            assertEquals(DailyArchiveUnlockOutcome.Unlocked, dao.unlockDailyArchive(epochDay, DailyArchivePayment.GEMS, 0))
            assertEquals(DailyArchiveUnlockOutcome.AlreadyUnlocked, dao.unlockDailyArchive(epochDay, DailyArchivePayment.GEMS, 0))
            assertEquals(DailyArchiveUnlockOutcome.AlreadyUnlocked, dao.unlockDailyArchive(epochDay, DailyArchivePayment.REWARDED, 0))

            assertEquals(7, dao.wallet(0).gems)
            val row = dao.events.getValue(EconomyEvent.dailyArchiveEventId(epochDay))
            assertEquals(EconomyEventType.DAILY_ARCHIVE_UNLOCK.name, row.eventType)
            assertEquals(-5, row.gemDelta)
        }

    @Test
    fun aShortBalanceOpensNothingWhileAnAdOrAStartedDayOpensForFree() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 3))

            assertEquals(DailyArchiveUnlockOutcome.NotEnoughGems(2), dao.unlockDailyArchive(1L, DailyArchivePayment.GEMS, 0))
            assertTrue(dao.events.isEmpty())
            assertEquals(DailyArchiveUnlockOutcome.Unlocked, dao.unlockDailyArchive(1L, DailyArchivePayment.REWARDED, 0))
            // A day the player started on its own date is theirs already.
            assertEquals(DailyArchiveUnlockOutcome.Unlocked, dao.unlockDailyArchive(2L, DailyArchivePayment.FREE, 0))

            assertEquals(3, dao.wallet(0).gems)
            assertEquals("rewarded", dao.events.getValue(EconomyEvent.dailyArchiveEventId(1L)).sourceId)
            assertEquals("free", dao.events.getValue(EconomyEvent.dailyArchiveEventId(2L)).sourceId)
            assertEquals(0, dao.events.getValue(EconomyEvent.dailyArchiveEventId(2L)).gemDelta)
        }

    @Test
    fun archivePlayFillsTheCalendarAndTheFullDailyCountButNotTheStreak() {
        val streakDays = listOf(today.minusDays(2), today.minusDays(1), today).map { solved(it, PuzzleType.BALANCE, Instant.EPOCH) }
        // The archive day, every entry solved after it was opened, completing its run.
        val archive = DailyChallengePolicyV8.definitionFor(day).entries.map { solved(day, it.puzzleType, openedAt.plusSeconds(60)) }

        val snapshot =
            StatisticsAggregator.aggregate(
                today,
                streakDays + archive,
                completedDailyDates = listOf(day),
                archiveUnlockedAt = mapOf(day to openedAt),
            )

        assertEquals(3, snapshot.statistics.currentDailyStreak)
        assertEquals(3, snapshot.statistics.bestDailyStreak)
        assertEquals(1, snapshot.statistics.completedDailyCount)
        assertTrue(day.dayOfMonth in snapshot.statistics.dailyMonth!!.completedDays)
        assertEquals(
            setOf(today.minusDays(2), today.minusDays(1), today),
            DailyStreakQualification.qualifiedDates(
                listOf(day),
                streakDays + archive,
                mapOf(day to openedAt),
            ),
        )
    }

    @Test
    fun solvesBeforeTheArchiveOpenedAndOlderDaysKeepTheirStreak() {
        val earlier = openedAt.minusSeconds(3_600)
        val onTime = solved(day, PuzzleType.BALANCE, earlier)
        val late = solved(day, PuzzleType.CROWNS, openedAt.plusSeconds(60))
        val older = solved(today.minusDays(10), PuzzleType.SUDOKU, Instant.EPOCH)

        val qualified =
            DailyStreakQualification.qualifiedDates(
                completedRunDates = emptyList(),
                dailyResults = listOf(onTime, late, older),
                archiveUnlockedAt = mapOf(day to openedAt),
            )

        // The day's own solve from before the archive still counts; a day never opened is untouched.
        assertEquals(setOf(day, today.minusDays(10)), qualified)
        assertEquals(
            setOf(today.minusDays(10)),
            DailyStreakQualification.qualifiedDates(emptyList(), listOf(late, older), mapOf(day to openedAt)),
        )
    }

    private fun solved(
        date: LocalDate,
        puzzleType: PuzzleType,
        completedAt: Instant,
    ): GameResult {
        val definition = DailyChallengePolicyV8.definitionFor(date)
        val entry = definition.entries.single { it.puzzleType == puzzleType }
        return GameResult(
            resultId = "daily-$date-$puzzleType",
            puzzleType = puzzleType,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            resultScope = GameResultScope.DAILY,
            hintsUsed = 0,
            completedAt = completedAt,
            outcome = GameOutcome.SOLVED,
            challengeDate = date,
            dailyPolicyVersion = definition.policyVersion,
        )
    }
}
