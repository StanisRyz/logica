package com.stanisryz.logica.puzzle.core.daily

/**
 * Saving a Daily streak broken by one missed day. A saved day counts for the streak only — never as
 * a played or completed Daily — and hosts pass it to [DailyStreakCalculator] beside the qualified
 * dates. The numbers come from the host's economy policy, since the core knows no prices.
 */
object DailyStreakRestore {
    /**
     * The epoch day that can be saved today, or null. It is yesterday when yesterday does not count,
     * the day before does, the streak ending there is at least [minimumStreak] days long, and no other
     * saved day lies within [cooldownDays] of it. Today itself may already count: the streak was still
     * broken yesterday. Only yesterday is ever offered, so the offer is gone tomorrow.
     */
    fun restorableEpochDay(
        today: DailyDate,
        qualifiedDates: Iterable<DailyDate>,
        restoredEpochDays: Set<Long>,
        minimumStreak: Int,
        cooldownDays: Int,
    ): Long? {
        val todayDay = today.toDailyEpochDay()
        val counted = qualifiedDates.mapTo(mutableSetOf(), DailyDate::toDailyEpochDay) + restoredEpochDays
        val yesterday = todayDay - 1L
        if (yesterday in counted) return null
        var cursor = yesterday - 1L
        var streak = 0
        while (cursor in counted) {
            streak++
            cursor--
        }
        if (streak < minimumStreak) return null
        if (restoredEpochDays.any { kotlin.math.abs(yesterday - it) < cooldownDays }) return null
        return yesterday
    }
}
