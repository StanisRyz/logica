package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCatalogProgressRepositoryTest {
    @Test
    fun mergeUsesMaximumPerExactBucketAndCodecIsDeterministic() {
        val balanceV1 = bucket(PuzzleType.BALANCE, Difficulty.EASY, 1)
        val balanceV2 = bucket(PuzzleType.BALANCE, Difficulty.EASY, 2)
        val crownsV1 = bucket(PuzzleType.CROWNS, Difficulty.HARD, 1)
        val local = snapshot(balanceV1 to 12, balanceV2 to 3)
        val cloud = snapshot(balanceV1 to 7, balanceV2 to 20, crownsV1 to 5)
        val store = FakeProgressStore(local)
        val repository = WebCatalogProgressRepository(store)
        repository.loadLocal()

        val merged = assertIs<WebCatalogMergeResult.Merged>(repository.mergeCloud(cloud))

        assertEquals(12, merged.snapshot.currentLevel(balanceV1).value)
        assertEquals(20, merged.snapshot.currentLevel(balanceV2).value)
        assertEquals(5, merged.snapshot.currentLevel(crownsV1).value)
        assertTrue(merged.cloudWriteRequired)
        assertEquals(merged.snapshot, store.snapshot)
        val encoded = WebCatalogProgressCodec.encode(merged.snapshot)
        assertContentEquals(encoded, WebCatalogProgressCodec.encode(merged.snapshot))
        assertEquals(merged.snapshot, WebCatalogProgressCodec.decode(encoded))
        encoded[4] = 2
        assertNull(WebCatalogProgressCodec.decode(encoded))
    }

    @Test
    fun advancementIsMonotonicIdempotentAndRejectsFutureClaims() {
        val store = FakeProgressStore(WebCatalogProgressSnapshot.EMPTY)
        val repository = WebCatalogProgressRepository(store)
        repository.loadLocal()
        val levelOne = level(PuzzleType.WORD, Difficulty.MEDIUM, pack = 1, level = 1)

        val advanced = assertIs<WebCatalogAdvanceResult.Advanced>(repository.advanceSolved(levelOne))
        assertEquals(2, advanced.currentLevel.value)
        assertIs<WebCatalogAdvanceResult.Idempotent>(repository.advanceSolved(levelOne))
        assertIs<WebCatalogAdvanceResult.Rejected>(
            repository.advanceSolved(level(PuzzleType.WORD, Difficulty.MEDIUM, pack = 1, level = 3)),
        )
        assertEquals(2, repository.currentLevel(bucket(PuzzleType.WORD, Difficulty.MEDIUM, 1)).value)
        assertEquals(1, store.saveCount)
    }

    private fun snapshot(vararg entries: Pair<WebCatalogProgressBucket, Int>) =
        WebCatalogProgressSnapshot(
            levels = entries.associate { (bucket, level) -> bucket to CatalogLevelNumber(level) },
        )

    private fun bucket(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        pack: Int,
    ) = WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPackVersion(pack))

    private fun level(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        pack: Int,
        level: Int,
    ) =
        CatalogLevelId(
            puzzleType = puzzleType,
            difficulty = difficulty,
            packVersion = CatalogLevelPackVersion(pack),
            levelNumber = CatalogLevelNumber(level),
        )

    private class FakeProgressStore(
        var snapshot: WebCatalogProgressSnapshot,
    ) : WebCatalogProgressStore {
        var saveCount = 0

        override fun load(): WebCatalogProgressSnapshot = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
            saveCount++
        }
    }
}
