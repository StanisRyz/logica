package com.stanisryz.logica.statistics

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StatisticsAggregatorTest {
    @Test
    fun aggregatesBalanceAndCrownsWithoutChangingDailyStreaks() {
        val today = LocalDate.of(2026, 8, 8)
        val results =
            listOf(
                result("easy", Difficulty.EASY, GameSessionScope.CATALOG, hintsUsed = 1),
                result(
                    id = "daily",
                    difficulty = Difficulty.MEDIUM,
                    scope = GameSessionScope.DAILY,
                    hintsUsed = 2,
                    challengeDate = today.minusDays(1),
                ),
                result("hard", Difficulty.HARD, GameSessionScope.CATALOG, hintsUsed = 0),
                result(
                    "crowns",
                    Difficulty.EXPERT,
                    GameSessionScope.CATALOG,
                    hintsUsed = 3,
                    puzzleType = PuzzleType.CROWNS,
                ),
                result(
                    "sudoku-solved",
                    Difficulty.HARD,
                    GameSessionScope.CATALOG,
                    hintsUsed = 4,
                    puzzleType = PuzzleType.SUDOKU,
                ),
                result(
                    "sudoku-failed",
                    Difficulty.MEDIUM,
                    GameSessionScope.CATALOG,
                    hintsUsed = 5,
                    puzzleType = PuzzleType.SUDOKU,
                    outcome = GameOutcome.FAILED,
                ),
                result(
                    "2048-solved",
                    Difficulty.MEDIUM,
                    GameSessionScope.CATALOG,
                    hintsUsed = 0,
                    puzzleType = PuzzleType.GAME_2048,
                ),
                result(
                    "2048-failed",
                    Difficulty.HARD,
                    GameSessionScope.CATALOG,
                    hintsUsed = 0,
                    puzzleType = PuzzleType.GAME_2048,
                    outcome = GameOutcome.FAILED,
                ),
            )
        val completedDailyDates =
            listOf(
                today.minusDays(7),
                today.minusDays(6),
                today.minusDays(5),
                today.minusDays(2),
                today.minusDays(1),
                today.minusDays(1),
            )

        val snapshot = StatisticsAggregator.aggregate(today, results, completedDailyDates)
        val statistics = snapshot.statistics

        assertEquals(6, statistics.totalCompletedResults)
        assertEquals(5, statistics.completedDailyCount)
        assertEquals(15, statistics.totalHintsUsed)
        assertEquals(2, statistics.currentDailyStreak)
        assertEquals(3, statistics.bestDailyStreak)
        assertEquals(3, statistics.byPuzzleType.getValue(PuzzleType.BALANCE).totalCompleted)
        assertEquals(
            mapOf(
                Difficulty.EASY to 1,
                Difficulty.MEDIUM to 1,
                Difficulty.HARD to 1,
                Difficulty.EXPERT to 0,
            ),
            statistics.byPuzzleType.getValue(PuzzleType.BALANCE).countsByDifficulty,
        )
        assertEquals(1, statistics.byPuzzleType.getValue(PuzzleType.CROWNS).totalCompleted)
        assertEquals(
            1,
            statistics.byPuzzleType
                .getValue(PuzzleType.CROWNS)
                .countsByDifficulty
                .getValue(Difficulty.EXPERT),
        )
        assertEquals(2, statistics.sudoku.played)
        assertEquals(1, statistics.sudoku.solved)
        assertEquals(1, statistics.sudoku.failed)
        assertEquals(9, statistics.sudoku.hintsUsed)
        assertEquals(1, statistics.sudoku.solvedByDifficulty.getValue(Difficulty.HARD))
        assertEquals(0, statistics.sudoku.solvedByDifficulty.getValue(Difficulty.MEDIUM))
        assertEquals(2, statistics.game2048.played)
        assertEquals(1, statistics.game2048.solved)
        assertEquals(1, statistics.game2048.failed)
        assertEquals(1, statistics.game2048.solvedByDifficulty.getValue(Difficulty.MEDIUM))
        assertEquals(0, statistics.game2048.solvedByDifficulty.getValue(Difficulty.HARD))
        assertEquals(2, snapshot.dailyHintsUsedByDate[today.minusDays(1)])
    }

    private fun result(
        id: String,
        difficulty: Difficulty,
        scope: GameSessionScope,
        hintsUsed: Int,
        challengeDate: LocalDate? = null,
        puzzleType: PuzzleType = PuzzleType.BALANCE,
        outcome: GameOutcome = GameOutcome.SOLVED,
    ): GameResult =
        GameResult(
            resultId = id,
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = PuzzleSeed(id.hashCode().toLong()),
            generatorVersion = GeneratorVersion(1),
            sessionScope = scope,
            hintsUsed = hintsUsed,
            completedAt = Instant.ofEpochMilli(1_000),
            outcome = outcome,
            challengeDate = challengeDate,
            dailyPolicyVersion = challengeDate?.let { DailyPolicyVersion(1) },
        )
}
