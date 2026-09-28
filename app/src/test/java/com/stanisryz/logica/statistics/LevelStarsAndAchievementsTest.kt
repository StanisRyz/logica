package com.stanisryz.logica.statistics

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.ui.profile.Achievement
import com.stanisryz.logica.ui.profile.unlockedAchievementIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LevelStarsAndAchievementsTest {
    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun eachLevelCountsItsBestStarsOnce() {
        val results =
            listOf(
                solved("a", level = 1, stars = 1),
                solved("b", level = 1, stars = 3), // a replay improved level 1
                solved("c", level = 2, stars = 2),
                solved("d", level = 3, stars = null), // recorded before stars
                GameResult(
                    resultId = "e",
                    puzzleType = PuzzleType.BALANCE,
                    difficulty = Difficulty.EASY,
                    puzzleSeed = PuzzleSeed(5),
                    generatorVersion = GeneratorVersion(1),
                    resultScope = GameResultScope.CATALOG,
                    hintsUsed = 0,
                    completedAt = Instant.ofEpochMilli(1_000),
                    outcome = GameOutcome.FAILED,
                    catalogLevel = level(4),
                ),
            )

        val profile = StatisticsAggregator.aggregate(today, results, emptyList()).statistics.toProfileStatistics()

        val stars = checkNotNull(profile.stars)
        assertEquals(5L, stars.total)
        assertEquals(5L, stars.forGame(PuzzleType.BALANCE))
        assertEquals(1L, stars.perfectLevels)
        assertEquals(setOf(Achievement.FIRST_SOLVE.id), profile.unlockedAchievementIds())
        assertEquals(4L, Achievement.SOLVER_50.progress(profile))
        assertTrue(Achievement.ALL_GAMES.progress(profile) == 1L)
    }

    private fun level(number: Int) =
        CatalogLevelId(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelNumber(number), CatalogLevelPackVersion.V1)

    private fun solved(
        id: String,
        level: Int,
        stars: Int?,
    ) = GameResult(
        resultId = id,
        puzzleType = PuzzleType.BALANCE,
        difficulty = Difficulty.EASY,
        puzzleSeed = PuzzleSeed(level.toLong()),
        generatorVersion = GeneratorVersion(1),
        resultScope = GameResultScope.CATALOG,
        hintsUsed = 0,
        completedAt = Instant.ofEpochMilli(1_000),
        catalogLevel = level(level),
        stars = stars,
    )
}
