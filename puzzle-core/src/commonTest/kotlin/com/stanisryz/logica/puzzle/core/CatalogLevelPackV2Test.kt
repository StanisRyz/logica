package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.ByteArrayCatalogLevelPackInput
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackError
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Nonogram Level Pack V2 names its generator by slot parity; every other bucket keeps its header's. */
class CatalogLevelPackV2Test {
    @Test
    fun nonogramV2AlternatesPicturesAndSymmetricLevelsBySlot() {
        val pack = pack(CatalogLevelPackVersion.V2, PuzzleType.NONOGRAM, GeneratorVersion(3))

        val versions = (1..4).map { level -> pack.definition(level, CatalogLevelPackVersion.V2).generatorVersion.value }
        val seeds = (1..4).map { level -> pack.definition(level, CatalogLevelPackVersion.V2).seed.value }

        assertEquals(listOf(3, 4, 3, 4), versions)
        assertEquals(listOf(10L, 20L, 30L, 40L), seeds)
        assertEquals(CatalogLevelPackVersion.V2, CatalogLevelPacks.activePackVersion(PuzzleType.NONOGRAM))
        assertEquals(CatalogLevelPackVersion.V1, CatalogLevelPacks.activePackVersion(PuzzleType.BALANCE))
    }

    @Test
    fun aNonogramV2BucketNamingAnotherGeneratorIsCorrupt() {
        val pack = pack(CatalogLevelPackVersion.V2, PuzzleType.NONOGRAM, GeneratorVersion(1))

        val resolved = pack.resolve(CatalogLevelId(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelNumber(1), CatalogLevelPackVersion.V2))

        assertEquals(CatalogLevelPackError.CORRUPT_ASSET, assertIs<CatalogLevelPackResult.Failure>(resolved).error)
    }

    @Test
    fun nonogramV1KeepsItsOneGenerator() {
        val pack = pack(CatalogLevelPackVersion.V1, PuzzleType.NONOGRAM, GeneratorVersion(1))

        assertEquals(listOf(1, 1, 1, 1), (1..4).map { pack.definition(it, CatalogLevelPackVersion.V1).generatorVersion.value })
    }

    private fun pack(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        generatorVersion: GeneratorVersion,
    ): BinaryCatalogLevelPack {
        val bytes =
            CatalogLevelPackFormat.header(packVersion, puzzleType, Difficulty.EASY, recordCount = 4, generatorVersion = generatorVersion) +
                listOf(10L, 20L, 30L, 40L).map { CatalogLevelPackFormat.record(PuzzleSeed(it)) }.reduce(ByteArray::plus)
        return BinaryCatalogLevelPack(source = { _, _, _ -> ByteArrayCatalogLevelPackInput(bytes) }, expectedRecordCount = 4)
    }

    private fun BinaryCatalogLevelPack.definition(
        level: Int,
        packVersion: CatalogLevelPackVersion,
    ) = assertIs<CatalogLevelPackResult.Success<com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition>>(
        resolve(CatalogLevelId(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelNumber(level), packVersion)),
    ).value
}
