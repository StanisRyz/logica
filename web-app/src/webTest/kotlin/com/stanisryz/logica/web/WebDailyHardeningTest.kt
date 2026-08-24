package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTransition
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Stage 45.8b1 regressions: source-aware Loading/Error retry never falls into the Catalog flow,
 * Daily save retry repeats only Daily persistence (never Statistics), and a completed Daily 2048
 * entry is not replayable from the terminal screen while FAILED stays retryable.
 */
class WebDailyHardeningTest {
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

    /** Fails the first Daily local mutation to exercise the SaveError path without cloud noise. */
    private class FlakyDailyAccess(
        private val delegate: WebDailyGameplayAccess,
    ) : WebDailyGameplayAccess {
        var failFirst = true

        override fun recordTerminalResult(
            attempt: WebDailyAttempt,
            outcome: WebStatisticsTerminalOutcome,
            wordAttemptsUsed: Int?,
        ): WebDailyRecordResult =
            if (failFirst) {
                failFirst = false
                WebDailyRecordResult.PersistenceFailed(IllegalStateException("storage rejected the update"))
            } else {
                delegate.recordTerminalResult(attempt, outcome, wordAttemptsUsed)
            }
    }

    private fun readyCoordinator(): Pair<WebDailyRepository, WebDailyGameplayCoordinator> {
        val repository =
            WebDailyRepository(WebCatalogProgressScope.STANDALONE, FakeDailyStore()) { today }.also { it.loadLocal() }
        val session = object : WebDailySessionAccess {
            override val dailyBinding =
                MutableStateFlow<WebDailyBinding>(
                    WebDailyBinding.Ready(WebPlayerContextToken(9L), repository, null, WebDailyCloudSyncStatus.LOCAL_ONLY),
                )

            override fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready) = Unit
        }
        return repository to WebDailyGameplayCoordinator(session) { today }
    }

    private val testWordRuntime =
        WordRuntime(
            generator =
                object : PuzzleGenerator<WordPuzzle> {
                    override val type = PuzzleType.WORD
                    override val version = GeneratorVersion(2)

                    override fun generate(
                        seed: PuzzleSeed,
                        difficulty: Difficulty,
                    ): WordPuzzle = WordPuzzle(PuzzleId(type, difficulty, seed, version), TEST_ANSWER)
                },
            allowedGuesses =
                object : WordAllowedGuesses {
                    override val size = 1

                    override fun contains(normalizedWord: String): Boolean = normalizedWord == TEST_ANSWER

                    override fun all(): List<String> = listOf(TEST_ANSWER)
                },
            requiredResourcePaths = listOf("/word/v2/test_answers.txt"),
        )

    private companion object {
        const val TEST_ANSWER = "тесто"
    }

    private fun solvedFact(repository: WebDailyRepository): Boolean =
        repository.snapshot.value.days.getValue(today).facts(PuzzleType.WORD).solved

    private fun game2048Facts(repository: WebDailyRepository) =
        repository.snapshot.value.days.getValue(today).facts(PuzzleType.GAME_2048)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun dailyLoadingFailureRetriesTheSameDailyIdentityWithoutEnteringCatalogFlow() =
        runTest {
            val (repository, coordinator) = readyCoordinator()
            val progression = FakeWebCatalogProgressAccess(initialLevel = 3)
            val started = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.WORD)).attempt

            var resolverCalls = 0
            val controller =
                WebWordController(
                    loadPack = {},
                    loadRuntimeResources = {},
                    progression = progression,
                    runtimeResolver = {
                        resolverCalls++
                        if (resolverCalls == 1) error("lexicon unavailable")
                        testWordRuntime
                    },
                    daily = coordinator,
                    scope = this,
                )

            controller.startDaily(started)
            advanceUntilIdle()

            // The Daily runtime-data failure keeps the Daily launch identity; no Catalog retry.
            val error = assertIs<WebWordState.Error>(controller.state)
            assertTrue(error.launch.isDaily)
            assertEquals(0, progression.advanceCalls)
            assertFalse(solvedFact(repository))

            controller.retryLoading()
            advanceUntilIdle()

            // The same deterministic identity is retried straight back into Daily gameplay.
            val playing = assertIs<WebWordState.Playing>(controller.state)
            val source = assertIs<WebGameplaySource.DailyChallenge>(playing.source)
            assertEquals(started.definition, source.attempt.definition)
            assertEquals(started.playerContextToken, source.attempt.playerContextToken)
            assertEquals(WebDailyCompletionState.Idle, controller.dailyCompletionState)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun dailySaveFailureShowsSaveErrorAndRetrySaveRepeatsOnlyDailyPersistence() =
        runTest {
            val (repository, coordinator) = readyCoordinator()
            val progression = FakeWebCatalogProgressAccess(initialLevel = 7)
            val statistics = RecordingStatistics()
            val flaky = FlakyDailyAccess(coordinator)
            val started = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.WORD)).attempt

            val controller =
                WebWordController(
                    loadPack = {},
                    loadRuntimeResources = {},
                    progression = progression,
                    runtimeResolver = { testWordRuntime },
                    statistics = statistics,
                    daily = flaky,
                    scope = this,
                )
            controller.startDaily(started)
            advanceUntilIdle()

            TEST_ANSWER.forEachIndexed(controller::setLetter)
            controller.submit()
            advanceUntilIdle()

            // The real terminal attempt recorded Statistics exactly once, but local Daily
            // durability failed: the terminal state is SaveError, never Saved.
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), statistics.outcomes)
            val saveError = assertIs<WebDailyCompletionState.SaveError>(controller.dailyCompletionState)
            assertEquals(WebStatisticsTerminalOutcome.SOLVED, saveError.outcome)
            assertFalse(solvedFact(repository))

            controller.retryDailySave()

            // Retry repeats only the original Daily mutation with the same facts.
            val saved = assertIs<WebDailyCompletionState.Saved>(controller.dailyCompletionState)
            assertEquals(WebStatisticsTerminalOutcome.SOLVED, saved.outcome)
            assertTrue(solvedFact(repository))
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), statistics.outcomes)
            assertEquals(0, progression.advanceCalls)
        }

    private class ScriptedEngine(
        puzzleId: Game2048PuzzleId,
        private var script: List<Game2048State>,
    ) : Web2048GameEngine {
        private val start = Game2048Engine(puzzleId).start()
        private var index = 0

        override fun start(): Game2048State = start

        override fun moveWithTrace(
            state: Game2048State,
            direction: Game2048Direction,
        ): Game2048MoveTransition {
            val next = script.getOrNull(index++)
            return next
                ?.let { updated -> Game2048MoveTransition(updated, Game2048MoveTrace(direction, emptyList(), emptyList(), null, 0L)) }
                ?: Game2048MoveTransition(state, null)
        }

        override fun retry(state: Game2048State): Game2048State = start
    }

    private fun gameOver(puzzleId: Game2048PuzzleId, score: Long): Game2048State {
        val start = Game2048Engine(puzzleId).start()
        return Game2048State(
            puzzleId = puzzleId,
            board = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2),
            score = score,
            nextSpawnIndex = start.nextSpawnIndex,
            status = if (score >= 30_000L) Game2048Status.SOLVED else Game2048Status.FAILED,
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun dailyGame2048SolvedEntryIsNotReplayableWhileFailedRemainsRetryable() =
        runTest {
            // Solved game over -> Saved(SOLVED); retry from the terminal screen is a no-op.
            val (solvedRepository, solvedCoordinator) = readyCoordinator()
            val solvedStatistics = RecordingStatistics()
            val solvedController =
                Web2048Controller(
                    loadPack = {},
                    progression = FakeWebCatalogProgressAccess(),
                    engineFactory = { puzzleId ->
                        ScriptedEngine(
                            puzzleId,
                            listOf(gameOver(puzzleId, score = 30_000L)),
                        )
                    },
                    statistics = solvedStatistics,
                    daily = solvedCoordinator,
                    scope = this,
                )
            val solvedAttempt =
                assertIs<WebDailyStartResult.Started>(solvedCoordinator.start(PuzzleType.GAME_2048)).attempt
            solvedController.startDaily(solvedAttempt)
            advanceUntilIdle()

            solvedController.move(Game2048Direction.LEFT)
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), solvedStatistics.outcomes)

            val terminal = assertIs<Web2048State.Playing>(solvedController.state)
            assertTrue(game2048Facts(solvedRepository).solved)
            val saved = assertIs<WebDailyCompletionState.Saved>(solvedController.dailyCompletionState)
            assertEquals(WebStatisticsTerminalOutcome.SOLVED, saved.outcome)

            solvedController.retry()
            assertEquals(terminal, solvedController.state)

            // A separate FAILED attempt stays Saved(FAILED) and remains a real fresh retry.
            val (failedRepository, failedCoordinator) = readyCoordinator()
            val failedStatistics = RecordingStatistics()
            val failedController =
                Web2048Controller(
                    loadPack = {},
                    progression = FakeWebCatalogProgressAccess(),
                    engineFactory = { puzzleId ->
                        ScriptedEngine(
                            puzzleId,
                            listOf(gameOver(puzzleId, score = 0L)),
                        )
                    },
                    statistics = failedStatistics,
                    daily = failedCoordinator,
                    scope = this,
                )
            val failedAttempt =
                assertIs<WebDailyStartResult.Started>(failedCoordinator.start(PuzzleType.GAME_2048)).attempt
            failedController.startDaily(failedAttempt)
            advanceUntilIdle()

            failedController.move(Game2048Direction.LEFT)
            val failedSaved = assertIs<WebDailyCompletionState.Saved>(failedController.dailyCompletionState)
            assertEquals(WebStatisticsTerminalOutcome.FAILED, failedSaved.outcome)
            assertTrue(game2048Facts(failedRepository).failedSeen)

            failedController.finishMotion(assertNotNull((failedController.state as Web2048State.Playing).motionRevision))
            failedController.retry()
            advanceUntilIdle()
            val retried = assertIs<Web2048State.Playing>(failedController.state)
            assertEquals(Game2048Status.IN_PROGRESS, retried.game.status)
            assertEquals(WebDailyCompletionState.Idle, failedController.dailyCompletionState)
        }
}

