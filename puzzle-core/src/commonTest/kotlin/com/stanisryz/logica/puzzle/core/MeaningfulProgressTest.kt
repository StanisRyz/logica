package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameEngine
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPosition
import com.stanisryz.logica.puzzle.core.nonogram.NonogramTool
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameEngine
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.puzzle.core.sudoku.hasMeaningfulProgress
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.hasMeaningfulProgress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The one rule both hosts use for "leaving costs a life": a fresh attempt is free, a first real action is not. */
class MeaningfulProgressTest {
    @Test
    fun balance() {
        val puzzle = BalanceGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)
        val engine = BalanceGameEngine(puzzle)
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress)
        val empty = allPositions(puzzle.size).map { (row, column) -> BalancePosition(row, column) }.first { it !in puzzle.fixedClues }
        assertTrue(engine.placeValue(start, empty, BalanceCell.ZERO).hasMeaningfulProgress)
    }

    @Test
    fun crowns() {
        val puzzle = CrownsGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)
        val engine = CrownsGameEngine(puzzle)
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress)
        assertTrue(engine.placeValue(start, CrownsPosition(0, 0), CrownsPlayerCell.MARKED).hasMeaningfulProgress)
    }

    @Test
    fun sudoku() {
        val engine = SudokuGameEngine(SUDOKU)
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress)
        // Row 0, column 0 is empty; its answer is 1.
        assertTrue(engine.placeValue(start, SudokuPosition(0, 0), 1).hasMeaningfulProgress)
    }

    @Test
    fun word() {
        val engine =
            WordGameEngine(
                WordPuzzle(PuzzleId(PuzzleType.WORD, Difficulty.MEDIUM, PuzzleSeed(1L), GeneratorVersion(2)), "слово"),
                NO_GUESSES,
            )
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress)
        assertTrue(engine.setLetter(start, 0, 'с').hasMeaningfulProgress)
    }

    @Test
    fun game2048OneValidSwipeIsProgress() {
        val engine = Game2048Engine(Game2048PuzzleId(PuzzleSeed(42L), Difficulty.MEDIUM, Game2048GeneratorVersion.V2))
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress(levelCleared = false, completionSaved = false))
        val moved = Game2048Direction.entries.map { engine.move(start, it) }.first { it != start }
        assertTrue(moved.hasMeaningfulProgress(levelCleared = false, completionSaved = false))
        // A cleared Catalog level stays guarded only until its completion is durably saved.
        assertTrue(moved.hasMeaningfulProgress(levelCleared = true, completionSaved = false))
        assertFalse(moved.hasMeaningfulProgress(levelCleared = true, completionSaved = true))
    }

    @Test
    fun nonogram() {
        val puzzle = NonogramGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)
        val engine = NonogramGameEngine(puzzle)
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress(start))
        val filled = allPositions(puzzle.size).first { (row, column) -> puzzle.isFilled(row, column) }
        assertTrue(engine.mark(start, NonogramPosition(filled.first, filled.second), NonogramTool.FILL).hasMeaningfulProgress(start))
    }

    @Test
    fun blockSudoku() {
        val engine = BlockSudokuEngine(PuzzleSeed(7L), Difficulty.MEDIUM)
        val start = engine.start()
        assertFalse(start.hasMeaningfulProgress)
        assertTrue(engine.place(start, trayIndex = 0, row = 0, column = 0).hasMeaningfulProgress)
    }

    private fun allPositions(size: Int): List<Pair<Int, Int>> = (0 until size).flatMap { row -> (0 until size).map { row to it } }

    private companion object {
        val SUDOKU =
            SudokuPuzzle(
                id =
                    SudokuPuzzleId(
                        SudokuDatasetVersion.V1,
                        SudokuDifficulty.EASY,
                        "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d",
                    ),
                givens = "050703060007000800000816000000030000005000100730040086906000204840572093000409000",
                solution = "158723469367954821294816375619238547485697132732145986976381254841572693523469718",
                upstreamRatingTenths = 12,
            )

        val NO_GUESSES =
            object : WordAllowedGuesses {
                override val size = 0

                override fun contains(normalizedWord: String) = false

                override fun all(): List<String> = emptyList()
            }
    }
}
