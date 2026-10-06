package com.stanisryz.logica.puzzle.core.daily

/**
 * Past Daily days a player may still play. Hosts decide which days are open and what opening one
 * costs; this only says which days the archive holds and what each day contains.
 */
object DailyArchive {
    /** The archive's days before [today], newest first: yesterday back to [days] days ago. */
    fun dates(
        today: DailyDate,
        days: Int,
    ): List<DailyDate> {
        val todayDay = today.toDailyEpochDay()
        return (1..days).map { dailyDateOfEpochDay(todayDay - it) }
    }

    /** Whether [date] is one of the archive's days; today and the future never are. */
    fun contains(
        today: DailyDate,
        date: DailyDate,
        days: Int,
    ): Boolean = (today.toDailyEpochDay() - date.toDailyEpochDay()) in 1L..days.toLong()

    /**
     * What a past day contains: the policy of its persisted run, which never changes, or the newest
     * policy for a day the player never started. Both are deterministic by the date alone.
     */
    fun definitionFor(
        date: DailyDate,
        persistedPolicy: DailyPolicyVersion?,
    ): DailyChallengeDefinition =
        DailyChallengePolicyResolver.definitionFor(date, persistedPolicy ?: DailyChallengePolicyResolver.NEW_RUN_VERSION)
}
