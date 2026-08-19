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
