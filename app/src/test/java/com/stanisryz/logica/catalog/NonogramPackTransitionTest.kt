package com.stanisryz.logica.catalog

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGenerators
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPictureLibraryV3
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Nonogram moved to Level Pack V2 without moving anyone: a player on V1 level 37 continues at
 * level 37 from V2, solving it advances only the V2 row, the current level is the higher of the two
 * rows, and levels below the V1 row stay V1 levels for the gallery, replays, and stars.
 */
class NonogramPackTransitionTest {
    private val completions = FakeGameCompletionDao(DailyChallengePolicyV5.definitionFor(LocalDate.of(2026, 10, 6)))
    private val repository = RoomCatalogLevelRepository(ProgressRows(completions), BinaryCatalogLevelPack(BundledFiles))

    @Test
    fun aV1PlayerContinuesAtTheSameLevelFromV2AndOnlyV2Advances() =
        runBlocking {
            completions.setCurrentLevel(PuzzleType.NONOGRAM, Difficulty.EASY, 37)

            val current = repository.currentLevelId(PuzzleType.NONOGRAM, Difficulty.EASY)
            assertEquals(37, current.levelNumber.value)
            assertEquals(CatalogLevelPackVersion.V2, current.packVersion)
            val definition = repository.resolve(current)
            // Level 37 is odd: a real picture of the frozen V3 library.
            assertEquals(3, definition.generatorVersion.value)
            val puzzle = NonogramGenerators.generate(definition.seed, Difficulty.EASY, definition.generatorVersion)
            assertEquals(NonogramPictureLibraryV3.picture(Difficulty.EASY, definition.seed.value.toInt()), puzzle.solution)

            completions.complete(nonogram(37, CatalogLevelPackVersion.V2).toEntity(1_000))

            assertEquals(38, completions.currentLevel(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelPackVersion.V2))
            assertEquals(37, completions.currentLevel(PuzzleType.NONOGRAM, Difficulty.EASY))
            val next = repository.currentLevelId(PuzzleType.NONOGRAM, Difficulty.EASY)
            assertEquals(38, next.levelNumber.value)
            // Level 38 is even: a symmetric Generator V4 board.
            assertEquals(4, repository.resolve(next).generatorVersion.value)
            // Other games stay on Level Pack V1.
            assertEquals(CatalogLevelPackVersion.V1, repository.currentLevelId(PuzzleType.BALANCE, Difficulty.EASY).packVersion)
        }

    @Test
    fun theHigherRowWinsWhenAnOlderDeviceAdvancesV1() =
        runBlocking {
            completions.setCurrentLevel(PuzzleType.NONOGRAM, Difficulty.HARD, 37)
            completions.complete(nonogram(37, CatalogLevelPackVersion.V2, Difficulty.HARD).toEntity(1_000))
            // An older game version keeps advancing the V1 row past the V2 one.
            completions.setCurrentLevel(PuzzleType.NONOGRAM, Difficulty.HARD, 45)

            assertEquals(45, repository.currentLevelId(PuzzleType.NONOGRAM, Difficulty.HARD).levelNumber.value)
            assertEquals(45, repository.observeCurrentLevel(PuzzleType.NONOGRAM, Difficulty.HARD).first().value)
            // The rating counts the cleared levels once: max(V1, V2) - 1, never the two rows added up.
            assertEquals(
                44,
                repository
                    .observeCurrentLevels(PuzzleType.NONOGRAM)
                    .first()
                    .getValue(Difficulty.HARD)
                    .value - 1,
            )

            // V2 accepts its level 45 (the V1 row) and still rejects anything beyond.
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { completions.complete(nonogram(46, CatalogLevelPackVersion.V2, Difficulty.HARD, "beyond").toEntity(2_000)) }
            }
            completions.complete(nonogram(45, CatalogLevelPackVersion.V2, Difficulty.HARD).toEntity(3_000))
            assertEquals(46, completions.currentLevel(PuzzleType.NONOGRAM, Difficulty.HARD, CatalogLevelPackVersion.V2))
            assertEquals(46, repository.currentLevelId(PuzzleType.NONOGRAM, Difficulty.HARD).levelNumber.value)
        }

    @Test
    fun levelsBelowTheV1RowAreV1LevelsForTheGalleryAndReplays() =
        runBlocking {
            completions.setCurrentLevel(PuzzleType.NONOGRAM, Difficulty.MEDIUM, 37)
            completions.complete(nonogram(37, CatalogLevelPackVersion.V2, Difficulty.MEDIUM).toEntity(1_000))
            completions.complete(nonogram(38, CatalogLevelPackVersion.V2, Difficulty.MEDIUM, stars = 1).toEntity(2_000))

            val versions = listOf(1, 10, 36, 37, 38).map { level -> level to levelId(level).packVersion.value }
            assertEquals(listOf(1 to 1, 10 to 1, 36 to 1, 37 to 2, 38 to 2), versions)
            assertEquals(1, repository.resolve(levelId(10)).generatorVersion.value)
            assertEquals(3, repository.resolve(levelId(37)).generatorVersion.value)
            assertEquals(4, repository.resolve(levelId(38)).generatorVersion.value)

            // A replay of V1 level 10 and of V2 level 38 raises only that level's stars and moves nothing.
            completions.complete(nonogram(10, CatalogLevelPackVersion.V1, Difficulty.MEDIUM, "replay-10", stars = 3).toEntity(3_000))
            completions.complete(nonogram(38, CatalogLevelPackVersion.V2, Difficulty.MEDIUM, "replay-38", stars = 3).toEntity(4_000))
            completions.complete(nonogram(38, CatalogLevelPackVersion.V2, Difficulty.MEDIUM, "replay-38b", stars = 2).toEntity(5_000))

            assertEquals(37, completions.currentLevel(PuzzleType.NONOGRAM, Difficulty.MEDIUM))
            assertEquals(39, completions.currentLevel(PuzzleType.NONOGRAM, Difficulty.MEDIUM, CatalogLevelPackVersion.V2))
            assertEquals(3, completions.findBestSolvedCatalogStars("NONOGRAM", "MEDIUM", 10, 1))
            assertEquals(null, completions.findBestSolvedCatalogStars("NONOGRAM", "MEDIUM", 10, 2))
            assertEquals(3, completions.findBestSolvedCatalogStars("NONOGRAM", "MEDIUM", 38, 2))
        }

    private suspend fun levelId(level: Int) = repository.levelId(PuzzleType.NONOGRAM, Difficulty.MEDIUM, CatalogLevelNumber(level))

    private fun nonogram(
        level: Int,
        packVersion: CatalogLevelPackVersion,
        difficulty: Difficulty = Difficulty.EASY,
        attemptId: String = "attempt",
        stars: Int? = null,
    ) = completions.catalogCompletion(
        PuzzleType.NONOGRAM,
        difficulty = difficulty,
        levelNumber = level,
        attemptId = attemptId,
        stars = stars,
        packVersion = packVersion,
    )

    /** The progression rows the completion transaction writes, read the way Room reads them. */
    private class ProgressRows(
        private val completions: FakeGameCompletionDao,
    ) : CatalogLevelProgressDao {
        override fun observeCurrentLevel(
            puzzleType: String,
            difficulty: String,
            packVersion: Int,
        ): Flow<List<Int>> = flow { emit(listOfNotNull(level(puzzleType, difficulty, packVersion))) }

        override suspend fun findCurrentLevel(
            puzzleType: String,
            difficulty: String,
            packVersion: Int,
        ): Int? = level(puzzleType, difficulty, packVersion)

        private fun level(
            puzzleType: String,
            difficulty: String,
            packVersion: Int,
        ) = completions.currentLevel(PuzzleType.valueOf(puzzleType), Difficulty.valueOf(difficulty), CatalogLevelPackVersion(packVersion))
    }

    private object BundledFiles : CatalogLevelPackSource {
        private val root = listOf(File("puzzle-data"), File("../puzzle-data")).first(File::isDirectory)

        override fun open(
            packVersion: CatalogLevelPackVersion,
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ) = File(root, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)).takeIf(File::isFile)?.inputStream()
    }
}
