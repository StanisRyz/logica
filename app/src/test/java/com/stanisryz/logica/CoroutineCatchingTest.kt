package com.stanisryz.logica

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineCatchingTest {
    @Test
    fun anOrdinaryFailureIsStillAResult() {
        val failure = IllegalStateException("database")
        assertEquals(failure, runCatchingCancellable { throw failure }.exceptionOrNull())
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
    }

    @Test
    fun cancellationIsRethrownSoTheCoroutineReallyStops() =
        runTest {
            var carriedOn = false
            var caught = false
            val job =
                launch {
                    val result = runCatchingCancellable { delay(1_000) }
                    caught = result.isFailure
                    carriedOn = true
                }
            runCurrent()
            job.cancel()
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(job.isCancelled)
            assertFalse(caught)
            assertFalse(carriedOn)
        }
}
