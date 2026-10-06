package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveWriteResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WebCloudWritePacerTest {
    private class Call(
        val key: String,
        val payload: Int,
        val at: Long,
    )

    private fun TestScope.pacer() = WebCloudWritePacer(now = { currentTime }, sleep = { delay(it) })

    @Test
    fun aSeriesOfChangesOverTenSecondsMakesAtMostFourCallsEndingWithTheLastState() =
        runTest {
            val pacer = pacer()
            val calls = mutableListOf<Call>()
            val results = mutableListOf<CloudSaveWriteResult>()
            repeat(20) { change ->
                launch {
                    delay(change * 500L)
                    results +=
                        pacer.write("logica_unified_save_v1") {
                            calls += Call("logica_unified_save_v1", change, currentTime)
                            CloudSaveWriteResult.Saved
                        }
                }
            }
            advanceUntilIdle()
            assertTrue(calls.size <= 4, "calls: ${calls.map { it.at }}")
            assertEquals(19, calls.last().payload)
            calls.zipWithNext().forEach { (a, b) -> assertTrue(b.at - a.at >= WebCloudWritePacer.MIN_SPACING_MS) }
            // Every change was answered: merged writes share the result of the call that carried them.
            assertEquals(20, results.size)
            assertTrue(results.all { it == CloudSaveWriteResult.Saved })
        }

    @Test
    fun differentKeysShareTheSpacing() =
        runTest {
            val pacer = pacer()
            val calls = mutableListOf<Call>()
            val a =
                async {
                    pacer.write("logica_state_v1") {
                        calls += Call("logica_state_v1", 0, currentTime)
                        CloudSaveWriteResult.Saved
                    }
                }
            val b =
                async {
                    pacer.write("logica_daily_v1") {
                        calls += Call("logica_daily_v1", 0, currentTime)
                        CloudSaveWriteResult.Saved
                    }
                }
            a.await()
            b.await()
            assertEquals(listOf("logica_state_v1", "logica_daily_v1"), calls.map { it.key })
            assertTrue(calls[1].at - calls[0].at >= WebCloudWritePacer.MIN_SPACING_MS)
        }

    @Test
    fun aWriteQueuedBeforeAPlayerChangeNeverReachesTheCloud() =
        runTest {
            val pacer = pacer()
            var epoch = 1L
            pacer.contextEpoch = { epoch }
            val calls = mutableListOf<Call>()
            pacer.write("logica_unified_save_v1") {
                calls += Call("first", 0, currentTime)
                CloudSaveWriteResult.Saved
            }
            // The second write waits for its turn; the Player switches meanwhile.
            val queued =
                async {
                    pacer.write("logica_unified_save_v1") {
                        calls += Call("stale", 1, currentTime)
                        CloudSaveWriteResult.Saved
                    }
                }
            delay(1_000L)
            epoch = 2L
            assertIs<CloudSaveWriteResult.Failed>(queued.await())
            assertEquals(listOf("first"), calls.map { it.key })
        }
}
