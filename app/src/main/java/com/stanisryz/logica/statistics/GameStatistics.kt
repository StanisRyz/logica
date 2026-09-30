package com.stanisryz.logica.statistics

import com.stanisryz.logica.daily.DailyStreakQualification
import com.stanisryz.logica.puzzle.core.daily.DailyStreakCalculator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordRules
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.ui.profile.LevelStarRecord
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
    /** Block Sudoku shares 2048's shape: terminal attempts and solves per difficulty. */
    val blockSudoku: Game2048Statistics = Game2048Statistics.EMPTY,
    /** Nonogram shares Sudoku's shape: terminal attempts, hints, and solves per difficulty. */
    val nonogram: SudokuStatistics = SudokuStatistics.EMPTY,
    val dailyMonth: DailyMonthHistory? = null,
    /** The best stars of every Catalog level solved with a star count, one record per level. */
    val levelStars: List<LevelStarRecord> = emptyList(),
)

/** The current month's Daily dates for the Profile calendar: full runs and partly solved days. */
internal data class DailyMonthHistory(
    val currentDate: LocalDate,
    val completedDays: Set<Int>,
    val partialDays: Set<Int>,
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
        val solvedDailyDates =
            solvedResults
                .filter { it.resultScope == GameResultScope.DAILY }
                .mapNotNullTo(mutableSetOf()) { it.challengeDate }

        fun LocalDate.inCurrentMonth() = year == currentDate.year && month == currentDate.month && !isAfter(currentDate)
        val completedDays = fullyCompletedDailyDates.filter { it.inCurrentMonth() }.mapTo(mutableSetOf()) { it.dayOfMonth }
        val dailyMonth =
            DailyMonthHistory(
                currentDate = currentDate,
                completedDays = completedDays,
                partialDays =
                    solvedDailyDates
                        .filter { it.inCurrentMonth() }
                        .mapTo(mutableSetOf()) { it.dayOfMonth }
                        .minus(completedDays),
            )
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
                    nonogram = sudokuStatistics(results, PuzzleType.NONOGRAM),
                    game2048 = game2048Statistics(results),
                    blockSudoku = game2048Statistics(results, PuzzleType.BLOCK_SUDOKU),
                    dailyMonth = dailyMonth,
                    levelStars = bestLevelStars(solvedResults),
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

    private fun sudokuStatistics(
        results: List<GameResult>,
        puzzleType: PuzzleType = PuzzleType.SUDOKU,
    ): SudokuStatistics {
        val sudokuResults = results.filter { it.puzzleType == puzzleType }
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

    private fun game2048Statistics(
        results: List<GameResult>,
        puzzleType: PuzzleType = PuzzleType.GAME_2048,
    ): Game2048Statistics {
        val gameResults = results.filter { it.puzzleType == puzzleType }
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

/** Each Catalog level counts its best solved attempt once; results without stars count nothing. */
private fun bestLevelStars(solvedResults: List<GameResult>): List<LevelStarRecord> =
    solvedResults
        .filter { it.catalogLevel != null && it.stars != null }
        .groupBy { checkNotNull(it.catalogLevel) }
        .map { (level, results) ->
            LevelStarRecord(level.puzzleType, level.difficulty, results.maxOf { checkNotNull(it.stars) }, level.levelNumber.value)
        }
