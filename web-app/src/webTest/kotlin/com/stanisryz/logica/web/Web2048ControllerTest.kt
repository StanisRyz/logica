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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun firstV2TargetCrossingAdvancesOnceWhileFreeplayContinuesThroughGameOver() =
        runTest {
            val progression = FakeWebCatalogProgressAccess()
            val controller =
                Web2048Controller(
                    loadPack = {},
                    progression = progression,
                    levelPack = fixedMediumLevelOne,
                    engineFactory = { puzzleId -> scriptedV2Engine(puzzleId) },
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            controller.move(Game2048Direction.LEFT)
            advanceUntilIdle()

            val freeplay = assertIs<Web2048State.Playing>(controller.state)
            assertEquals(Game2048Status.IN_PROGRESS, freeplay.game.status)
            assertEquals(true, freeplay.game.goalReached)
            assertEquals(1, progression.advanceCalls)
            assertEquals(2, assertIs<WebCatalogCompletionState.Saved>(controller.completionState).nextLevel.levelNumber.value)

            controller.finishMotion(assertNotNull(freeplay.motionRevision))
            controller.move(Game2048Direction.RIGHT)
            val terminal = assertIs<Web2048State.Playing>(controller.state)
            controller.finishMotion(assertNotNull(terminal.motionRevision))
            advanceUntilIdle()

            assertEquals(Game2048Status.SOLVED, assertIs<Web2048State.Playing>(controller.state).game.status)
            assertEquals(1, progression.advanceCalls)
            assertEquals(2, assertIs<WebCatalogCompletionState.Saved>(controller.completionState).nextLevel.levelNumber.value)
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
