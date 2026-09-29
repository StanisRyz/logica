package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebLeaderboardTest {
    private class FakeBridge(
        var supported: Boolean = true,
    ) : WebLeaderboardBridge {
        val scores = mutableListOf<Int>()
        val calls = mutableListOf<Pair<String, Int>>()
        var accept = true
        var snapshot: WebLeaderboardSnapshot? = WebLeaderboardSnapshot(listOf(WebLeaderboardEntry(1, 7, "Аня")), playerRank = 1)

        override fun isLeaderboardsSupported(): Boolean = supported

        override suspend fun setLeaderboardScore(
            name: String,
            score: Int,
        ): Boolean {
            scores += score
            calls += name to score
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

            controller.submit(SOLVED, "A", 3)
            controller.submit(SOLVED, "A", 5) // coalesced: only the latest total is sent
            scope.advanceUntilIdle()
            controller.submit(SOLVED, "A", 5) // not a growth
            controller.submit(SOLVED, "A", 4)
            scope.advanceUntilIdle()
            assertEquals(listOf(5), bridge.scores)

            controller.submit(SOLVED, "B", 2) // a new Player's own, lower total
            scope.advanceUntilIdle()
            assertEquals(listOf(5, 2), bridge.scores)

            // A refused call is retried by the next growth only.
            bridge.accept = false
            controller.submit(SOLVED, "B", 4)
            scope.advanceUntilIdle()
            bridge.accept = true
            controller.submit(SOLVED, "B", 4)
            scope.advanceUntilIdle()
            assertEquals(listOf(5, 2, 4, 4), bridge.scores)
        }

    @Test
    fun loadPublishesTheTableOrUnavailableAndStandaloneDoesNothing() =
        runTest {
            val bridge = FakeBridge()
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val controller = WebLeaderboardController(bridge, scope)
            controller.load(SOLVED)
            scope.advanceUntilIdle()
            assertEquals(WebLeaderboardState.Ready(checkNotNull(bridge.snapshot)), controller.state(SOLVED).value)

            bridge.snapshot = null
            controller.load(SOLVED)
            scope.advanceUntilIdle()
            assertEquals(WebLeaderboardState.Unavailable, controller.state(SOLVED).value)

            val standalone = FakeBridge(supported = false)
            val offline = WebLeaderboardController(standalone, scope)
            offline.submit(SOLVED, "A", 3)
            offline.load(SOLVED)
            scope.advanceUntilIdle()
            assertEquals(emptyList(), standalone.scores)
            assertEquals(WebLeaderboardState.Idle, offline.state(SOLVED).value)
        }

    @Test
    fun eachGameHasItsOwnTableAndASentValueMarksItsTableStale() =
        runTest {
            val bridge = FakeBridge()
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val controller = WebLeaderboardController(bridge, scope)
            val sudoku = WebLeaderboardController.ratingLeaderboard(PuzzleType.SUDOKU)
            val best2048 = WebLeaderboardController.ratingLeaderboard(PuzzleType.GAME_2048)
            assertEquals("rating_sudoku", sudoku)
            assertEquals("best_2048", best2048)

            controller.load(sudoku)
            scope.advanceUntilIdle()
            assertTrue(controller.state(sudoku).value is WebLeaderboardState.Ready)
            assertEquals(WebLeaderboardState.Idle, controller.state(best2048).value)

            controller.submit(sudoku, "A", 12)
            controller.submit(best2048, "A", 4096)
            controller.submit(sudoku, "A", 0) // no rating yet: nothing to send
            scope.advanceUntilIdle()
            assertEquals(listOf(sudoku to 12, best2048 to 4096), bridge.calls)
            assertEquals(WebLeaderboardState.Idle, controller.state(sudoku).value)
        }

    private companion object {
        const val SOLVED = WebLeaderboardController.SOLVED_LEADERBOARD
    }
}
