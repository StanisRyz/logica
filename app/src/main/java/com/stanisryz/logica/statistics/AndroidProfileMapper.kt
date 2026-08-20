package com.stanisryz.logica.statistics

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.DailyProfileMetrics
import com.stanisryz.logica.ui.profile.Game2048ProfileStatistics
import com.stanisryz.logica.ui.profile.ProfileAttemptDistribution
import com.stanisryz.logica.ui.profile.ProfileDifficultyCounts
import com.stanisryz.logica.ui.profile.ProfileStatistics
import com.stanisryz.logica.ui.profile.ProfileUiState
import com.stanisryz.logica.ui.profile.SolvedPuzzleProfileStatistics
import com.stanisryz.logica.ui.profile.SudokuProfileStatistics
import com.stanisryz.logica.ui.profile.WordProfileStatistics

internal fun StatisticsUiState.toProfileUiState(): ProfileUiState =
    when (this) {
        StatisticsUiState.Loading -> ProfileUiState.Loading
        StatisticsUiState.Error -> ProfileUiState.Error
        is StatisticsUiState.Ready -> statistics.toProfileStatistics().toUiState()
    }

internal fun GameStatistics.toProfileStatistics(): ProfileStatistics {
    val wordProfile = word.toProfileStatistics()
    val sudokuProfile = sudoku.toProfileStatistics()
    val game2048Profile = game2048.toProfileStatistics()
    return ProfileStatistics(
        totalSolved = totalCompletedResults.toLong(),
        totalHintsUsed = totalHintsUsed.toLong(),
        completedTerminalResults =
            totalCompletedResults.toLong() +
                wordProfile.failed +
                sudokuProfile.failed +
                game2048Profile.failed,
        balance = requireNotNull(byPuzzleType[PuzzleType.BALANCE]).toProfileStatistics(),
        crowns = requireNotNull(byPuzzleType[PuzzleType.CROWNS]).toProfileStatistics(),
        sudoku = sudokuProfile,
        game2048 = game2048Profile,
        word = wordProfile,
        dailyMetrics =
            DailyProfileMetrics(
                completedCount = completedDailyCount.toLong(),
                currentStreak = currentDailyStreak.toLong(),
                bestStreak = bestDailyStreak.toLong(),
            ),
    )
}

private fun PuzzleStatistics.toProfileStatistics(): SolvedPuzzleProfileStatistics =
    SolvedPuzzleProfileStatistics(
        totalSolved = totalCompleted.toLong(),
        solvedByDifficulty =
            ProfileDifficultyCounts.from(
                countsByDifficulty.mapValues { (_, count) -> count.toLong() },
            ),
    )

private fun SudokuStatistics.toProfileStatistics(): SudokuProfileStatistics =
    SudokuProfileStatistics(
        played = played.toLong(),
        solved = solved.toLong(),
        failed = failed.toLong(),
        hintsUsed = hintsUsed.toLong(),
        solvedByDifficulty =
            ProfileDifficultyCounts.from(
                solvedByDifficulty.mapValues { (_, count) -> count.toLong() },
            ),
    )

private fun Game2048Statistics.toProfileStatistics(): Game2048ProfileStatistics =
    Game2048ProfileStatistics(
        played = played.toLong(),
        solved = solved.toLong(),
        failed = failed.toLong(),
        solvedByDifficulty =
            ProfileDifficultyCounts.from(
                solvedByDifficulty.mapValues { (_, count) -> count.toLong() },
            ),
    )

private fun WordStatistics.toProfileStatistics(): WordProfileStatistics =
    WordProfileStatistics(
        played = played.toLong(),
        solved = solved.toLong(),
        failed = failed.toLong(),
        solvedAttemptDistribution =
            ProfileAttemptDistribution.from(
                solvedAttemptCounts.mapValues { (_, count) -> count.toLong() },
            ),
    )
