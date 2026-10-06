package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import java.time.Instant
import java.time.LocalDate

/** Android Room/result adaptation; the calendar streak calculation itself lives in puzzle-core. */
internal object DailyStreakQualification {
    /**
     * The dates that keep the streak. A date opened in the Daily archive counts only through what
     * was solved before it was opened ([archiveUnlockedAt]): play in the archive fills the calendar
     * and the full-Daily count but never the streak. A run completes with its last solved result,
     * so a completed run counts only when that result came before the archive too. Dates never
     * opened in the archive, every one before the archive existed, keep counting as they did.
     */
    fun qualifiedDates(
        completedRunDates: Iterable<LocalDate>,
        dailyResults: Iterable<GameResult>,
        archiveUnlockedAt: Map<LocalDate, Instant> = emptyMap(),
    ): Set<LocalDate> {
        val solvedDaily =
            dailyResults.filter { result ->
                result.challengeDate != null &&
                    result.dailyPolicyVersion != null &&
                    result.resultScope == GameResultScope.DAILY &&
                    result.outcome == GameOutcome.SOLVED
            }

        fun GameResult.onTime(): Boolean = archiveUnlockedAt[challengeDate]?.let { completedAt.isBefore(it) } ?: true
        val dates =
            completedRunDates.filterTo(mutableSetOf()) { date ->
                val unlockedAt = archiveUnlockedAt[date] ?: return@filterTo true
                val completedAt = solvedDaily.filter { it.challengeDate == date }.maxOfOrNull { it.completedAt }
                completedAt != null && completedAt.isBefore(unlockedAt)
            }
        solvedDaily.forEach { result ->
            if (result.onTime() && DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(checkNotNull(result.dailyPolicyVersion))) {
                dates += checkNotNull(result.challengeDate)
            }
        }
        return dates
    }
}
