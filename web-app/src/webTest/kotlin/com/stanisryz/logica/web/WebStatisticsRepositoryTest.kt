package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebStatisticsRepositoryTest {
    @Test
    fun multiDeviceMergeKeepsComponentsAndAggregatesBySum() {
        val wordEasy = WebStatisticsBucket(PuzzleType.WORD, Difficulty.EASY)
        val deviceA = "device-a-00000001"
        val installationStore = FakeInstallationIdStore()
        val deviceB = WebInstallationIdProvider(installationStore) { "device-b-00000002" }.getOrCreate()
        assertEquals(
            deviceB,
            WebInstallationIdProvider(installationStore) { error("The persisted ID must be reused.") }.getOrCreate(),
        )
        val local =
            snapshot(
                deviceB,
                wordEasy,
                WebStatisticsCounters(played = 7L, solved = 5L, failed = 2L, hints = 3L),
            )
        val cloud =
            snapshot(
                deviceA,
                wordEasy,
                WebStatisticsCounters(
                    played = 10L,
                    solved = 8L,
                    failed = 2L,
                    hints = 4L,
                    wordSolvedAttempts = mapOf(1 to 3L, 2 to 5L),
                ),
            )
        val store = FakeStatisticsStore(local)
        val repository = WebStatisticsRepository(WebCatalogProgressScope.STANDALONE, deviceB, store)
        repository.loadLocal()

        val merge = assertIs<WebStatisticsMergeResult.Merged>(repository.mergeCloud(cloud))

        assertEquals(setOf(deviceA, deviceB), merge.snapshot.components.keys)
        assertEquals(17L, repository.aggregate().totals(PuzzleType.WORD, Difficulty.EASY).played)
        assertEquals(7L, repository.aggregate().totals(difficulty = Difficulty.EASY).hints)
        assertEquals(mapOf(1 to 3L, 2 to 5L), repository.aggregate().totals(PuzzleType.WORD).wordSolvedAttempts)
        assertTrue(merge.cloudWriteRequired)
        val encoded = WebStatisticsCodec.encode(merge.snapshot)
        assertContentEquals(encoded, WebStatisticsCodec.encode(merge.snapshot))
        assertEquals(merge.snapshot, WebStatisticsCodec.decode(encoded))
    }

    @Test
    fun sameDeviceMergeUsesMaximumAndRepeatedMergeIsIdempotent() {
        val balanceHard = WebStatisticsBucket(PuzzleType.BALANCE, Difficulty.HARD)
        val device = "device-a-00000001"
        val local = snapshot(device, balanceHard, WebStatisticsCounters(played = 12L, solved = 9L))
        val cloud = snapshot(device, balanceHard, WebStatisticsCounters(played = 9L, solved = 7L))
        val store = FakeStatisticsStore(local)
        val repository = WebStatisticsRepository(WebCatalogProgressScope.STANDALONE, device, store)
        repository.loadLocal()

        val first = assertIs<WebStatisticsMergeResult.Merged>(repository.mergeCloud(cloud))
        val second = assertIs<WebStatisticsMergeResult.Merged>(repository.mergeCloud(first.snapshot))

        assertEquals(
            12L,
            first.snapshot.components
                .getValue(device)
                .buckets
                .getValue(balanceHard)
                .played,
        )
        assertEquals(first.snapshot, second.snapshot)
        assertFalse(second.cloudWriteRequired)
        assertEquals(12L, repository.aggregate().totals().played)
        val overflowed = WebStatisticsCodec.encode(first.snapshot)
        val firstCounterOffset = 12 + 2 + device.length + 4 + 2
        overflowed[firstCounterOffset] = 0x80.toByte()
        assertNull(WebStatisticsCodec.decode(overflowed))
    }

    private fun snapshot(
        deviceId: String,
        bucket: WebStatisticsBucket,
        counters: WebStatisticsCounters,
    ): WebStatisticsSnapshot =
        WebStatisticsSnapshot(
            components =
                mapOf(
                    deviceId to WebStatisticsDeviceComponent(mapOf(bucket to counters)),
                ),
        )

    private class FakeStatisticsStore(
        var snapshot: WebStatisticsSnapshot,
    ) : WebStatisticsStore {
        override fun load(): WebStatisticsSnapshot = snapshot

        override fun save(snapshot: WebStatisticsSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeInstallationIdStore : WebInstallationIdStore {
        private var installationId: String? = null

        override fun load(): String? = installationId

        override fun save(installationId: String) {
            this.installationId = installationId
        }
    }
}
