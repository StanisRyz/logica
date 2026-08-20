package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import java.time.LocalDate

/** Android Room/result adaptation; the calendar streak calculation itself lives in puzzle-core. */
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
