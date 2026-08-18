package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
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

class WebCrownsControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun frozenLevelOneStartsCommonEngineAndAcceptsPencilInput() =
        runTest {
            var loadedDifficulty: Difficulty? = null
            val controller =
                WebCrownsController(
                    loadPack = { loadedDifficulty = it },
                    levelPack = fixedMediumLevelOne,
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()

            val playing = assertIs<WebCrownsState.Playing>(controller.state)
            assertEquals(Difficulty.MEDIUM, loadedDifficulty)
            assertEquals(PuzzleType.CROWNS, playing.definition.puzzleType)
            assertEquals(CatalogLevelPacks.FIRST_LEVEL, playing.definition.levelNumber)
            assertEquals(PuzzleSeed(22), playing.definition.seed)

            controller.selectValue(CrownsPlayerCell.MARKED)
            controller.togglePencilMode()
            val position = CrownsPosition(0, 0)
            controller.onCellTapped(position)

            val updated = assertIs<WebCrownsState.Playing>(controller.state)
            assertEquals(setOf(CrownsPlayerCell.MARKED), updated.game.pencilAt(position))
        }

    private val fixedMediumLevelOne =
        object : CatalogLevelPack {
            override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
                assertEquals(PuzzleType.CROWNS, levelId.puzzleType)
                assertEquals(Difficulty.MEDIUM, levelId.difficulty)
                assertEquals(CatalogLevelPacks.FIRST_LEVEL, levelId.levelNumber)
                assertEquals(CatalogLevelPackVersion.V1, levelId.packVersion)
                return CatalogLevelPackResult.Success(
                    CatalogLevelDefinition(
                        levelId = levelId,
                        seed = PuzzleSeed(22),
                        generatorVersion = GeneratorVersion(1),
                    ),
                )
            }
        }
}
