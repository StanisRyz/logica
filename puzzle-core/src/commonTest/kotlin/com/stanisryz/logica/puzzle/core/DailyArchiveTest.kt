package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.daily.DailyArchive
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV7
import com.stanisryz.logica.puzzle.core.daily.dailyDateOfEpochDay
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Daily archive's window and contents, the same on every target. */
class DailyArchiveTest {
    // 2026-10-06.
    private val today = dailyDateOfEpochDay(20_732L)

    @Test
    fun epochDaysRoundTripOnEveryTarget() {
        assertEquals(2026, today.getYear())
        assertEquals(10, today.getMonthValue())
        assertEquals(6, today.getDayOfMonth())
        // Leap days, century years, and the days around the epoch.
        listOf(-719_528L, -1L, 0L, 59L, 10_956L, 11_016L, 11_017L, 20_732L, 2_932_896L).forEach { day ->
            assertEquals(day, dailyDateOfEpochDay(day).toDailyEpochDay())
        }
        (19_000L..21_000L).forEach { day -> assertEquals(day, dailyDateOfEpochDay(day).toDailyEpochDay()) }
    }

    @Test
    fun theWindowHoldsThePastThirtyDaysNewestFirst() {
        val dates = DailyArchive.dates(today, 30)

        assertEquals(30, dates.size)
        assertEquals(today.toDailyEpochDay() - 1, dates.first().toDailyEpochDay())
        assertEquals(today.toDailyEpochDay() - 30, dates.last().toDailyEpochDay())
        assertTrue(DailyArchive.contains(today, dates.last(), 30))
        assertFalse(DailyArchive.contains(today, today, 30))
        assertFalse(DailyArchive.contains(today, dailyDateOfEpochDay(today.toDailyEpochDay() - 31), 30))
        assertFalse(DailyArchive.contains(today, dailyDateOfEpochDay(today.toDailyEpochDay() + 1), 30))
    }

    @Test
    fun aStartedDayKeepsItsPolicyAndANewOneUsesTheNewest() {
        val date = dailyDateOfEpochDay(today.toDailyEpochDay() - 3)

        val fresh = DailyArchive.definitionFor(date, persistedPolicy = null)
        val started = DailyArchive.definitionFor(date, persistedPolicy = DailyChallengePolicyV7.VERSION)

        assertEquals(DailyChallengePolicyResolver.NEW_RUN_VERSION, fresh.policyVersion)
        assertFalse(fresh.entries.any { it.puzzleType == PuzzleType.WORD })
        assertEquals(DailyChallengePolicyV7.VERSION, started.policyVersion)
        assertTrue(started.entries.any { it.puzzleType == PuzzleType.WORD })
        // Deterministic by the date alone: the same day gives the same puzzles again.
        assertEquals(fresh, DailyArchive.definitionFor(date, persistedPolicy = null))
    }
}
