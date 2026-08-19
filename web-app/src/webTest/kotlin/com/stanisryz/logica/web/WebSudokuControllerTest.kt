package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebSudokuControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun frozenLevelOneLoadsOneDatasetAndAcceptsPencilInput() =
        runTest {
            var loadedPack: Difficulty? = null
            var loadedDataset: Pair<SudokuDatasetVersion, SudokuDifficulty>? = null
            val controller =
                WebSudokuController(
                    loadPack = { loadedPack = it },
                    loadDataset = { version, difficulty -> loadedDataset = version to difficulty },
                    progression = FakeWebCatalogProgressAccess(),
                    levelPack = fixedMediumLevelOne,
                    dataset = fixedDataset,
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()

            val playing = assertIs<WebSudokuState.Playing>(controller.state)
            assertEquals(Difficulty.MEDIUM, loadedPack)
            assertEquals(SudokuDatasetVersion.V1 to SudokuDifficulty.MEDIUM, loadedDataset)
            assertEquals(PuzzleSeed(22), playing.definition.seed)

            val position = SudokuPosition(0, 0)
            controller.selectCell(position)
            controller.togglePencilMode()
            controller.inputDigit(1)

            val updated = assertIs<WebSudokuState.Playing>(controller.state)
            assertTrue(
                updated.game
                    .cellAt(position)
                    .candidates
                    .contains(1),
            )
        }

    private val fixedMediumLevelOne =
        object : CatalogLevelPack {
            override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
                assertEquals(PuzzleType.SUDOKU, levelId.puzzleType)
                assertEquals(Difficulty.MEDIUM, levelId.difficulty)
                assertEquals(CatalogLevelPacks.FIRST_LEVEL, levelId.levelNumber)
                assertEquals(CatalogLevelPackVersion.V1, levelId.packVersion)
                return CatalogLevelPackResult.Success(
                    CatalogLevelDefinition(levelId, PuzzleSeed(22), GeneratorVersion(1)),
                )
            }
        }

    private val fixedDataset =
        object : SudokuDataset {
            override fun availableCount(
                version: SudokuDatasetVersion,
                difficulty: SudokuDifficulty,
            ): SudokuDatasetResult<Int> = SudokuDatasetResult.Success(1)

            override fun getPuzzle(id: SudokuPuzzleId): SudokuDatasetResult<SudokuPuzzle> = SudokuDatasetResult.Success(puzzle)

            override fun selectPuzzle(
                version: SudokuDatasetVersion,
                difficulty: SudokuDifficulty,
                selector: Long,
            ): SudokuDatasetResult<SudokuPuzzle> {
                assertEquals(SudokuDatasetVersion.V1, version)
                assertEquals(SudokuDifficulty.MEDIUM, difficulty)
                assertEquals(22L, selector)
                return SudokuDatasetResult.Success(puzzle)
            }
        }

    private val puzzle =
        SudokuPuzzle(
            id = SudokuPuzzleId(SudokuDatasetVersion.V1, SudokuDifficulty.MEDIUM, "0".repeat(64)),
            givens = "050703060007000800000816000000030000005000100730040086906000204840572093000409000",
            solution = "158723469367954821294816375619238547485697132732145986976381254841572693523469718",
            upstreamRatingTenths = 20,
        )
}
