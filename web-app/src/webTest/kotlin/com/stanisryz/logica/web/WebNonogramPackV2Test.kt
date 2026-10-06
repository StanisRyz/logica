package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPictureLibraryV3
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Web side of the Nonogram's move to Level Pack V2: the V2 bucket continues the V1 numbering,
 * solving advances only V2, the game level is the higher bucket (an older device without a V2 bucket
 * included), levels below the V1 bucket stay V1 levels, and the controller plays a V2 level from its
 * own bucket and generator, fetching the picture library only for a picture level.
 */
class WebNonogramPackV2Test {
    private val v1 = WebCatalogProgressBucket(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelPackVersion.V1)
    private val v2 = WebCatalogProgressBucket(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelPackVersion.V2)

    @Test
    fun theV2BucketContinuesFromV1AndOnlyItAdvances() {
        val repository = repository(snapshot(v1 to 37))

        assertEquals(37, repository.currentLevel(v2).value)
        val advanced = assertIs<WebCatalogAdvanceResult.Advanced>(repository.advanceSolved(level(37, CatalogLevelPackVersion.V2)))

        assertEquals(38, advanced.currentLevel.value)
        assertEquals(
            37,
            repository.snapshot.value
                .currentLevel(v1)
                .value,
        )
        assertEquals(
            38,
            repository.snapshot.value
                .gameLevel(PuzzleType.NONOGRAM, Difficulty.EASY)
                .value,
        )
        // A level beyond the V2 bucket is still refused.
        assertIs<WebCatalogAdvanceResult.Rejected>(repository.advanceSolved(level(40, CatalogLevelPackVersion.V2)))
        // Other games keep their single V1 bucket.
        assertEquals(CatalogLevelPackVersion.V1, CatalogLevelPacks.activePackVersion(PuzzleType.SUDOKU))
    }

    @Test
    fun anOlderDeviceWithoutAV2BucketMergesWithoutLosingOrDoublingAnything() {
        val current = repository(snapshot(v1 to 37, v2 to 41))
        // An older game version never writes V2 and kept advancing V1 on another device.
        val older = snapshot(v1 to 45)

        val merged = assertIs<WebCatalogMergeResult.Merged>(current.mergeCloud(older)).snapshot

        assertEquals(45, merged.currentLevel(v1).value)
        assertEquals(41, merged.currentLevel(v2).value)
        assertEquals(45, merged.gameLevel(PuzzleType.NONOGRAM, Difficulty.EASY).value)
        assertEquals(45, current.currentLevel(v2).value)
        // The rating counts the cleared levels once: max(V1, V2) - 1.
        assertEquals(44, merged.clearedLevels(PuzzleType.NONOGRAM).getValue(Difficulty.EASY))

        // The older device reads the newer snapshot and keeps its V2 bucket when it writes back.
        val olderRepository = repository(older)
        val back = assertIs<WebCatalogMergeResult.Merged>(olderRepository.mergeCloud(snapshot(v1 to 37, v2 to 41))).snapshot
        assertEquals(41, back.currentLevel(v2).value)
        assertEquals(45, back.currentLevel(v1).value)
    }

    @Test
    fun levelsBelowTheV1BucketStayV1LevelsWithTheirOwnStars() {
        val repository = repository(snapshot(v1 to 37, v2 to 40))

        val packs =
            listOf(1, 10, 36, 37, 38, 39).map {
                repository.snapshot.value
                    .bucketForLevel(PuzzleType.NONOGRAM, Difficulty.EASY, it)
                    .packVersion.value
            }
        assertEquals(listOf(1, 1, 1, 2, 2, 2), packs)

        // A replay of V1 level 10 and of V2 level 38 raises each level's own stars, never lowers them.
        assertTrue(repository.recordStars(level(10, CatalogLevelPackVersion.V1), 3))
        assertTrue(repository.recordStars(level(38, CatalogLevelPackVersion.V2), 2))
        repository.recordStars(level(38, CatalogLevelPackVersion.V2), 1)
        val stars = repository.stars.value
        assertEquals(3, stars.starsOf(v1, 10))
        assertEquals(2, stars.starsOf(v2, 38))
        assertEquals(0, stars.starsOf(v2, 10))
        assertEquals(5, stars.levelRecords().sumOf { it.stars })
        assertEquals(3, repository.previousBestStars(level(10, CatalogLevelPackVersion.V1)))
        assertEquals(2, repository.previousBestStars(level(38, CatalogLevelPackVersion.V2)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theControllerPlaysAV2LevelFromItsOwnBucketAndGenerator() =
        runTest {
            val progression = FakeWebCatalogProgressAccess(initialLevel = 37)
            val loads = mutableListOf<Pair<Difficulty, CatalogLevelPackVersion>>()
            val pictureLoads = mutableListOf<Difficulty>()
            val controller =
                WebNonogramController(
                    loadPack = { difficulty, packVersion -> loads += difficulty to packVersion },
                    progression = progression,
                    loadPictures = { difficulty ->
                        pictureLoads += difficulty
                        WebPuzzleData.installWordLexiconResource(NonogramPictureLibraryV3.resourcePath(difficulty), PICTURE_LIBRARY)
                    },
                    levelPack = AlternatingPack,
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.EASY)
            advanceUntilIdle()
            val picture = assertIs<WebNonogramState.Playing>(controller.state)

            assertEquals(listOf(Difficulty.EASY to CatalogLevelPackVersion.V2), loads)
            assertEquals(listOf(Difficulty.EASY), pictureLoads)
            assertEquals(3, picture.puzzle.id.generatorVersion.value)
            assertEquals(10, picture.puzzle.size)
            assertEquals(CatalogLevelPackVersion.V2, (picture.source as WebGameplaySource.CatalogLevel).attempt.levelId.packVersion)

            // The gallery rebuilds a V1 level from Level Pack V1 with Generator V1.
            val v1Picture = controller.galleryPicture(Difficulty.EASY, 10, CatalogLevelPackVersion.V1)
            assertEquals(1, v1Picture?.id?.generatorVersion?.value)
            assertEquals(Difficulty.EASY to CatalogLevelPackVersion.V1, loads.last())
            // An even V2 level is a symmetric V4 board and needs no picture library.
            val symmetric = controller.galleryPicture(Difficulty.EASY, 38, CatalogLevelPackVersion.V2)
            assertEquals(4, symmetric?.id?.generatorVersion?.value)
            assertEquals(1, pictureLoads.size)
        }

    /** Level Pack V1 levels are Generator V1; V2 alternates V3 pictures and V4 boards by slot. */
    private object AlternatingPack : CatalogLevelPack {
        override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
            val version = CatalogLevelPacks.generatorVersionFor(levelId, CatalogLevelPacks.NONOGRAM_V2_PICTURES)
            val seed =
                if (levelId.packVersion == CatalogLevelPackVersion.V1) {
                    1L
                } else if (version.value == 3) {
                    0L
                } else {
                    1L
                }
            val generator = if (levelId.packVersion == CatalogLevelPackVersion.V1) GeneratorVersion(1) else version
            return CatalogLevelPackResult.Success(CatalogLevelDefinition(levelId, PuzzleSeed(seed), generator))
        }
    }

    private fun repository(snapshot: WebCatalogProgressSnapshot) =
        WebCatalogProgressRepository(WebCatalogProgressScope.STANDALONE, MemoryStore(snapshot)).also { it.loadLocal() }

    private fun snapshot(vararg entries: Pair<WebCatalogProgressBucket, Int>) =
        WebCatalogProgressSnapshot(levels = entries.associate { (bucket, level) -> bucket to CatalogLevelNumber(level) })

    private fun level(
        number: Int,
        packVersion: CatalogLevelPackVersion,
    ) = CatalogLevelId(PuzzleType.NONOGRAM, Difficulty.EASY, CatalogLevelNumber(number), packVersion)

    private class MemoryStore(
        var snapshot: WebCatalogProgressSnapshot,
    ) : WebCatalogProgressStore {
        override fun load(): WebCatalogProgressSnapshot = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
        }
    }

    private companion object {
        /** One 10x10 picture (a heart) standing in for the fetched Easy library. */
        const val PICTURE_LIBRARY =
            "# test library\n" +
                "heart\t.##....##./####..####/##########/##########/##########/.########./..######../...####.../....##..../..........\t3\n"
    }
}
