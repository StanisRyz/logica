package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveLoadResult
import com.stanisryz.logica.platform.SaveRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The unified cloud is written only after a definite restore (Found or Missing) for the current
 * Player context: a failed or undecodable read never overwrites the stored save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebUnifiedSaveIntegrityTest {
    private var contextTokenValue = 1L

    @Test
    fun failedReadNeverWritesUntilARetriedRestoreSucceeds() =
        runTest {
            val repository = ScriptedSaveRepository(SaveLoadResult.Failed(IllegalStateException("network")))
            val section = ProbeSection("probe")
            val scheduler = scheduler(repository, listOf(section))

            assertEquals(WebSaveRestoreOutcome.UNRESOLVED, scheduler.restoreAndEstablish(token()))
            assertEquals(0, repository.writes)
            assertFalse(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)

            // Neither a durable change nor an immediate flush may write while unresolved.
            scheduler.markDirty()
            assertFalse(scheduler.flushNow())
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(0, repository.writes)
            assertEquals(2, repository.loads) // the ~2s bounded restore retry still fails

            // The next retried read succeeds: ordinary merge, then the canonical establish.
            repository.next = SaveLoadResult.Found(SaveData(sections = mapOf("probe" to byteArrayOf(7))))
            advanceUntilIdle()
            assertEquals(listOf<Byte>(7), section.applied.single().toList())
            assertEquals(1, repository.writes)
            assertTrue(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)
            assertTrue(scheduler.flushNow())
            assertEquals(2, repository.writes)
        }

    @Test
    fun boundedRestoreRetriesStopAndALaterDurableChangeRetriesRestore() =
        runTest {
            val repository = ScriptedSaveRepository(SaveLoadResult.Failed(IllegalStateException("network")))
            val scheduler = scheduler(repository, listOf(ProbeSection("probe")))

            scheduler.restoreAndEstablish(token())
            advanceUntilIdle()
            // Initial read + ~2s, ~8s, ~30s retries, then a stop; nothing was ever written.
            assertEquals(4, repository.loads)
            assertEquals(0, repository.writes)

            // A durable change asks for one more restore (bursts deduplicated), never a write.
            repeat(3) { scheduler.markDirty() }
            advanceUntilIdle()
            assertEquals(5, repository.loads)
            assertEquals(0, repository.writes)

            repository.next = SaveLoadResult.Missing
            scheduler.markDirty()
            advanceUntilIdle()
            assertEquals(6, repository.loads)
            assertEquals(1, repository.writes)
            assertTrue(scheduler.unifiedSaveActive)
        }

    @Test
    fun undecodableEnvelopeNeverOverwritesTheCloud() =
        runTest {
            val corrupt = byteArrayOf(1, 2, 3)
            val newerVersion =
                WebSaveCodec.encode(SaveData(version = SaveData.CURRENT_VERSION + 1, sections = mapOf("probe" to byteArrayOf(1))))
            for (stored in listOf(corrupt, newerVersion)) {
                val gateway = FakeCloudGateway(CloudSaveReadResult.Found(stored))
                val cloud = YandexCloudSaveRepository(gateway)
                assertIs<SaveLoadResult.Undecodable>(cloud.load())
                val scheduler = scheduler(cloud, listOf(ProbeSection("probe")))

                assertEquals(WebSaveRestoreOutcome.UNRESOLVED, scheduler.restoreAndEstablish(token()))
                scheduler.markDirty()
                assertFalse(scheduler.flushNow())
                advanceUntilIdle()
                assertEquals(0, gateway.writes)
                assertFalse(scheduler.unifiedSaveActive)
                scheduler.invalidateContext()
            }
        }

    @Test
    fun oneUnreadableOrUnknownSectionKeepsTheCloudButMergesTheRest() =
        runTest {
            val readable = ProbeSection("probe")
            val catalog = CatalogCodecSection()
            val unreadable =
                SaveData(sections = mapOf("probe" to byteArrayOf(5), WebSaveSectionIds.CATALOG to byteArrayOf(9, 9, 9)))
            val unknown = SaveData(sections = mapOf("probe" to byteArrayOf(6), "fromNewerBuild" to byteArrayOf(1)))
            for (stored in listOf(unreadable, unknown)) {
                val repository = ScriptedSaveRepository(SaveLoadResult.Found(stored))
                val scheduler = scheduler(repository, listOf(readable, catalog))

                assertEquals(WebSaveRestoreOutcome.UNRESOLVED, scheduler.restoreAndEstablish(token()))
                scheduler.markDirty()
                assertFalse(scheduler.flushNow())
                advanceUntilIdle()
                assertEquals(0, repository.writes)
                assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)
                scheduler.invalidateContext()
            }
            // The decodable section still merged both times (again on every restore retry).
            assertEquals(listOf<Byte>(5, 6), readable.applied.map { it.single() }.distinct())
            assertTrue(catalog.applied.isEmpty())
        }

    @Test
    fun missingSaveIsEmptyAndEstablishesAsBefore() =
        runTest {
            val gateway = FakeCloudGateway(CloudSaveReadResult.Missing)
            val scheduler = scheduler(YandexCloudSaveRepository(gateway), listOf(ProbeSection("probe")))

            assertEquals(WebSaveRestoreOutcome.EMPTY, scheduler.restoreAndEstablish(token()))
            assertEquals(1, gateway.writes)
            assertTrue(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)
        }

    @Test
    fun unsupportedCloudIsNeitherReadAgainNorWritten() =
        runTest {
            val gateway = FakeCloudGateway(CloudSaveReadResult.Unsupported)
            val scheduler = scheduler(YandexCloudSaveRepository(gateway), listOf(ProbeSection("probe")))

            assertEquals(WebSaveRestoreOutcome.UNAVAILABLE, scheduler.restoreAndEstablish(token()))
            scheduler.markDirty()
            assertFalse(scheduler.flushNow())
            advanceUntilIdle()
            assertEquals(0, gateway.writes)
            assertEquals(1, gateway.reads)
            assertFalse(scheduler.unifiedSaveActive)
        }

    @Test
    fun aPlayerSwitchDuringRestoreRetriesCanNeverWriteTheOldContext() =
        runTest {
            val repository = ScriptedSaveRepository(SaveLoadResult.Failed(IllegalStateException("network")))
            val scheduler = scheduler(repository, listOf(ProbeSection("probe")))
            scheduler.restoreAndEstablish(token())

            // The Player context moves on before the retry fires, even though a read would now succeed.
            contextTokenValue = 2L
            repository.next = SaveLoadResult.Missing
            advanceUntilIdle()
            assertEquals(0, repository.writes)
            assertFalse(scheduler.unifiedSaveActive)

            // An explicit invalidation cancels pending retries outright.
            scheduler.invalidateContext()
            repository.next = SaveLoadResult.Failed(IllegalStateException("network"))
            scheduler.restoreAndEstablish(token())
            val loadsBeforeSwitch = repository.loads
            scheduler.invalidateContext()
            repository.next = SaveLoadResult.Missing
            advanceUntilIdle()
            assertEquals(loadsBeforeSwitch, repository.loads)
            assertEquals(0, repository.writes)

            // The new context restores and establishes on its own.
            assertEquals(WebSaveRestoreOutcome.EMPTY, scheduler.restoreAndEstablish(token()))
            assertEquals(1, repository.writes)
        }

    @Test
    fun localRepositoryReportsUndecodableAndFailedReadsExplicitly() =
        runTest {
            val corrupt = LocalSaveRepository("k", { "%%%" }, { _, _ -> })
            assertIs<SaveLoadResult.Undecodable>(corrupt.load())
            val garbage = LocalSaveRepository("k", { WebBase64.encode(byteArrayOf(1, 2)) }, { _, _ -> })
            assertIs<SaveLoadResult.Undecodable>(garbage.load())
            val throwing = LocalSaveRepository("k", { error("storage blocked") }, { _, _ -> })
            assertIs<SaveLoadResult.Failed>(throwing.load())
            val empty = LocalSaveRepository("k", { null }, { _, _ -> })
            assertEquals(SaveLoadResult.Missing, empty.load())
            assertNull(WebSaveCodec.decode(byteArrayOf(1, 2)))
        }

    private fun token(): WebPlayerContextToken = WebPlayerContextToken(contextTokenValue)

    private fun TestScope.scheduler(
        repository: SaveRepository,
        sections: List<WebSaveSection>,
    ): WebUnifiedSaveScheduler =
        WebUnifiedSaveScheduler(
            saveManager = WebSaveManager(sections, repository),
            scope = this,
            isTokenCurrent = { it.value == contextTokenValue },
            debounceMs = 100L,
        )

    private class ProbeSection(
        override val id: String,
    ) : WebSaveSection {
        val applied = mutableListOf<ByteArray>()

        override fun export(): ByteArray = byteArrayOf(1)

        override fun apply(payload: ByteArray): Boolean {
            applied += payload
            return true
        }
    }

    /** Reports decoding exactly like the production Catalog section adapter. */
    private class CatalogCodecSection : WebSaveSection {
        override val id = WebSaveSectionIds.CATALOG
        val applied = mutableListOf<WebCatalogProgressSnapshot>()

        override fun export(): ByteArray = WebCatalogProgressCodec.encode(WebCatalogProgressSnapshot.EMPTY)

        override fun apply(payload: ByteArray): Boolean {
            applied += WebCatalogProgressCodec.decode(payload) ?: return false
            return true
        }
    }

    private class ScriptedSaveRepository(
        var next: SaveLoadResult,
    ) : SaveRepository {
        var loads = 0
        var writes = 0

        override suspend fun load(): SaveLoadResult {
            loads += 1
            return next
        }

        override suspend fun save(data: SaveData): Boolean {
            writes += 1
            return true
        }
    }

    private class FakeCloudGateway(
        private val readResult: CloudSaveReadResult,
    ) : CloudSaveGateway {
        override val availability = CloudSaveAvailability.AVAILABLE
        var reads = 0
        var writes = 0

        override suspend fun read(): CloudSaveReadResult {
            reads += 1
            return readResult
        }

        override suspend fun write(payload: ByteArray): CloudSaveWriteResult {
            writes += 1
            return CloudSaveWriteResult.Saved
        }
    }
}
