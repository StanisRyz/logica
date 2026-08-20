package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyStreakQualification
import com.stanisryz.logica.puzzle.core.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import java.time.LocalDate

internal data class GameStatistics(
    val totalCompletedResults: Int,
    val completedDailyCount: Int,
    val totalHintsUsed: Int,
    val currentDailyStreak: Int,
    val bestDailyStreak: Int,
    val byPuzzleType: Map<PuzzleType, PuzzleStatistics>,
    val word: WordStatistics,
    val sudoku: SudokuStatistics = SudokuStatistics.EMPTY,
    val game2048: Game2048Statistics = Game2048Statistics.EMPTY,
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

/** Sudoku keeps terminal-attempt counts while solved difficulty counters retain solved semantics. */
internal data class SudokuStatistics(
    val played: Int,
    val solved: Int,
    val failed: Int,
    val hintsUsed: Int,
    val solvedByDifficulty: Map<Difficulty, Int>,
) {
    companion object {
        val EMPTY =
            SudokuStatistics(
                played = 0,
                solved = 0,
                failed = 0,
                hintsUsed = 0,
                solvedByDifficulty = Difficulty.entries.associateWith { 0 },
            )
    }
}

/** 2048 records terminal outcomes and solved counts by its target-based difficulty. */
internal data class Game2048Statistics(
    val played: Int,
    val solved: Int,
    val failed: Int,
    val solvedByDifficulty: Map<Difficulty, Int>,
) {
    companion object {
        val EMPTY =
            Game2048Statistics(
                played = 0,
                solved = 0,
                failed = 0,
                solvedByDifficulty = Difficulty.entries.associateWith { 0 },
            )
    }
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
        // Two different concepts: how many Dailies were finished in full, and which dates keep the
        // streak alive. From Policy V5 on one solved entry qualifies a date without completing it.
        val fullyCompletedDailyDates = completedDailyDates.filterNot { it.isAfter(currentDate) }.toSet()
        val streakDates = DailyStreakQualification.qualifiedDates(fullyCompletedDailyDates, results)
        val streak = DailyStreakCalculator.calculate(currentDate, streakDates)
        // Every "solved" metric counts solved attempts only; a failed attempt stays durable but
        // never inflates them. Word keeps its own played/solved/failed breakdown below.
        val solvedResults = results.filter { it.outcome == GameOutcome.SOLVED }
        val puzzleStatistics =
            listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.SUDOKU).associateWith { puzzleType ->
                val puzzleResults = solvedResults.filter { it.puzzleType == puzzleType }
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
                .filter { it.resultScope == GameResultScope.DAILY }
                .mapNotNull { result -> result.challengeDate?.let { it to result.hintsUsed } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, hints) -> hints.sum() }
        return StatisticsSnapshot(
            statistics =
                GameStatistics(
                    totalCompletedResults = solvedResults.size,
                    completedDailyCount = fullyCompletedDailyDates.size,
                    totalHintsUsed = results.sumOf(GameResult::hintsUsed),
                    currentDailyStreak = streak.current,
                    bestDailyStreak = streak.best,
                    byPuzzleType = puzzleStatistics,
                    word = wordStatistics(results),
                    sudoku = sudokuStatistics(results),
                    game2048 = game2048Statistics(results),
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

    private fun sudokuStatistics(results: List<GameResult>): SudokuStatistics {
        val sudokuResults = results.filter { it.puzzleType == PuzzleType.SUDOKU }
        val solvedResults = sudokuResults.filter { it.outcome == GameOutcome.SOLVED }
        return SudokuStatistics(
            played = sudokuResults.size,
            solved = solvedResults.size,
            failed = sudokuResults.count { it.outcome == GameOutcome.FAILED },
            hintsUsed = sudokuResults.sumOf(GameResult::hintsUsed),
            solvedByDifficulty =
                Difficulty.entries.associateWith { difficulty ->
                    solvedResults.count { it.difficulty == difficulty }
                },
        )
    }

    private fun game2048Statistics(results: List<GameResult>): Game2048Statistics {
        val gameResults = results.filter { it.puzzleType == PuzzleType.GAME_2048 }
        val solvedResults = gameResults.filter { it.outcome == GameOutcome.SOLVED }
        return Game2048Statistics(
            played = gameResults.size,
            solved = solvedResults.size,
            failed = gameResults.count { it.outcome == GameOutcome.FAILED },
            solvedByDifficulty =
                Difficulty.entries.associateWith { difficulty ->
                    solvedResults.count { it.difficulty == difficulty }
                },
        )
    }
}
