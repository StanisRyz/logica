package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalanceSolver
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuEngine
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsSolver
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV1
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuSelectorV1
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden outputs of the shipped generators. Frozen level packs and Daily entries store only seeds,
 * so Android (JVM) and Web (JS/Wasm) must build the very same puzzle from a seed. These run on every
 * target against references recorded from the JVM: a mismatch on one target is a cross-platform
 * determinism bug, never a reason to re-record. Word is left out: its lexicon is installed by the Web
 * host at runtime and is not readable from `commonTest` on JS/Wasm.
 */
class GoldenDeterminismTest {
    @Test
    fun balanceV1GivensAndSolution() {
        val puzzle = BalanceGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)
        val solution = checkNotNull(BalanceSolver().solve(puzzle))
        val givens =
            grid(puzzle.size) { row, column ->
                when (puzzle.fixedClues[BalancePosition(row, column)]) {
                    BalanceCell.ZERO -> '0'
                    BalanceCell.ONE -> '1'
                    else -> '.'
                }
            }
        val solved = grid(puzzle.size) { row, column -> if (solution.cellAt(BalancePosition(row, column)) == BalanceCell.ONE) '1' else '0' }
        assertEquals(BALANCE_GIVENS, givens)
        assertEquals(BALANCE_SOLUTION, solved)
    }

    @Test
    fun crownsV1RegionsAndSolution() {
        val puzzle = CrownsGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)
        val solution = checkNotNull(CrownsSolver().solve(puzzle))
        val regions = grid(puzzle.size) { row, column -> 'a' + puzzle.regionAt(CrownsPosition(row, column)).value }
        val crowns = grid(puzzle.size) { row, column -> if (CrownsPosition(row, column) in solution.crowns) 'X' else '.' }
        assertEquals(CROWNS_REGIONS, regions)
        assertEquals(CROWNS_SOLUTION, crowns)
    }

    @Test
    fun sudokuV1SelectorPicksTheSameRecords() {
        val picks =
            listOf(1L, 2L, 12_345L, -9_876_543_210L).map { selector ->
                SudokuSelectorV1.index(SudokuDatasetVersion.V1, SudokuDifficulty.MEDIUM, selector, recordCount = 10_000)
            }
        assertEquals(SUDOKU_PICKS, picks)
    }

    @Test
    fun game2048V2SpawnsAfterAFixedMoveSequence() {
        val engine = Game2048Engine(Game2048PuzzleId(PuzzleSeed(42L), Difficulty.MEDIUM, Game2048GeneratorVersion.V2))
        var state = engine.start()
        val directions = listOf(Game2048Direction.LEFT, Game2048Direction.UP, Game2048Direction.RIGHT, Game2048Direction.DOWN)
        repeat(24) { move -> state = engine.move(state, directions[move % directions.size]) }
        assertEquals(GAME_2048_BOARD, state.board.joinToString(","))
        assertEquals(GAME_2048_SCORE, state.score)
        assertEquals(GAME_2048_SPAWNS, state.nextSpawnIndex)
    }

    @Test
    fun nonogramV1AndV2Pictures() {
        assertEquals(NONOGRAM_V1, picture(NonogramGeneratorV1().generate(PuzzleSeed(7L), Difficulty.MEDIUM)))
        assertEquals(NONOGRAM_V2, picture(NonogramGeneratorV2().generate(PuzzleSeed(20_366L), Difficulty.MEDIUM)))
    }

    @Test
    fun blockSudokuV1FirstThreeDeals() {
        val engine = BlockSudokuEngine(PuzzleSeed(7L), Difficulty.MEDIUM)
        var state = engine.start()
        val deals = mutableListOf(tray(state))
        // Each piece goes to the first place it fits, so the next deal follows deterministically too.
        while (deals.size < 3 && !state.status.isTerminal) {
            val dealBefore = state.deal
            state = placeFirstFit(engine, state)
            if (state.deal != dealBefore) deals += tray(state)
        }
        assertEquals(BLOCK_SUDOKU_DEALS, deals)
    }

    private fun grid(
        size: Int,
        cell: (Int, Int) -> Char,
    ): String = (0 until size).joinToString("/") { row -> (0 until size).map { column -> cell(row, column) }.joinToString("") }

    private fun picture(puzzle: NonogramPuzzle): String =
        grid(puzzle.size) { row, column -> if (puzzle.isFilled(row, column)) '#' else '.' }

    private fun tray(state: BlockSudokuState): String = state.tray.joinToString("|") { it?.let(::shape) ?: "-" }

    private fun shape(piece: BlockPiece): String = piece.cells.joinToString(" ") { "${it.row}${it.column}" }

    private fun placeFirstFit(
        engine: BlockSudokuEngine,
        state: BlockSudokuState,
    ): BlockSudokuState {
        state.tray.forEachIndexed { index, piece ->
            if (piece == null) return@forEachIndexed
            for (row in 0 until BlockSudokuRules.SIZE) {
                for (column in 0 until BlockSudokuRules.SIZE) {
                    if (BlockSudokuRules.canPlace(state.board, piece, row, column)) return engine.place(state, index, row, column)
                }
            }
        }
        error("No tray piece fits")
    }

    private companion object {
        const val BALANCE_GIVENS = "010011/01011./1.1.0./011001/1001.0/10.001"
        const val BALANCE_SOLUTION = "010011/010110/101100/011001/100110/101001"
        const val CROWNS_REGIONS = "acccbb/cccccb/ecccbb/ecccdb/eeccfb/eccfff"
        const val CROWNS_SOLUTION = "X...../.....X/..X.../....X./.X..../...X.."
        val SUDOKU_PICKS = listOf(4577, 1583, 7744, 28)
        const val GAME_2048_BOARD = "2,0,0,0,0,0,0,8,0,0,2,16,4,16,8,2"
        const val GAME_2048_SCORE = 120L
        const val GAME_2048_SPAWNS = 26L
        const val NONOGRAM_V1 = "##.##.##/##....##/.######./...##.../...##.../..####../.######./..####.."
        const val NONOGRAM_V2 =
            "........../##########/###....###/#.##..##.#/#..####..#/#...##...#/#........#/##########/........../.........."
        val BLOCK_SUDOKU_DEALS =
            listOf(
                "00 10 20 30 40|00 01 10|00 01 10 11",
                "00 01 02 11|00 10 11 12|00 01 02 03",
                "00 01 02 10 20|00|00 01 10 11",
            )
    }
}
