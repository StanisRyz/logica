package com.stanisryz.logica.web

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebLeaderboardTest {
    private class FakeBridge(
        var supported: Boolean = true,
    ) : WebLeaderboardBridge {
        val scores = mutableListOf<Int>()
        var accept = true
        var snapshot: WebLeaderboardSnapshot? = WebLeaderboardSnapshot(listOf(WebLeaderboardEntry(1, 7, "Аня")), playerRank = 1)

        override fun isLeaderboardsSupported(): Boolean = supported

        override suspend fun setLeaderboardScore(
            name: String,
            score: Int,
        ): Boolean {
            scores += score
            return accept
        }

        override suspend fun leaderboardEntries(name: String): WebLeaderboardSnapshot? = snapshot
    }

    @Test
    fun onlyGrowthIsSubmittedAndAnotherPlayerStartsOver() =
        runTest {
            val bridge = FakeBridge()
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val controller = WebLeaderboardController(bridge, scope)

            controller.submitSolved("A", 3)
            controller.submitSolved("A", 5) // coalesced: only the latest total is sent
            scope.advanceUntilIdle()
            controller.submitSolved("A", 5) // not a growth
            controller.submitSolved("A", 4)
            scope.advanceUntilIdle()
            assertEquals(listOf(5), bridge.scores)

            controller.submitSolved("B", 2) // a new Player's own, lower total
            scope.advanceUntilIdle()
            assertEquals(listOf(5, 2), bridge.scores)

            // A refused call is retried by the next growth only.
            bridge.accept = false
            controller.submitSolved("B", 4)
            scope.advanceUntilIdle()
            bridge.accept = true
            controller.submitSolved("B", 4)
            scope.advanceUntilIdle()
            assertEquals(listOf(5, 2, 4, 4), bridge.scores)
        }

    @Test
    fun loadPublishesTheTableOrUnavailableAndStandaloneDoesNothing() =
        runTest {
            val bridge = FakeBridge()
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val controller = WebLeaderboardController(bridge, scope)
            controller.load()
            scope.advanceUntilIdle()
            assertEquals(WebLeaderboardState.Ready(checkNotNull(bridge.snapshot)), controller.state.value)

            bridge.snapshot = null
            controller.load()
            scope.advanceUntilIdle()
            assertEquals(WebLeaderboardState.Unavailable, controller.state.value)

            val standalone = FakeBridge(supported = false)
            val offline = WebLeaderboardController(standalone, scope)
            offline.submitSolved("A", 3)
            offline.load()
            scope.advanceUntilIdle()
            assertEquals(emptyList(), standalone.scores)
            assertEquals(WebLeaderboardState.Idle, offline.state.value)
        }
}
