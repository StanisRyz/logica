package com.stanisryz.logica.puzzle.core.daily

/** Explicit browser date value; deterministic Daily code never reads the browser clock. */
actual class DailyDate(
    private val year: Int,
    private val month: Int,
    private val day: Int,
) {
    init {
        require(month in 1..12) { "Month must be within 1..12." }
        require(day in 1..daysInMonth(year, month)) { "Day $day is invalid for $year-$month." }
    }

    actual fun getYear(): Int = year

    actual fun getMonthValue(): Int = month

    actual fun getDayOfMonth(): Int = day

    override fun equals(other: Any?): Boolean = other is DailyDate && year == other.year && month == other.month && day == other.day

    override fun hashCode(): Int = 31 * (31 * year + month) + day

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    private companion object {
        fun daysInMonth(
            year: Int,
            month: Int,
        ): Int =
            when (month) {
                2 -> if (isLeapYear(year)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }

        fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}
