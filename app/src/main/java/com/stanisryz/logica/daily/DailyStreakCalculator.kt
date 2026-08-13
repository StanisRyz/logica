package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import java.time.LocalDate

internal data class DailyStreak(
    val current: Int,
    val best: Int,
)

/**
 * Which calendar dates count towards the streak. This is deliberately separate from full Daily
 * completion: a completed run of any policy qualifies its date, and from Policy V5 on a single
 * solved Daily result qualifies it as well. Nothing is persisted — the qualified dates are derived
 * from durable history every time — and a set is what a date lands in, so solving two or five games
 * on one V5 date still contributes exactly one day.
 *
 * The historical V1–V4 rule is untouched: a partially solved V1–V4 date has no completed run and no
 * V5+ result, so it stays unqualified.
 */
internal object DailyStreakQualification {
    fun qualifiedDates(
        completedRunDates: Iterable<LocalDate>,
        dailyResults: Iterable<GameResult>,
    ): Set<LocalDate> {
        val dates = completedRunDates.toMutableSet()
        dailyResults.forEach { result ->
            val challengeDate = result.challengeDate ?: return@forEach
            val policyVersion = result.dailyPolicyVersion ?: return@forEach
            if (
                result.resultScope == GameResultScope.DAILY &&
                result.outcome == GameOutcome.SOLVED &&
                DailyChallengePolicyResolver.qualifiesStreakOnAnySolvedEntry(policyVersion)
            ) {
                dates += challengeDate
            }
        }
        return dates
    }
}

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
