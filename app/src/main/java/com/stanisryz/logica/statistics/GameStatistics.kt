package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
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
    val word: WordStatistics,
)

internal data class PuzzleStatistics(
    val totalCompleted: Int,
    val countsByDifficulty: Map<Difficulty, Int>,
)

/** Word-specific metrics; unlike the other puzzles, a Word result can be terminal without a solve. */
internal data class WordStatistics(
    val played: Int,
    val solved: Int,
    val failed: Int,
    val solvedAttemptCounts: Map<Int, Int>,
) {
    val winRatePercent: Int get() = if (played == 0) 0 else solved * 100 / played
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
                    word = wordStatistics(results),
                ),
            dailyHintsUsedByDate = dailyHints,
        )
    }

    private fun wordStatistics(results: List<GameResult>): WordStatistics {
        val wordResults = results.filter { it.puzzleType == PuzzleType.WORD }
        val solvedResults = wordResults.filter { it.outcome == GameOutcome.SOLVED }
        return WordStatistics(
            played = wordResults.size,
            solved = solvedResults.size,
            failed = wordResults.count { it.outcome == GameOutcome.FAILED },
            solvedAttemptCounts =
                (1..WordRules.MAXIMUM_ATTEMPTS).associateWith { attempts ->
                    solvedResults.count { it.attemptsUsed == attempts }
                },
        )
    }
}
