package com.stanisryz.logica.puzzle.core.daily

data class DailyStreak(
    val current: Int,
    val best: Int,
)

/** Pure calendar-day streak calculation shared by Android and Web. */
object DailyStreakCalculator {
    fun calculate(
        currentDate: DailyDate,
        qualifiedDates: Iterable<DailyDate>,
    ): DailyStreak {
        val currentDay = currentDate.toDailyEpochDay()
        val days =
            qualifiedDates
                .asSequence()
                .map(DailyDate::toDailyEpochDay)
                .filter { it <= currentDay }
                .toSet()
        if (days.isEmpty()) return DailyStreak(current = 0, best = 0)

        var best = 0
        var run = 0
        var previous: Long? = null
        days.sorted().forEach { day ->
            run = if (previous?.plus(1L) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }

        var cursor = if (currentDay in days) currentDay else currentDay - 1L
        var current = 0
        while (cursor in days) {
            current++
            cursor--
        }
        return DailyStreak(current = current, best = best)
    }
}

/** Proleptic-Gregorian day identity matching java.time.LocalDate without using java.time in common code. */
private fun DailyDate.toDailyEpochDay(): Long {
    val year = getYear().toLong()
    val month = getMonthValue().toLong()
    var total = 365L * year
    total +=
        if (year >= 0L) {
            (year + 3L) / 4L - (year + 99L) / 100L + (year + 399L) / 400L
        } else {
            -(year / -4L - year / -100L + year / -400L)
        }
    total += (367L * month - 362L) / 12L
    total += getDayOfMonth() - 1L
    if (month > 2L) total -= if (isLeapYear(year)) 1L else 2L
    return total - DAYS_0000_TO_1970
}

private fun isLeapYear(year: Long): Boolean = year % 4L == 0L && (year % 100L != 0L || year % 400L == 0L)

private const val DAYS_0000_TO_1970 = 719_528L
