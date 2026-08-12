package com.stanisryz.logica.catalog

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one invariant the whole level system rests on: a public level is the same puzzle for everyone,
 * forever, and the 10 000-slot cycle keeps producing new levels without new content.
 */
class FrozenCatalogLevelPackTest {
    private val pack = BinaryCatalogLevelPack(BundledLevelPackFiles)

    @Test
    fun everyBucketResolvesTheSameFrozenDefinitionForTheSameLevel() {
        CatalogLevelPacks.PUZZLE_TYPES.forEach { puzzleType ->
            Difficulty.entries.forEach { difficulty ->
                val levelId = CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(127))
                val first = pack.require(levelId)
                val second = pack.require(levelId)

                assertEquals("$puzzleType/$difficulty is not stable", first, second)
                assertEquals(levelId, first.levelId)
                assertTrue("$puzzleType/$difficulty has no generator version", first.generatorVersion.value > 0)
            }
        }
    }

    @Test
    fun levelTenThousandAndOneReusesContentSlotOneButStaysItsOwnLevel() {
        val first = CatalogLevelId(PuzzleType.BALANCE, Difficulty.MEDIUM, CatalogLevelNumber(1))
        val cycled =
            CatalogLevelId(
                PuzzleType.BALANCE,
                Difficulty.MEDIUM,
                CatalogLevelNumber(CatalogLevelPacks.SLOTS_PER_BUCKET + 1),
            )

        assertEquals(1, cycled.contentSlot.value)
        assertEquals(pack.require(first).seed, pack.require(cycled).seed)
        // Same content, different level: a different displayed number and a different completion.
        assertNotEquals(first, cycled)
        assertNotEquals(pack.require(first).levelId, pack.require(cycled).levelId)
        assertNotEquals(resultIdOf(first), resultIdOf(cycled))
        assertEquals(
            2,
            pack
                .require(CatalogLevelId(PuzzleType.BALANCE, Difficulty.MEDIUM, CatalogLevelNumber(10_002)))
                .levelId.contentSlot.value,
        )
    }

    @Test
    fun missingContentFailsCleanlyInsteadOfSubstitutingAnotherPuzzle() {
        val emptyPack = BinaryCatalogLevelPack(CatalogLevelPackSource { _, _, _ -> null })
        val failure = emptyPack.resolve(CatalogLevelId(PuzzleType.CROWNS, Difficulty.HARD, CatalogLevelNumber(3)))

        assertTrue("$failure", failure is CatalogLevelPackResult.Failure)
    }

    private fun BinaryCatalogLevelPack.require(levelId: CatalogLevelId) =
        when (val resolved = resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error("${levelId.puzzleType}/${levelId.difficulty}: ${resolved.detail}")
        }

    /** The completion identity a Catalog attempt at this level would carry. */
    private fun resultIdOf(levelId: CatalogLevelId): String =
        GameAttempt(
            attemptId = "attempt",
            puzzleType = levelId.puzzleType,
            difficulty = levelId.difficulty,
            seed = pack.require(levelId).seed,
            generatorVersion = pack.require(levelId).generatorVersion,
            context = GameAttemptContext.Catalog(levelId),
        ).resultId

    /** Reads the shipped assets straight off disk; the runtime reads exactly the same bytes. */
    private object BundledLevelPackFiles : CatalogLevelPackSource {
        private val assetsDirectory: File =
            listOf(File("src/main/assets"), File("app/src/main/assets"))
                .firstOrNull(File::isDirectory)
                ?: error("The bundled assets directory was not found.")

        override fun open(
            packVersion: CatalogLevelPackVersion,
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ) = File(assetsDirectory, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))
            .takeIf(File::isFile)
            ?.inputStream()
    }
}
