package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import java.time.LocalDate

/** Only what the completed Daily card and the share formatter need for one puzzle's result. */
internal data class DailyPuzzleResultSummary(
    val puzzleType: PuzzleType,
    val outcome: GameOutcome,
    val attemptsUsed: Int?,
)

internal data class DailyResultSummary(
    val challengeDate: LocalDate,
    val policyVersion: DailyPolicyVersion,
    val entries: List<DailyPuzzleResultSummary>,
    val completedCount: Int,
    val totalCount: Int,
    val currentStreak: Int,
    val bestStreak: Int,
)

/**
 * Builds a shareable Daily result only when persisted terminal results exactly match the resolved
 * Daily definition, ordered by policy rather than completion time. A completed run with a
 * missing/mismatched result returns null so sharing stays unavailable instead of showing an
 * incomplete or misleading summary; the normal completion UI does not depend on this.
 */
internal object DailyResultSummaryBuilder {
    fun build(
        definition: DailyChallengeDefinition,
        run: SavedDailyRun,
        results: List<GameResult>,
        currentStreak: Int,
        bestStreak: Int,
    ): DailyResultSummary? {
        if (run.status != DailyRunStatus.COMPLETED) return null
        if (run.challengeDate != definition.challengeDate || run.policyVersion != definition.policyVersion) {
            return null
        }
        val resultByPuzzleType =
            results
                .asSequence()
                .filter { it.sessionScope == GameSessionScope.DAILY }
                .filter { it.challengeDate == definition.challengeDate && it.dailyPolicyVersion == definition.policyVersion }
                .associateBy { it.puzzleType }
        val entries =
            definition.entries.map { entry ->
                val result = resultByPuzzleType[entry.puzzleType] ?: return null
                DailyPuzzleResultSummary(entry.puzzleType, result.outcome, result.attemptsUsed)
            }
        return DailyResultSummary(
            challengeDate = definition.challengeDate,
            policyVersion = definition.policyVersion,
            entries = entries,
            completedCount = entries.size,
            totalCount = definition.entries.size,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
        )
    }
}
