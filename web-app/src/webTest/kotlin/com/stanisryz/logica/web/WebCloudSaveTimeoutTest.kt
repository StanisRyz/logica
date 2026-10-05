package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Stage 2.2a: a cloud getData/setData that never answers fails after a while instead of hanging. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebCloudSaveTimeoutTest {
    private val token = WebPlayerContextToken(1L)

    @Test
    fun aHungReadFailsAfterTenSecondsAndItsLateAnswerIsIgnored() =
        runTest {
            val late = CompletableDeferred<String?>()
            val bridge = ScriptedPlayerData(read = { late.await() })
            val gateway = YandexCloudSaveGateway(bridge, YandexCloudSaveGateway.DAILY_STATE_KEY)

            val result = async { gateway.read() }
            advanceTimeBy(9_999)
            runCurrent()
            assertFalse(result.isCompleted)
            advanceTimeBy(2)
            runCurrent()
            assertIs<CloudSaveReadResult.Failed>(result.await())

            late.complete(WebBase64.encode(byteArrayOf(1)))
            runCurrent()
            assertIs<CloudSaveReadResult.Failed>(result.await())
        }

    @Test
    fun aHungWriteFailsAfterFifteenSeconds() =
        runTest {
            val bridge = ScriptedPlayerData(write = { awaitCancellation() })
            val gateway = YandexCloudSaveGateway(bridge, YandexCloudSaveGateway.STATISTICS_STATE_KEY)

            val result = async { gateway.write(byteArrayOf(1)) }
            advanceTimeBy(14_999)
            runCurrent()
            assertFalse(result.isCompleted)
            advanceTimeBy(2)
            runCurrent()
            assertIs<CloudSaveWriteResult.Failed>(result.await())
        }

    @Test
    fun aHungUnifiedReadLeavesTheContextUnresolvedAndTheRetryRestores() =
        runTest {
            val bridge = ScriptedPlayerData(read = { awaitCancellation() })
            val scheduler = scheduler(bridge)

            val outcome = async { scheduler.restoreAndEstablish(token) }
            advanceTimeBy(10_001)
            runCurrent()
            assertEquals(WebSaveRestoreOutcome.UNRESOLVED, outcome.await())
            assertEquals(0, bridge.writes)
            assertFalse(scheduler.unifiedSaveActive)

            // The ~2s restore retry reads an empty cloud and establishes the canonical save.
            bridge.read = { null }
            advanceUntilIdle()
            assertEquals(2, bridge.reads)
            assertEquals(1, bridge.writes)
            assertTrue(scheduler.unifiedSaveActive)
        }

    @Test
    fun aHungUnifiedWriteFailsReleasesTheWriteLockAndTheNextWritePasses() =
        runTest {
            val bridge = ScriptedPlayerData(read = { null }, write = { awaitCancellation() })
            val scheduler = scheduler(bridge)

            val outcome = async { scheduler.restoreAndEstablish(token) }
            advanceTimeBy(15_001)
            runCurrent()
            assertTrue(outcome.isCompleted)
            assertEquals(1, bridge.writes)
            assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)
            assertFalse(scheduler.unifiedSaveActive)

            // The establishing write gave up the write lock: an immediate flush gets it and lands.
            bridge.write = {}
            assertTrue(scheduler.flushNow())
            assertEquals(2, bridge.writes)
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)
            assertTrue(scheduler.unifiedSaveActive)
        }

    private fun TestScope.scheduler(bridge: ScriptedPlayerData): WebUnifiedSaveScheduler =
        WebUnifiedSaveScheduler(
            saveManager =
                WebSaveManager(
                    listOf(ProbeSection()),
                    YandexCloudSaveRepository(YandexCloudSaveGateway(bridge, "logica_unified_save_v1")),
                ),
            scope = this,
            isTokenCurrent = { it == token },
            debounceMs = 100L,
        )

    private class ScriptedPlayerData(
        var read: suspend () -> String? = { null },
        var write: suspend () -> Unit = {},
    ) : WebPlayerDataBridge {
        var reads = 0
        var writes = 0

        override suspend fun readPlayerData(key: String): String? {
            reads += 1
            return read()
        }

        override suspend fun writePlayerData(
            key: String,
            value: String,
            flush: Boolean,
        ) {
            writes += 1
            write()
        }
    }

    private class ProbeSection : WebSaveSection {
        override val id = "probe"

        override fun export(): ByteArray = byteArrayOf(1)

        override fun apply(payload: ByteArray): Boolean = true
    }
}
