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
import com.stanisryz.logica.ui.components.GameKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            assertEquals(PuzzleSeed(22), (playing.source as WebGameplaySource.CatalogLevel).definition.seed)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun catalogHintWithEmptyInventoryExplainsItselfAndLeavesTheBoardUnchanged() =
        runTest {
            val store =
                object : WebGameplayStore {
                    var hints = 0

                    override fun tryConsumeHint(): Boolean {
                        if (hints <= 0) return false
                        hints--
                        return true
                    }
                }
            val controller =
                WebSudokuController(
                    loadPack = {},
                    loadDataset = { _, _ -> },
                    progression = FakeWebCatalogProgressAccess(),
                    store = store,
                    levelPack = fixedMediumLevelOne,
                    dataset = fixedDataset,
                    scope = this,
                )
            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()
            val before = assertIs<WebSudokuState.Playing>(controller.state).game

            controller.requestHint()

            assertTrue(controller.hintsExhaustedNotice)
            assertEquals(before, assertIs<WebSudokuState.Playing>(controller.state).game)

            controller.dismissHintsExhaustedNotice()
            store.hints = 1
            controller.requestHint()

            assertFalse(controller.hintsExhaustedNotice)
            assertEquals(0, store.hints)
            assertEquals(1, assertIs<WebSudokuState.Playing>(controller.state).game.hintsUsed)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun hardwareKeysMoveTheSelectionAndEnterDigitsLikeTheKeypad() =
        runTest {
            val controller =
                WebSudokuController(
                    loadPack = {},
                    loadDataset = { _, _ -> },
                    progression = FakeWebCatalogProgressAccess(),
                    levelPack = fixedMediumLevelOne,
                    dataset = fixedDataset,
                    scope = this,
                )
            controller.selectDifficulty(Difficulty.MEDIUM)
            advanceUntilIdle()

            // The first arrow selects the top-left cell; later arrows move and stop at the edge.
            controller.onHardwareKey(GameKey.Right)
            assertEquals(SudokuPosition(0, 0), assertIs<WebSudokuState.Playing>(controller.state).selectedCell)
            controller.onHardwareKey(GameKey.Up)
            assertEquals(SudokuPosition(0, 0), assertIs<WebSudokuState.Playing>(controller.state).selectedCell)

            // (0, 0) is empty and its answer is 1.
            controller.onHardwareKey(GameKey.Digit(1))
            assertEquals(1, assertIs<WebSudokuState.Playing>(controller.state).game.cellAt(SudokuPosition(0, 0)).value)

            controller.onHardwareKey(GameKey.Down)
            controller.onHardwareKey(GameKey.Letter('з'))
            assertTrue(assertIs<WebSudokuState.Playing>(controller.state).isPencilMode)
            assertEquals(SudokuPosition(1, 0), assertIs<WebSudokuState.Playing>(controller.state).selectedCell)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aHintKeepsTheUndoHistoryAndUndoLeavesItsCellAndCount() =
        runTest {
            val controller = playingController()
            // Two reversible moves: pencil marks in (0, 0) and (0, 2).
            controller.togglePencilMode()
            controller.selectCell(SudokuPosition(0, 0))
            controller.inputDigit(3)
            controller.selectCell(SudokuPosition(0, 2))
            controller.inputDigit(3)
            controller.togglePencilMode()
            // The hint opens the selected empty (0, 4), whose answer is 2.
            controller.selectCell(SudokuPosition(0, 4))
            controller.requestHint()
            assertTrue(controller.canUndo)

            controller.undo()

            val game = assertIs<WebSudokuState.Playing>(controller.state).game
            assertFalse(game.cellAt(SudokuPosition(0, 2)).candidates.contains(3))
            assertTrue(game.cellAt(SudokuPosition(0, 0)).candidates.contains(3))
            assertEquals(2, game.cellAt(SudokuPosition(0, 4)).value)
            assertEquals(1, game.hintsUsed)
            assertTrue(controller.canUndo)
        }

    private fun kotlinx.coroutines.test.TestScope.playingController(): WebSudokuController {
        val controller =
            WebSudokuController(
                loadPack = {},
                loadDataset = { _, _ -> },
                progression = FakeWebCatalogProgressAccess(),
                store =
                    object : WebGameplayStore {
                        override fun tryConsumeHint(): Boolean = true
                    },
                levelPack = fixedMediumLevelOne,
                dataset = fixedDataset,
                scope = this,
            )
        controller.selectDifficulty(Difficulty.MEDIUM)
        testScheduler.advanceUntilIdle()
        return controller
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
