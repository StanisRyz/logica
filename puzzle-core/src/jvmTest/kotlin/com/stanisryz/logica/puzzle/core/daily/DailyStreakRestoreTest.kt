package com.stanisryz.logica.puzzle.core.daily

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A streak broken yesterday can be saved when it ran at least three days up to the day before, and
 * two saved days stay a week apart; a saved day keeps the streak alive like a played one.
 */
class DailyStreakRestoreTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun aThreeDayStreakBrokenYesterdayCanBeSaved() {
        val played = days(-4, -3, -2)

        assertEquals(today.minusDays(1).toEpochDay(), restorable(played))
        // Two days are not enough.
        assertNull(restorable(days(-3, -2)))
    }

    @Test
    fun todayAlreadyPlayedStillOffersYesterday() {
        assertEquals(today.minusDays(1).toEpochDay(), restorable(days(-4, -3, -2, 0)))
    }

    @Test
    fun onlyYesterdayIsOfferedAndNeverTwice() {
        // Yesterday counts: nothing to save.
        assertNull(restorable(days(-3, -2, -1)))
        // Two days missed: the streak ended the day before yesterday's yesterday, nothing is offered.
        assertNull(restorable(days(-5, -4, -3)))
        // Yesterday already saved.
        assertNull(restorable(days(-4, -3, -2), restored = setOf(today.minusDays(1).toEpochDay())))
    }

    @Test
    fun savedDaysStayAWeekApart() {
        val played = days(-12, -11, -10, -9, -8, -6, -5, -4, -3, -2)
        // A save six days before yesterday blocks; seven days before allows it.
        assertNull(restorable(played, restored = setOf(today.minusDays(7).toEpochDay())))
        assertEquals(today.minusDays(1).toEpochDay(), restorable(played, restored = setOf(today.minusDays(8).toEpochDay())))
    }

    @Test
    fun aSavedDayKeepsTheStreakAlive() {
        val played = days(-4, -3, -2, 0)
        val saved = setOf(today.minusDays(1).toEpochDay())

        assertEquals(DailyStreak(current = 1, best = 3), DailyStreakCalculator.calculate(today, played))
        assertEquals(DailyStreak(current = 5, best = 5), DailyStreakCalculator.calculate(today, played, saved))
    }

    private fun restorable(
        played: List<LocalDate>,
        restored: Set<Long> = emptySet(),
    ): Long? = DailyStreakRestore.restorableEpochDay(today, played, restored, minimumStreak = 3, cooldownDays = 7)

    private fun days(vararg offsets: Int): List<LocalDate> = offsets.map { today.plusDays(it.toLong()) }
}
