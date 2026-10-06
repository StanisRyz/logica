package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The frozen Nonogram Level Pack V2 over the frozen V3 picture library: odd levels are real pictures,
 * even levels symmetric V4 boards, any N consecutive picture levels show every picture once, and the
 * library itself stays as frozen (the golden values were recorded when it was written).
 */
class NonogramPackV2Test {
    private val root = listOf(File("."), File("..")).first { File(it, "puzzle-data/levels").isDirectory }
    private val pack =
        BinaryCatalogLevelPack(
            CatalogLevelPackSource { packVersion, puzzleType, difficulty ->
                File(root, "puzzle-data/" + CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))
                    .takeIf(File::isFile)
                    ?.inputStream()
            },
        )

    @Test
    fun oddLevelsArePicturesAndEvenLevelsSymmetricBoards() {
        Difficulty.entries.forEach { difficulty ->
            val size = NonogramPictureLibraryV3.sizeFor(difficulty)
            listOf(1, 2, 37, 38, 9_999, 10_000, 10_001).forEach { level ->
                val definition = definition(difficulty, level)
                val puzzle =
                    if (level % 2 == 1) {
                        assertEquals(3, definition.generatorVersion.value)
                        NonogramGeneratorV3().generate(definition.seed, difficulty)
                    } else {
                        assertEquals(4, definition.generatorVersion.value)
                        NonogramGeneratorV4().generate(definition.seed, difficulty)
                    }
                assertEquals(size, puzzle.size, "$difficulty level $level")
                assertEquals(definition.generatorVersion, puzzle.id.generatorVersion)
            }
            assertEquals(definition(difficulty, 1).seed, definition(difficulty, 10_001).seed)
            // The k-th even level is the k-th accepted V4 seed; every seed has been accepted so far.
            assertEquals(PuzzleSeed(1), definition(difficulty, 2).seed)
            assertEquals(PuzzleSeed(19), definition(difficulty, 38).seed)
        }
    }

    @Test
    fun everyCycleOfPictureLevelsShowsEveryPictureOnce() {
        Difficulty.entries.forEach { difficulty ->
            val count = NonogramPictureLibraryV3.count(difficulty)
            val indices = (0 until count).map { definition(difficulty, 37 + 2 * it).seed.value.toInt() }
            assertEquals((0 until count).toSet(), indices.toSet(), "$difficulty from level 37")
        }
    }

    @Test
    fun theFrozenLibraryKeepsItsPicturesAndItsHardExpertRule() {
        val golden =
            mapOf(
                Difficulty.EASY to Golden(160, "airplane", 6617071626032045037L, -6385556881499443326L),
                Difficulty.MEDIUM to Golden(375, "air-traffic-control", 2474591683454926075L, 6337789527540010306L),
                Difficulty.HARD to Golden(293, "air-traffic-control", -8258838058321055011L, 3765517163541592182L),
                Difficulty.EXPERT to Golden(95, "airplane-landing", 4181140298811839323L, -5731512300972806260L),
            )
        Difficulty.entries.forEach { difficulty ->
            val count = NonogramPictureLibraryV3.count(difficulty)
            val first = NonogramGeneratorV3().generate(PuzzleSeed(0), difficulty)
            val last = NonogramGeneratorV3().generate(PuzzleSeed(count - 1L), difficulty)
            val expected = golden.getValue(difficulty)
            assertEquals(expected, Golden(count, NonogramPictureLibraryV3.key(difficulty, 0), fnv(first.solution), fnv(last.solution)))
            assertFailsWith<IllegalArgumentException> { NonogramGeneratorV3().generate(PuzzleSeed(count.toLong()), difficulty) }
            (0 until count).forEach { index ->
                val puzzle = NonogramGeneratorV3().generate(PuzzleSeed(index.toLong()), difficulty)
                val sweeps = NonogramLineSolver.solveWithEffort(puzzle.size, puzzle.rowClues, puzzle.columnClues)?.sweeps
                assertTrue(sweeps != null, "$difficulty/$index is not solved by line logic")
                if (difficulty == Difficulty.HARD) assertTrue(sweeps <= NonogramGeneratorV4.HARD_MAX_SWEEPS)
                if (difficulty == Difficulty.EXPERT) assertTrue(sweeps > NonogramGeneratorV4.HARD_MAX_SWEEPS)
            }
        }
    }

    private data class Golden(
        val count: Int,
        val firstKey: String,
        val firstHash: Long,
        val lastHash: Long,
    )

    private fun definition(
        difficulty: Difficulty,
        level: Int,
    ): CatalogLevelDefinition =
        when (
            val resolved =
                pack.resolve(CatalogLevelId(PuzzleType.NONOGRAM, difficulty, CatalogLevelNumber(level), CatalogLevelPackVersion.V2))
        ) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }

    private fun fnv(cells: List<Boolean>): Long {
        var hash = -3750763034362895579L
        cells.forEach { hash = (hash xor if (it) 1L else 0L) * 1099511628211L }
        return hash
    }
}
