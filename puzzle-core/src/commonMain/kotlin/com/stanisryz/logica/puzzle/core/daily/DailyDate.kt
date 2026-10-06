package com.stanisryz.logica.puzzle.core.daily

/** Platform date shape used by deterministic Daily policy code. */
expect class DailyDate {
    fun getYear(): Int

    fun getMonthValue(): Int

    fun getDayOfMonth(): Int
}

/** The date of a [toDailyEpochDay] value, the inverse of it on every target. */
expect fun dailyDateOfEpochDay(epochDay: Long): DailyDate
