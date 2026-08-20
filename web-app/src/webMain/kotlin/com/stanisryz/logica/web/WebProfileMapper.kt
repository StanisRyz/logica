package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.Game2048ProfileStatistics
import com.stanisryz.logica.ui.profile.ProfileAttemptDistribution
import com.stanisryz.logica.ui.profile.ProfileDifficultyCounts
import com.stanisryz.logica.ui.profile.ProfileStatistics
import com.stanisryz.logica.ui.profile.SolvedPuzzleProfileStatistics
import com.stanisryz.logica.ui.profile.SudokuProfileStatistics
import com.stanisryz.logica.ui.profile.WordProfileStatistics

/** Removes installation components and maps the current Player aggregate into shared presentation. */
internal fun WebStatisticsAggregate.toProfileStatistics(): ProfileStatistics {
    val all = totals()
    val word = totals(PuzzleType.WORD)
    val sudoku = totals(PuzzleType.SUDOKU)
    val game2048 = totals(PuzzleType.GAME_2048)
    return ProfileStatistics(
        totalSolved = all.solved,
        totalHintsUsed = all.hints,
        completedTerminalResults = all.played,
        balance = solvedPuzzleStatistics(PuzzleType.BALANCE),
        crowns = solvedPuzzleStatistics(PuzzleType.CROWNS),
        sudoku =
            SudokuProfileStatistics(
                played = sudoku.played,
                solved = sudoku.solved,
                failed = sudoku.failed,
                hintsUsed = sudoku.hints,
                solvedByDifficulty = solvedDifficultyCounts(PuzzleType.SUDOKU),
            ),
        game2048 =
            Game2048ProfileStatistics(
                played = game2048.played,
                solved = game2048.solved,
                failed = game2048.failed,
                solvedByDifficulty = solvedDifficultyCounts(PuzzleType.GAME_2048),
            ),
        word =
            WordProfileStatistics(
                played = word.played,
                solved = word.solved,
                failed = word.failed,
                solvedAttemptDistribution = ProfileAttemptDistribution.from(word.wordSolvedAttempts),
            ),
        dailyMetrics = null,
    )
}

private fun WebStatisticsAggregate.solvedPuzzleStatistics(puzzleType: PuzzleType): SolvedPuzzleProfileStatistics =
    SolvedPuzzleProfileStatistics(
        totalSolved = totals(puzzleType).solved,
        solvedByDifficulty = solvedDifficultyCounts(puzzleType),
    )

private fun WebStatisticsAggregate.solvedDifficultyCounts(puzzleType: PuzzleType): ProfileDifficultyCounts =
    ProfileDifficultyCounts.from(
        Difficulty.entries.associateWith { difficulty ->
            totals(puzzleType = puzzleType, difficulty = difficulty).solved
        },
    )
