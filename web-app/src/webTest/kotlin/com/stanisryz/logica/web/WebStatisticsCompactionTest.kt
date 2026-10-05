package com.stanisryz.logica.web

import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveLoadResult
import com.stanisryz.logica.platform.SaveRepository
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Stage 2.2: a statistics component only for installations that play, and a bounded section. */
class WebStatisticsCompactionTest {
    private class CountingStore(
        var snapshot: WebStatisticsSnapshot = WebStatisticsSnapshot.EMPTY,
    ) : WebStatisticsStore {
        var saves = 0

        override fun load(): WebStatisticsSnapshot = snapshot

        override fun save(snapshot: WebStatisticsSnapshot) {
            saves += 1
            this.snapshot = snapshot
        }
    }

    private fun id(n: Int): String = "device-" + n.toString().padStart(12, '0')

    private fun component(played: Long): WebStatisticsDeviceComponent =
        WebStatisticsDeviceComponent(
            mapOf(
                WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.EASY) to WebStatisticsCounters(played = played),
            ),
        )

    private fun played(snapshot: WebStatisticsSnapshot): Long = WebStatisticsAggregator.aggregate(snapshot).totals().played

    private val solvedBalance =
        WebStatisticsTerminalResult(PuzzleType.BALANCE, Difficulty.EASY, WebStatisticsTerminalOutcome.SOLVED, hintsUsed = 0)

    @Test
    fun bindingWithoutPlaysCreatesNoComponentAndTheFirstPlayDoes() {
        val store = CountingStore()
        val repository = WebStatisticsRepository(WebCatalogProgressScope.STANDALONE, id(1), store)
        repository.loadLocal()
        assertTrue(
            repository.snapshot.value.components
                .isEmpty(),
        )
        assertEquals(0, store.saves)

        assertIs<WebStatisticsRecordResult.Recorded>(repository.recordTerminalResult(solvedBalance))
        assertEquals(setOf(id(1)), repository.snapshot.value.components.keys)
        assertEquals(1L, played(store.snapshot))
    }

    @Test
    fun aCompactedCopyAndAnOldCopyNeverCountOnePlayTwice() {
        val others = (100 until 113).associate { id(it) to component(played = 1L) } // 13 other installations
        val deviceA = id(1)
        val deviceB = id(2)
        val oldCopy = WebStatisticsSnapshot(components = others + (deviceA to component(10L)))

        // Device A compacts: 14 components, the two smallest others are folded.
        val compacted = WebStatisticsCompaction.compact(oldCopy, protectedId = deviceA)
        assertEquals(WebStatisticsCompaction.MAX_ACTIVE_COMPONENTS, compacted.components.size)
        assertEquals(2, compacted.archive.foldedIds.size)
        assertTrue(deviceA in compacted.components)
        assertEquals(23L, played(compacted))

        // Device B still holds the old copy plus its own plays, and merges A's compacted copy.
        val store = CountingStore(WebStatisticsSnapshot(components = others + (deviceA to component(10L)) + (deviceB to component(5L))))
        val repository = WebStatisticsRepository(WebCatalogProgressScope.STANDALONE, deviceB, store)
        repository.loadLocal()
        val merged = assertIs<WebStatisticsMergeResult.Merged>(repository.mergeCloud(compacted)).snapshot
        assertEquals(28L, played(merged)) // 13 + 10 + 5, each play once
        assertTrue(merged.components.size <= WebStatisticsCompaction.MAX_ACTIVE_COMPONENTS)
        assertTrue(deviceB in merged.components)

        // And back on A, merging B's result changes nothing in the totals.
        assertEquals(28L, played(WebStatisticsMerger.merge(compacted, merged)))
        assertEquals(28L, played(WebStatisticsMerger.merge(merged, oldCopy)))
    }

    @Test
    fun anInstallationWhoseComponentWasFoldedElsewhereMovesToAFreshId() {
        val deviceB = id(2)
        val cloud =
            WebStatisticsSnapshot(
                archive = WebStatisticsArchive(totals = component(4L), foldedIds = setOf(deviceB)),
            )
        val store = CountingStore(WebStatisticsSnapshot(components = mapOf(deviceB to component(4L))))
        val repository = WebStatisticsRepository(WebCatalogProgressScope.STANDALONE, deviceB, store) { id(3) }
        repository.loadLocal()
        repository.mergeCloud(cloud)
        assertNotEquals(deviceB, repository.installationId)
        assertEquals(4L, played(repository.snapshot.value))

        repository.recordTerminalResult(solvedBalance)
        assertEquals(5L, played(repository.snapshot.value))
        assertEquals(setOf(id(3)), repository.snapshot.value.components.keys)
    }

    @Test
    fun theSectionAtItsMaximumStaysWithinTheChosenLimit() {
        val allBuckets =
            listOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.WORD,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
                PuzzleType.NONOGRAM,
                PuzzleType.BLOCK_SUDOKU,
            ).flatMap { type -> Difficulty.entries.map { WebStatisticsBucket(type, it) } }
                .associateWith { WebStatisticsCounters(played = Long.MAX_VALUE / 1_000, solved = 1, failed = 1, hints = 1) }
        val full = WebStatisticsDeviceComponent(allBuckets)

        fun longId(n: Int): String = "x".repeat(WebInstallationId.MAX_LENGTH - 6) + n.toString().padStart(6, '0')
        val foldedIds = mutableSetOf<String>()
        var budget = WebStatisticsCompaction.MAX_FOLDED_ID_BYTES
        var n = 1_000
        while (budget >= 2 + WebInstallationId.MAX_LENGTH) {
            foldedIds += longId(n++)
            budget -= 2 + WebInstallationId.MAX_LENGTH
        }
        val snapshot =
            WebStatisticsSnapshot(
                components = (1..WebStatisticsCompaction.MAX_ACTIVE_COMPONENTS).associate { longId(it) to full },
                archive =
                    WebStatisticsArchive(
                        WebStatisticsDeviceComponent(allBuckets.mapValues { WebStatisticsCounters(played = 1) }),
                        foldedIds,
                    ),
            )
        val size = WebStatisticsCodec.encodedSize(snapshot)
        assertTrue(size <= 40L * 1024, "size $size")
        assertEquals(size.toInt(), WebStatisticsCodec.encode(snapshot).size)
        assertEquals(snapshot, WebStatisticsCodec.decode(WebStatisticsCodec.encode(snapshot)))
    }

    @Test
    fun aSnapshotWithoutAnArchiveIsStillWrittenAsSchemaOne() {
        val plain = WebStatisticsSnapshot(components = mapOf(id(1) to component(3L)))
        val encoded = WebStatisticsCodec.encode(plain)
        assertEquals(1, encoded[7].toInt()) // schema version, big-endian Int at offset 4
        assertEquals(plain, WebStatisticsCodec.decode(encoded))
        val archived = plain.copy(archive = WebStatisticsArchive(component(2L), setOf(id(9))))
        val encodedArchive = WebStatisticsCodec.encode(archived)
        assertEquals(2, encodedArchive[7].toInt())
        assertEquals(archived, WebStatisticsCodec.decode(encodedArchive))
    }

    @Test
    fun anOversizedEnvelopeIsAFailedWriteNotAnException() =
        runTest {
            val repository =
                object : SaveRepository {
                    var writes = 0

                    override suspend fun load(): SaveLoadResult = SaveLoadResult.Missing

                    override suspend fun save(data: SaveData): Boolean {
                        writes += 1
                        return true
                    }
                }
            val oversized =
                object : WebSaveSection {
                    override val id = WebSaveSectionIds.STATISTICS

                    override fun export(): ByteArray = ByteArray(200_000)

                    override fun apply(payload: ByteArray) = true
                }
            assertFalse(WebSaveManager(listOf(oversized), repository).persist())
            val throwing =
                object : WebSaveSection {
                    override val id = WebSaveSectionIds.STATISTICS

                    override fun export(): ByteArray = error("over its own limit")

                    override fun apply(payload: ByteArray) = true
                }
            assertFalse(WebSaveManager(listOf(throwing), repository).persist())
            assertEquals(0, repository.writes)

            val scheduler = WebUnifiedSaveScheduler(WebSaveManager(listOf(oversized), repository), scope = this)
            scheduler.restoreAndEstablish(WebPlayerContextToken(1L))
            assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)
            scheduler.invalidateContext()
        }
}
