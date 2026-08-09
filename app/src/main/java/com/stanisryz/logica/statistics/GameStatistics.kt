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
    val byPuzzleType: Map<PuzzleType, PuzzleStatistics>,
)

internal data class PuzzleStatistics(
    val totalCompleted: Int,
    val countsByDifficulty: Map<Difficulty, Int>,
)

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
        val puzzleStatistics =
            listOf(PuzzleType.BALANCE, PuzzleType.CROWNS).associateWith { puzzleType ->
                val puzzleResults = results.filter { it.puzzleType == puzzleType }
                PuzzleStatistics(
                    totalCompleted = puzzleResults.size,
                    countsByDifficulty =
                        Difficulty.entries.associateWith { difficulty ->
                            puzzleResults.count { it.difficulty == difficulty }
                        },
                )
            }
        val dailyHints =
            results
                .asSequence()
                .filter { it.sessionScope == GameSessionScope.DAILY }
                .mapNotNull { result -> result.challengeDate?.let { it to result.hintsUsed } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, hints) -> hints.sum() }
        return StatisticsSnapshot(
            statistics =
                GameStatistics(
                    totalCompletedResults = results.size,
                    completedDailyCount = dailyDates.size,
                    totalHintsUsed = results.sumOf(GameResult::hintsUsed),
                    currentDailyStreak = streak.current,
                    bestDailyStreak = streak.best,
                    byPuzzleType = puzzleStatistics,
                ),
            dailyHintsUsedByDate = dailyHints,
        )
    }
}
