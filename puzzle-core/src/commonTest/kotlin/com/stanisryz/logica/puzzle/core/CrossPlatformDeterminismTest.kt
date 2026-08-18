package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.ByteArrayCatalogLevelPackInput
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1
import kotlin.test.Test
import kotlin.test.assertEquals

class CrossPlatformDeterminismTest {
    @Test
    fun puzzleRandomV1SequenceIsStable() {
        val random = PuzzleRandomV1(PuzzleSeed(0))

        assertEquals(
            listOf(
                0xe220a8397b1dcdafUL.toLong(),
                0x6e789e6aa1b965f4UL.toLong(),
                0x06c45d188009454fUL.toLong(),
                0xf88bb8a8724c81ecUL.toLong(),
                0x1b39896a51a8749bUL.toLong(),
            ),
            List(5) { random.nextLong() },
        )
    }

    @Test
    fun game2048V2OpeningIsStable() {
        val state =
            Game2048Engine(
                Game2048PuzzleId(
                    seed = PuzzleSeed(-8_204_800_036L),
                    difficulty = Difficulty.EXPERT,
                    generatorVersion = Game2048GeneratorVersion.V2,
                ),
            ).start()

        assertEquals(
            listOf(2, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            state.board,
        )
        assertEquals(0L, state.score)
        assertEquals(2L, state.nextSpawnIndex)
    }

    @Test
    fun byteArrayCatalogInputUsesTheUnchangedBinaryFormat() {
        val levelId = CatalogLevelId(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelNumber(2))
        val bytes =
            CatalogLevelPackFormat.header(
                packVersion = CatalogLevelPackVersion.V1,
                puzzleType = PuzzleType.BALANCE,
                difficulty = Difficulty.EASY,
                recordCount = 2,
                generatorVersion = GeneratorVersion(1),
            ) +
                CatalogLevelPackFormat.record(PuzzleSeed(11L)) +
                CatalogLevelPackFormat.record(PuzzleSeed(22L))
        val pack =
            BinaryCatalogLevelPack(
                source = { _, _, _ -> ByteArrayCatalogLevelPackInput(bytes) },
                expectedRecordCount = 2,
            )

        assertEquals(
            CatalogLevelPackResult.Success(
                CatalogLevelDefinition(levelId, PuzzleSeed(22L), GeneratorVersion(1)),
            ),
            pack.resolve(levelId),
        )
    }
}
