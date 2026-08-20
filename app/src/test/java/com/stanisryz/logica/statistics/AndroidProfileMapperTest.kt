package com.stanisryz.logica.statistics

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProfileMapperTest {
    @Test
    fun mapsDailyAndNormalizesDifficultyCounters() {
        val statistics =
            GameStatistics(
                totalCompletedResults = 12,
                completedDailyCount = 3,
                totalHintsUsed = 7,
                currentDailyStreak = 2,
                bestDailyStreak = 5,
                byPuzzleType =
                    mapOf(
                        PuzzleType.BALANCE to
                            PuzzleStatistics(
                                totalCompleted = 4,
                                countsByDifficulty = mapOf(Difficulty.EASY to 4),
                            ),
                        PuzzleType.CROWNS to
                            PuzzleStatistics(
                                totalCompleted = 3,
                                countsByDifficulty = mapOf(Difficulty.EXPERT to 3),
                            ),
                    ),
                word =
                    WordStatistics(
                        played = 3,
                        solved = 2,
                        failed = 1,
                        solvedAttemptCounts = mapOf(1 to 1, 4 to 1),
                    ),
                sudoku =
                    SudokuStatistics(
                        played = 2,
                        solved = 1,
                        failed = 1,
                        hintsUsed = 5,
                        solvedByDifficulty = mapOf(Difficulty.MEDIUM to 1),
                    ),
                game2048 =
                    Game2048Statistics(
                        played = 2,
                        solved = 2,
                        failed = 0,
                        solvedByDifficulty = mapOf(Difficulty.HARD to 2),
                    ),
            )

        val state = StatisticsUiState.Ready(statistics).toProfileUiState()

        assertTrue(state is ProfileUiState.Ready)
        val profile = (state as ProfileUiState.Ready).statistics
        assertEquals(12L, profile.totalSolved)
        assertEquals(3L, profile.dailyMetrics?.completedCount)
        assertEquals(2L, profile.dailyMetrics?.currentStreak)
        assertEquals(5L, profile.dailyMetrics?.bestStreak)
        assertEquals(4L, profile.balance.solvedByDifficulty.easy)
        assertEquals(0L, profile.balance.solvedByDifficulty.expert)
        assertEquals(3L, profile.crowns.solvedByDifficulty.expert)
        assertEquals(1L, profile.sudoku.solvedByDifficulty.medium)
        assertEquals(2L, profile.game2048.solvedByDifficulty.hard)
        assertEquals(1L, profile.word.solvedAttemptDistribution[4])
        assertEquals(0L, profile.word.solvedAttemptDistribution[6])
    }
}
