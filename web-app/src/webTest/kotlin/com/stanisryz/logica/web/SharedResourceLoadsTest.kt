package com.stanisryz.logica.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Concurrent requests for one resource share one fetch; a failed one is fetched again. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedResourceLoadsTest {
    @Test
    fun twoParallelRequestsForOnePathFetchOnce() =
        runTest {
            val loads = SharedResourceLoads(backgroundScope)
            val gate = CompletableDeferred<Unit>()
            var fetches = 0
            val fetch: suspend () -> Unit = {
                fetches++
                gate.await()
            }
            val first = async { loads.load("sudoku/v1/easy.sdk", fetch) }
            val second = async { loads.load("sudoku/v1/easy.sdk", fetch) }
            advanceUntilIdle()
            gate.complete(Unit)
            first.await()
            second.await()
            loads.load("sudoku/v1/easy.sdk", fetch)

            assertEquals(1, fetches)
        }

    @Test
    fun aFailedLoadIsFetchedAgainAndACancelledCallerDoesNotCancelIt() =
        runTest {
            // Like the loader's own scope: a supervisor, so one failed load cancels nothing else.
            val loads =
                SharedResourceLoads(
                    CoroutineScope(
                        backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job),
                    ),
                )
            var fetches = 0
            val failing =
                runCatching {
                    loads.load("levels/v1/x.lvp") {
                        fetches++
                        error("HTTP 404")
                    }
                }
            assertTrue(failing.isFailure)

            val gate = CompletableDeferred<Unit>()
            val abandoned =
                launch {
                    loads.load("levels/v1/x.lvp") {
                        fetches++
                        gate.await()
                    }
                }
            advanceUntilIdle()
            abandoned.cancel()
            val waiting = async { loads.load("levels/v1/x.lvp") { fetches++ } }
            advanceUntilIdle()
            gate.complete(Unit)
            waiting.await()

            assertEquals(2, fetches)
        }
}
