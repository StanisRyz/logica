package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.ProfileUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebProfileMapperTest {
    @Test
    fun mapsAggregateWithoutDailyAndKeepsFailedOnlyStatisticsNonEmpty() {
        val aggregate =
            WebStatisticsAggregate(
                buckets =
                    mapOf(
                        WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.HARD) to
                            WebStatisticsCounters(played = 2, failed = 2),
                        WebStatisticsBucket(PuzzleType.WORD, Difficulty.MEDIUM) to
                            WebStatisticsCounters(
                                played = 3,
                                solved = 2,
                                failed = 1,
                                wordSolvedAttempts = mapOf(2 to 1, 5 to 1),
                            ),
                        WebStatisticsBucket(PuzzleType.SUDOKU, Difficulty.EXPERT) to
                            WebStatisticsCounters(played = 1, solved = 1, hints = 4),
                    ),
            )

        val profile = aggregate.toProfileStatistics()

        assertNull(profile.dailyMetrics)
        assertEquals(6L, profile.completedTerminalResults)
        assertEquals(0L, profile.balance.totalSolved)
        assertEquals(1L, profile.sudoku.solvedByDifficulty.expert)
        assertEquals(1L, profile.word.failed)
        assertEquals(1L, profile.word.solvedAttemptDistribution[2])
        assertEquals(0L, profile.word.solvedAttemptDistribution[6])

        val failedOnly =
            WebStatisticsAggregate(
                mapOf(
                    WebStatisticsBucket(PuzzleType.CROWNS, Difficulty.EASY) to
                        WebStatisticsCounters(played = 1, failed = 1),
                ),
            ).toProfileStatistics()
        assertIs<ProfileUiState.Ready>(failedOnly.toUiState())
    }
}
