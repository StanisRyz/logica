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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Sudoku input on the Android host: undo across a hint and digit-first entry. */
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

    @Test
    fun digitFirstEntersTheActiveDigitInEveryTappedCell() {
        val viewModel = loadedViewModel()
        val puzzle = viewModel.ready().puzzle
        val digit = puzzle.solution[emptyCells(viewModel).first().index].digitToInt()
        val right = emptyCells(viewModel).filter { puzzle.solution[it.index].digitToInt() == digit }.take(2)
        val wrong = emptyCells(viewModel).first { puzzle.solution[it.index].digitToInt() != digit }
        val given =
            (0 until 81).map(SudokuPosition::fromIndex).first {
                viewModel
                    .ready()
                    .game
                    .cellAt(it)
                    .status == SudokuCellStatus.GIVEN
            }
        val givenDigit =
            viewModel
                .ready()
                .game
                .cellAt(given)
                .value

        // No selection: the digit becomes the active one.
        viewModel.inputDigit(digit)
        assertEquals(digit, viewModel.ready().activeDigit)
        assertNull(viewModel.ready().selectedCell)

        // Taps enter it cell after cell; a wrong cell is an ordinary mistake.
        right.forEach(viewModel::onCellTapped)
        viewModel.onCellTapped(wrong)
        right.forEach {
            assertEquals(
                SudokuCellStatus.CORRECT,
                viewModel
                    .ready()
                    .game
                    .cellAt(it)
                    .status,
            )
        }
        assertEquals(
            SudokuCellStatus.INCORRECT,
            viewModel
                .ready()
                .game
                .cellAt(wrong)
                .status,
        )
        assertEquals(1, viewModel.ready().game.mistakesUsed)

        // A given cell makes its digit the active one; tapping that digit again drops it.
        viewModel.onCellTapped(given)
        assertEquals(givenDigit, viewModel.ready().activeDigit)
        viewModel.inputDigit(givenDigit)
        assertNull(viewModel.ready().activeDigit)

        // Cell-first is unchanged: a tap selects, a digit fills the selected cell.
        val next = emptyCells(viewModel).first()
        viewModel.onCellTapped(next)
        assertEquals(next, viewModel.ready().selectedCell)
        viewModel.inputDigit(puzzle.solution[next.index].digitToInt())
        assertEquals(
            SudokuCellStatus.CORRECT,
            viewModel
                .ready()
                .game
                .cellAt(next)
                .status,
        )
        assertNull(viewModel.ready().activeDigit)
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
