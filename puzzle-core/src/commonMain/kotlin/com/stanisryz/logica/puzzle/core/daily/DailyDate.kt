package com.stanisryz.logica.puzzle.core.daily

/** Platform date shape used by deterministic Daily policy code. */
expect class DailyDate {
    fun getYear(): Int

    fun getMonthValue(): Int

    fun getDayOfMonth(): Int
}
