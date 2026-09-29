package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebCatalogStarsTest {
    private val easyBalance = WebCatalogProgressBucket(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelPackVersion.V1)
    private val hardWord = WebCatalogProgressBucket(PuzzleType.WORD, Difficulty.HARD, CatalogLevelPackVersion.V1)

    private class MemoryProgressStore : WebCatalogProgressStore {
        var snapshot = WebCatalogProgressSnapshot.EMPTY

        override fun load() = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
        }
    }

    @Test
    fun codecRoundTripsAndRejectsCorruptPayloads() {
        val snapshot =
            WebCatalogStarsSnapshot.EMPTY
                .withBest(easyBalance, 1, 3)
                .withBest(easyBalance, 6, 2)
                .withBest(hardWord, 2, 1)
        val encoded = WebCatalogStarsCodec.encode(snapshot)
        assertEquals(snapshot, WebCatalogStarsCodec.decode(encoded))
        assertNull(WebCatalogStarsCodec.decode(encoded.copyOf(encoded.size - 1)))
        assertEquals(listOf(3, 0, 0, 0, 0, 2), snapshot.levels.getValue(easyBalance))
    }

    @Test
    fun bestStarsOnlyGrowAndMergeByLevelMaximum() {
        val local = WebCatalogStarsSnapshot.EMPTY.withBest(easyBalance, 1, 2).withBest(easyBalance, 1, 1)
        assertEquals(2, local.starsOf(easyBalance, 1))
        val cloud = WebCatalogStarsSnapshot.EMPTY.withBest(easyBalance, 1, 1).withBest(easyBalance, 2, 3)
        val merged = local.mergedWith(cloud)
        assertEquals(listOf(2, 3), merged.levels.getValue(easyBalance))
        assertEquals(merged, merged.mergedWith(cloud))
    }

    @Test
    fun repositoryRecordsStarsDurablyAndReportsCloudGaps() {
        val starsStore = WebCatalogStarsStore.InMemory()
        val repository = WebCatalogProgressRepository(WebCatalogProgressScope.STANDALONE, MemoryProgressStore(), starsStore)
        repository.loadLocal()
        var durableChanges = 0
        repository.onDurableChange = { durableChanges++ }
        val level = CatalogLevelId(PuzzleType.BALANCE, Difficulty.EASY, CatalogLevelNumber(1), CatalogLevelPackVersion.V1)

        assertTrue(repository.recordStars(level, 2))
        assertFalse(repository.recordStars(level, 1)) // never lowers a level
        assertTrue(repository.recordStars(level, 3))
        assertEquals(2, durableChanges)
        assertEquals(3, starsStore.load().starsOf(easyBalance, 1))

        assertTrue(repository.mergeCloudStars(WebCatalogStarsSnapshot.EMPTY)) // the cloud lacks level 1
        assertFalse(repository.mergeCloudStars(repository.stars.value))
    }

    @Test
    fun best2048IsKeptLocallyPublishedOnceAndMergedByMaximum() {
        val bestStore = WebBestScoreStore.InMemory()
        val repository =
            WebCatalogProgressRepository(
                WebCatalogProgressScope.STANDALONE,
                MemoryProgressStore(),
                bestScoreStore = bestStore,
            )
        repository.loadLocal()
        var durableChanges = 0
        repository.onDurableChange = { durableChanges++ }

        repository.recordBest2048(1_200)
        repository.recordBest2048(3_400)
        repository.recordBest2048(2_000) // never lowers the best
        assertEquals(3_400, bestStore.load())
        assertEquals(0L, repository.best2048.value) // not published mid-game
        repository.publishBest2048()
        repository.publishBest2048() // nothing new to publish
        assertEquals(3_400, repository.best2048.value)
        assertEquals(1, durableChanges)

        assertTrue(repository.mergeCloudBest2048(1_000)) // the cloud lacks the local best
        assertFalse(repository.mergeCloudBest2048(9_000))
        assertEquals(9_000, repository.best2048.value)
        assertEquals(9_000, bestStore.load())

        val encoded = WebBestScoreCodec.encode(9_000)
        assertEquals(9_000, WebBestScoreCodec.decode(encoded))
        assertNull(WebBestScoreCodec.decode(encoded.copyOf(encoded.size - 1)))
    }
}
