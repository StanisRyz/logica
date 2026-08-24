package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTransition
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Daily 2048 resolves only at the real terminal game state, unlike Catalog 2048 whose first V2
 * target crossing completes the level. A target crossing records nothing; a qualifying final state
 * records exactly one SOLVED and a separate pre-target game over exactly one FAILED, while Catalog
 * progression stays untouched the whole time.
 */
class WebDailyGame2048ControllerTest {
    private val today = DailyDate(2026, 8, 24)

    private class FakeDailyStore : WebDailyStore {
        var snapshot: WebDailySnapshotV1 = WebDailySnapshotV1.EMPTY

        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
        }
    }

    private class RecordingStatistics : WebGameplayStatistics {
        private var nextAttemptIdentity = 0L
        val outcomes = mutableListOf<WebStatisticsTerminalOutcome>()

        override fun startAttempt(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): WebStatisticsAttempt =
            WebStatisticsAttempt(
                identity = WebStatisticsAttemptIdentity(++nextAttemptIdentity),
                playerContextToken = null,
                repository = null,
                puzzleType = puzzleType,
                difficulty = difficulty,
            )

        override fun recordTerminalResult(
            attempt: WebStatisticsAttempt,
            outcome: WebStatisticsTerminalOutcome,
            hintsUsed: Int,
            wordAttemptsUsed: Int?,
        ): WebStatisticsAttemptRecordResult {
            attempt.markRecorded()
            outcomes += outcome
            return WebStatisticsAttemptRecordResult.Recorded
        }
    }

    private class ScriptedEngine(
        puzzleId: Game2048PuzzleId,
        firstScript: List<Game2048State>,
        private val retryScript: List<Game2048State>,
    ) : Web2048GameEngine {
        private val start = Game2048Engine(puzzleId).start()
        private var script = firstScript
        private var index = 0

        override fun start(): Game2048State = start

        override fun moveWithTrace(
            state: Game2048State,
            direction: Game2048Direction,
        ): Game2048MoveTransition {
            val next = script.getOrNull(index++)
            return next?.let { updated -> Game2048MoveTransition(updated, Game2048MoveTrace(direction, emptyList(), emptyList(), null, 0L)) }
                ?: Game2048MoveTransition(state, null)
        }

        override fun retry(state: Game2048State): Game2048State {
            script = retryScript
            index = 0
            return start
        }
    }

    private fun gameOver(puzzleId: Game2048PuzzleId, start: Game2048State, score: Long): Game2048State =
        Game2048State(
            puzzleId = puzzleId,
            // A full checker board has no legal move left in any direction.
            board = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2),
            score = score,
            nextSpawnIndex = start.nextSpawnIndex,
            status = if (score >= 30_000L) Game2048Status.SOLVED else Game2048Status.FAILED,
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun targetCrossingRecordsNothingAndRealGameOverResolvesExactlyOnceWithoutCatalogAdvancement() =
        runTest {
            val store = FakeDailyStore()
            val repository =
                WebDailyRepository(WebCatalogProgressScope.STANDALONE, store) { today }.also { it.loadLocal() }
            val session = object : WebDailySessionAccess {
                override val dailyBinding =
                    MutableStateFlow<WebDailyBinding>(
                        WebDailyBinding.Ready(WebPlayerContextToken(5L), repository, null, WebDailyCloudSyncStatus.LOCAL_ONLY),
                    )

                override fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready) = Unit
            }
            val coordinator = WebDailyGameplayCoordinator(session) { today }
            val progression = FakeWebCatalogProgressAccess(initialLevel = 4)
            val statistics = RecordingStatistics()
            val attempt = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.GAME_2048)).attempt

            val controller =
                Web2048Controller(
                    loadPack = {},
                    progression = progression,
                    engineFactory = { puzzleId ->
                        val start = Game2048Engine(puzzleId).start()
                        ScriptedEngine(
                            puzzleId,
                            firstScript = listOf(
                                // Move 1 crosses the V2 target while play continues...
                                start.copy(score = 30_000L, status = Game2048Status.IN_PROGRESS),
                                // ...and move 2 ends the game with the goal reached.
                                gameOver(puzzleId, start, score = 30_000L),
                            ),
                            retryScript = listOf(gameOver(puzzleId, start, score = 0L)),
                        )
                    },
                    statistics = statistics,
                    daily = coordinator,
                    scope = this,
                )

            controller.startDaily(attempt)
            advanceUntilIdle()

            // First move crosses the V2 target while play continues: nothing is recorded at all.
            controller.move(Game2048Direction.LEFT)
            controller.finishMotion(assertNotNull((controller.state as Web2048State.Playing).motionRevision))
            assertEquals(emptyList(), statistics.outcomes)
            val recordBeforeGameOver = repository.snapshot.value.days.getValue(today)
            assertFalse(recordBeforeGameOver.facts(PuzzleType.GAME_2048).solved)

            // The later real game over with the goal reached records exactly one SOLVED, once.
            controller.move(Game2048Direction.LEFT)
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), statistics.outcomes)
            assertTrue(repository.snapshot.value.days.getValue(today).facts(PuzzleType.GAME_2048).solved)

            // A separate fresh attempt whose game over stays below the target records one FAILED.
            controller.finishMotion(assertNotNull((controller.state as Web2048State.Playing).motionRevision))
            controller.retry()
            advanceUntilIdle()
            controller.move(Game2048Direction.LEFT)
            assertEquals(
                listOf(WebStatisticsTerminalOutcome.SOLVED, WebStatisticsTerminalOutcome.FAILED),
                statistics.outcomes,
            )
            val recordAfterBoth = repository.snapshot.value.days.getValue(today)
            assertTrue(recordAfterBoth.facts(PuzzleType.GAME_2048).failedSeen)
            assertTrue(recordAfterBoth.facts(PuzzleType.GAME_2048).solved)

            // Daily never advances Catalog progression in any of these scenarios.
            assertEquals(0, progression.advanceCalls)
            val current =
                assertIs<WebCatalogLevelResolution.Resolved>(
                    progression.resolveCurrentLevel(PuzzleType.GAME_2048, Difficulty.MEDIUM),
                )
            assertEquals(4, current.attempt.levelId.levelNumber.value)
        }
}

