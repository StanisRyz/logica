package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import java.time.LocalDate

internal data class GameStatistics(
    val totalCompletedResults: Int,
    val completedDailyCount: Int,
    val totalHintsUsed: Int,
    val currentDailyStreak: Int,
    val bestDailyStreak: Int,
    val completionCountsByPuzzleType: Map<PuzzleType, Int>,
    val balanceCountsByDifficulty: Map<Difficulty, Int>,
) {
    val totalCompletedBalance: Int
        get() = completionCountsByPuzzleType[PuzzleType.BALANCE] ?: 0
}

internal data class StatisticsSnapshot(
    val statistics: GameStatistics,
    val dailyHintsUsedByDate: Map<LocalDate, Int>,
)

internal object StatisticsAggregator {
    fun aggregate(
        currentDate: LocalDate,
        results: List<GameResult>,
        completedDailyDates: Iterable<LocalDate>,
    ): StatisticsSnapshot {
        val dailyDates = completedDailyDates.filterNot { it.isAfter(currentDate) }.toSet()
        val streak = DailyStreakCalculator.calculate(currentDate, dailyDates)
        val puzzleCounts = results.groupingBy(GameResult::puzzleType).eachCount()
        val balanceResults = results.filter { it.puzzleType == PuzzleType.BALANCE }
        val difficultyCounts =
            Difficulty.entries.associateWith { difficulty ->
                balanceResults.count { it.difficulty == difficulty }
            }
        val dailyHints =
            results
                .asSequence()
                .filter { it.sessionScope == GameSessionScope.DAILY }
                .mapNotNull { result -> result.challengeDate?.let { it to result.hintsUsed } }
                .toMap()
        return StatisticsSnapshot(
            statistics =
                GameStatistics(
                    totalCompletedResults = results.size,
                    completedDailyCount = dailyDates.size,
                    totalHintsUsed = results.sumOf(GameResult::hintsUsed),
                    currentDailyStreak = streak.current,
                    bestDailyStreak = streak.best,
                    completionCountsByPuzzleType = puzzleCounts,
                    balanceCountsByDifficulty = difficultyCounts,
                ),
            dailyHintsUsedByDate = dailyHints,
        )
    }
}
