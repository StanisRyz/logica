package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTrace
import com.stanisryz.logica.puzzle.core.game2048.Game2048MoveTransition
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Web2048ControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun frozenLevelOneUsesCommonTraceAndLocksOverlappingMoves() =
        runTest {
            var loadedDifficulty: Difficulty? = null
            val controller =
                Web2048Controller(
                    loadPack = { loadedDifficulty = it },
                    progression = FakeWebCatalogProgressAccess(),
                    levelPack = fixedMediumLevelOne,
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()

            val playing = assertIs<Web2048State.Playing>(controller.state)
            assertEquals(Difficulty.MEDIUM, loadedDifficulty)
            assertEquals(Game2048GeneratorVersion.V2, playing.game.puzzleId.generatorVersion)
            val engine = Game2048Engine(playing.game.puzzleId)
            val direction =
                Game2048Direction.entries.first { candidate ->
                    engine.moveWithTrace(playing.game, candidate).trace != null
                }
            val expected = engine.moveWithTrace(playing.game, direction)

            controller.move(direction)
            val animating = assertIs<Web2048State.Playing>(controller.state)
            assertEquals(expected.state, animating.game)
            assertEquals(expected.trace, animating.motionTrace)
            val revision = assertNotNull(animating.motionRevision)

            controller.move(direction)
            assertEquals(animating, controller.state)
            controller.finishMotion(revision + 1L)
            assertEquals(animating, controller.state)

            controller.finishMotion(revision)
            val finished = assertIs<Web2048State.Playing>(controller.state)
            assertNull(finished.motionRevision)
            assertNull(finished.motionTrace)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun firstV2TargetCrossingSurvivesImmediateExitAndFreeplayCannotAdvanceTwice() =
        runTest {
            val exitProgression = FakeWebCatalogProgressAccess()
            val exitStatistics = RecordingGameplayStatistics()
            val exitController =
                Web2048Controller(
                    loadPack = {},
                    progression = exitProgression,
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2Engine(puzzleId) },
                    statistics = exitStatistics,
                    scope = this,
                )

            exitController.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            exitController.move(Game2048Direction.LEFT)
            exitController.showDifficultySelector()

            val current =
                assertIs<WebCatalogLevelResolution.Resolved>(
                    exitProgression.resolveCurrentLevel(PuzzleType.GAME_2048, Difficulty.MEDIUM),
                )
            assertEquals(2, current.attempt.levelId.levelNumber.value)
            assertEquals(1, exitProgression.advanceCalls)
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), exitStatistics.outcomes)

            val freeplayProgression = FakeWebCatalogProgressAccess()
            val freeplayStatistics = RecordingGameplayStatistics()
            val freeplayController =
                Web2048Controller(
                    loadPack = {},
                    progression = freeplayProgression,
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2Engine(puzzleId) },
                    statistics = freeplayStatistics,
                    scope = this,
                )

            freeplayController.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            freeplayController.move(Game2048Direction.LEFT)

            val freeplay = assertIs<Web2048State.Playing>(freeplayController.state)
            assertEquals(Game2048Status.IN_PROGRESS, freeplay.game.status)
            assertEquals(true, freeplay.game.goalReached)
            assertEquals(1, freeplayProgression.advanceCalls)
            assertEquals(
                2,
                assertIs<WebCatalogCompletionState.Saved>(freeplayController.completionState).nextLevel.levelNumber.value,
            )

            freeplayController.finishMotion(assertNotNull(freeplay.motionRevision))
            freeplayController.move(Game2048Direction.RIGHT)
            val terminal = assertIs<Web2048State.Playing>(freeplayController.state)
            freeplayController.finishMotion(assertNotNull(terminal.motionRevision))

            assertEquals(Game2048Status.SOLVED, assertIs<Web2048State.Playing>(freeplayController.state).game.status)
            assertEquals(1, freeplayProgression.advanceCalls)
            assertEquals(
                2,
                assertIs<WebCatalogCompletionState.Saved>(freeplayController.completionState).nextLevel.levelNumber.value,
            )
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), freeplayStatistics.outcomes)

            val failedStatistics = RecordingGameplayStatistics()
            val failedController =
                Web2048Controller(
                    loadPack = {},
                    progression = FakeWebCatalogProgressAccess(),
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2FailureEngine(puzzleId) },
                    statistics = failedStatistics,
                    scope = this,
                )
            failedController.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            failedController.move(Game2048Direction.LEFT)
            // The dead end first offers the ad-paid undo; declining it records the failure.
            failedController.declineUndoOffer()
            assertEquals(listOf(WebStatisticsTerminalOutcome.FAILED), failedStatistics.outcomes)
        }

    @Test
    fun finishingACatalogFreeplayShowsTheClearedLevelAndRecordsNothingMore() =
        runTest {
            val progression = FakeWebCatalogProgressAccess()
            val statistics = RecordingGameplayStatistics()
            val controller =
                Web2048Controller(
                    loadPack = {},
                    progression = progression,
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2Engine(puzzleId) },
                    statistics = statistics,
                    scope = this,
                )
            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            controller.move(Game2048Direction.LEFT)
            controller.finishMotion(assertNotNull(assertIs<Web2048State.Playing>(controller.state).motionRevision))

            controller.finish()

            val finished = assertIs<Web2048State.Playing>(controller.state)
            assertTrue(finished.isOver)
            assertEquals(Game2048Status.IN_PROGRESS, finished.game.status)
            // The level was cleared at the crossing; finishing writes nothing a second time.
            assertEquals(1, progression.advanceCalls)
            assertEquals(listOf(WebStatisticsTerminalOutcome.SOLVED), statistics.outcomes)
            controller.move(Game2048Direction.RIGHT)
            assertEquals(finished, controller.state)
            // The card offers Next Level from the saved clear, exactly as after a game over.
            assertEquals(2, assertIs<WebCatalogCompletionState.Saved>(controller.completionState).nextLevel.levelNumber.value)
        }

    @Test
    fun aDeadEndBeforeTheTargetOffersTheUndoOnceAndHoldsTheResultUntilAnswered() =
        runTest {
            val statistics = RecordingGameplayStatistics()
            val economy = RecordingEconomy()
            val controller =
                Web2048Controller(
                    loadPack = {},
                    progression = FakeWebCatalogProgressAccess(),
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2FailureEngine(puzzleId) },
                    statistics = statistics,
                    economy = economy,
                    scope = this,
                )
            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            val beforeLoss = assertIs<Web2048State.Playing>(controller.state).game

            controller.move(Game2048Direction.LEFT)

            // The offer is open and nothing is recorded yet; leaving now counts as an unfinished level.
            assertTrue(controller.undoOffered)
            assertEquals(Game2048Status.FAILED, assertIs<Web2048State.Playing>(controller.state).game.status)
            assertEquals(emptyList(), statistics.outcomes)
            assertEquals(emptyList(), economy.results)

            // A watched ad takes the losing move back, with no economy effect.
            controller.undoLosingMoveAfterAd()
            val restored = assertIs<Web2048State.Playing>(controller.state)
            assertFalse(controller.undoOffered)
            assertEquals(beforeLoss, restored.game)
            assertEquals(Game2048Status.IN_PROGRESS, restored.game.status)
            assertEquals(emptyList(), economy.results)

            // The next dead end of the same attempt is final: no second offer, one FAILED and one life.
            controller.move(Game2048Direction.LEFT)
            assertFalse(controller.undoOffered)
            assertEquals(listOf(WebStatisticsTerminalOutcome.FAILED), statistics.outcomes)
            assertEquals(listOf(false), economy.results)
        }

    private class RecordingEconomy : WebGameplayEconomy {
        val results = mutableListOf<Boolean>()

        override fun recordTerminalResult(
            solved: Boolean,
            gemsEarned: Int,
        ) {
            results += solved
        }

        override fun recordAbandonedAttempt() = Unit
    }

    private fun scriptedV2Engine(puzzleId: Game2048PuzzleId): Web2048GameEngine {
        val start = Game2048Engine(puzzleId).start()
        val targetCrossed = start.copy(score = 30_000L, status = Game2048Status.IN_PROGRESS)
        val gameOver =
            Game2048State(
                puzzleId = puzzleId,
                board = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2),
                score = 30_000L,
                nextSpawnIndex = targetCrossed.nextSpawnIndex,
                status = Game2048Status.SOLVED,
            )
        return object : Web2048GameEngine {
            private var move = 0

            override fun start(): Game2048State = start

            override fun moveWithTrace(
                state: Game2048State,
                direction: Game2048Direction,
            ): Game2048MoveTransition =
                Game2048MoveTransition(
                    state = if (move++ == 0) targetCrossed else gameOver,
                    trace = Game2048MoveTrace(direction, emptyList(), emptyList(), null, 0L),
                )

            override fun retry(state: Game2048State): Game2048State = start
        }
    }

    private fun scriptedV2FailureEngine(puzzleId: Game2048PuzzleId): Web2048GameEngine {
        val start = Game2048Engine(puzzleId).start()
        val gameOver =
            Game2048State(
                puzzleId = puzzleId,
                board = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2),
                score = 0L,
                nextSpawnIndex = start.nextSpawnIndex,
                status = Game2048Status.FAILED,
            )
        return object : Web2048GameEngine {
            override fun start(): Game2048State = start

            override fun moveWithTrace(
                state: Game2048State,
                direction: Game2048Direction,
            ): Game2048MoveTransition =
                Game2048MoveTransition(
                    state = gameOver,
                    trace = Game2048MoveTrace(direction, emptyList(), emptyList(), null, 0L),
                )

            override fun retry(state: Game2048State): Game2048State = start
        }
    }

    private class RecordingGameplayStatistics : WebGameplayStatistics {
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
            outcomes += outcome
            return WebStatisticsAttemptRecordResult.Recorded
        }
    }

    private val fixedMediumLevelOne =
        object : CatalogLevelPack {
            override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
                assertEquals(PuzzleType.GAME_2048, levelId.puzzleType)
                assertEquals(Difficulty.MEDIUM, levelId.difficulty)
                assertEquals(CatalogLevelPacks.FIRST_LEVEL, levelId.levelNumber)
                assertEquals(CatalogLevelPackVersion.V1, levelId.packVersion)
                return CatalogLevelPackResult.Success(
                    CatalogLevelDefinition(
                        levelId = levelId,
                        seed = PuzzleSeed(22),
                        generatorVersion = GeneratorVersion(2),
                    ),
                )
            }
        }
}
