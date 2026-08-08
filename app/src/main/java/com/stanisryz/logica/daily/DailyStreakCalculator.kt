package com.stanisryz.logica.daily

import java.time.LocalDate

internal data class DailyStreak(
    val current: Int,
    val best: Int,
)

internal object DailyStreakCalculator {
    fun calculate(
        currentDate: LocalDate,
        completedDates: Iterable<LocalDate>,
    ): DailyStreak {
        val dates = completedDates.filterNot { it.isAfter(currentDate) }.toSortedSet()
        if (dates.isEmpty()) return DailyStreak(current = 0, best = 0)

        var best = 0
        var run = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            run = if (previous?.plusDays(1) == date) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }

        val currentEnd = if (currentDate in dates) currentDate else currentDate.minusDays(1)
        var current = 0
        var cursor = currentEnd
        while (cursor in dates) {
            current++
            cursor = cursor.minusDays(1)
        }
        return DailyStreak(current = current, best = best)
    }
}
