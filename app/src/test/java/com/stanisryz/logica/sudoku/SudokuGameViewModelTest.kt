package com.stanisryz.logica.sudoku

import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.FrozenLevelRepository
import com.stanisryz.logica.economy.GatedHintWallet
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCatalogProvider
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Sudoku input on the Android host: undo across a hint. */
@OptIn(ExperimentalCoroutinesApi::class)
class SudokuGameViewModelTest {
    private val work = StandardTestDispatcher()
    private val wallet = GatedHintWallet()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(work.scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aHintKeepsTheUndoHistoryAndUndoLeavesItsCellAndCount() {
        val viewModel = loadedViewModel()
        val (first, second, hinted) = emptyCells(viewModel).take(3)
        // Two reversible moves: pencil marks in two empty cells.
        viewModel.togglePencilMode()
        viewModel.selectCell(first)
        viewModel.inputDigit(3)
        viewModel.selectCell(second)
        viewModel.inputDigit(3)
        viewModel.togglePencilMode()
        // The hint opens a third, selected empty cell.
        viewModel.selectCell(hinted)
        wallet.gate.complete(Unit)
        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()
        assertTrue(viewModel.ready().canUndo)

        viewModel.undo()

        val ready = viewModel.ready()
        assertFalse(
            ready.game
                .cellAt(second)
                .candidates
                .contains(3),
        )
        assertTrue(
            ready.game
                .cellAt(first)
                .candidates
                .contains(3),
        )
        assertEquals(SudokuCellStatus.CORRECT, ready.game.cellAt(hinted).status)
        assertEquals(1, ready.game.hintsUsed)
        assertTrue(ready.canUndo)
    }

    private fun loadedViewModel(): SudokuGameViewModel {
        val levels = FrozenLevelRepository(PuzzleType.SUDOKU)
        val viewModel =
            SudokuGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels) { "attempt" },
                completionRepository = RecordingCompletionRepository(),
                economyRepository = wallet,
                provider = SudokuCatalogProvider(canonicalDataset),
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun SudokuGameViewModel.ready(): SudokuGameUiState.Ready = uiState.value as SudokuGameUiState.Ready

    private fun emptyCells(viewModel: SudokuGameViewModel): List<SudokuPosition> =
        (0 until 81)
            .map(SudokuPosition::fromIndex)
            .filter {
                viewModel
                    .ready()
                    .game
                    .cellAt(it)
                    .status == SudokuCellStatus.EMPTY
            }

    private companion object {
        val canonicalDataset =
            BinarySudokuDataset { version, difficulty ->
                val directory = listOf(File("puzzle-data"), File("../puzzle-data")).first(File::isDirectory)
                File(directory, "sudoku/v${version.value}/${difficulty.name.lowercase()}.sdk").takeIf(File::isFile)?.readBytes()
            }
    }
}
