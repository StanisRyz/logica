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

/** Civil date from days since 1970-01-01 (proleptic Gregorian), matching `LocalDate.ofEpochDay`. */
actual fun dailyDateOfEpochDay(epochDay: Long): DailyDate {
    val shifted = epochDay + 719_468L
    val era = (if (shifted >= 0L) shifted else shifted - 146_096L) / 146_097L
    val dayOfEra = shifted - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthIndex = (5L * dayOfYear + 2L) / 153L
    val day = dayOfYear - (153L * monthIndex + 2L) / 5L + 1L
    val month = if (monthIndex < 10L) monthIndex + 3L else monthIndex - 9L
    val year = yearOfEra + era * 400L + if (month <= 2L) 1L else 0L
    return DailyDate(year.toInt(), month.toInt(), day.toInt())
}
