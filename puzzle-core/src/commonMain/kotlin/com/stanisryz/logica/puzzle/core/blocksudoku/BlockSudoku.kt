package com.stanisryz.logica.puzzle.core.blocksudoku

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/** One cell of the 9x9 board or of a piece, row-major from the top left. */
data class BlockCell(
    val row: Int,
    val column: Int,
)

/** A piece as its filled cells, normalized so its top-left bounding corner is (0, 0). */
data class BlockPiece(
    val cells: List<BlockCell>,
) {
    val height: Int = cells.maxOf { it.row } + 1
    val width: Int = cells.maxOf { it.column } + 1
    val size: Int get() = cells.size
}

enum class BlockSudokuStatus {
    IN_PROGRESS,

    /** The level's target score is reached; the level is cleared. */
    SOLVED,

    /** No piece left in the tray fits anywhere before the target was reached. */
    FAILED,
    ;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}

/**
 * One Block Sudoku position. [board] is the 9x9 grid row-major, [tray] the three dealt pieces
 * (null once placed), [deal] how many trays have been dealt. [lastCleared] is the transient set of
 * cells the last placement cleared, for presentation only.
 */
data class BlockSudokuState(
    val board: List<Boolean>,
    val tray: List<BlockPiece?>,
    val deal: Int,
    val score: Int,
    val targetScore: Int,
    val status: BlockSudokuStatus,
    val placements: Int = 0,
    val lastCleared: Set<BlockCell> = emptySet(),
    val lastGain: Int = 0,
) {
    fun isFilled(
        row: Int,
        column: Int,
    ): Boolean = board[row * BlockSudokuRules.SIZE + column]

    /**
     * Whether leaving this unfinished attempt throws away something the player did — a placed piece —
     * so leaving costs a life. Both hosts ask only this.
     */
    val hasMeaningfulProgress: Boolean get() = !status.isTerminal && placements > 0
}

/**
 * The frozen Block Sudoku rules (version 1): a 9x9 board, three pieces per deal drawn from a
 * difficulty's weighted shapes, full rows, columns, and 3x3 boxes clearing together. A placement
 * scores its cells, and a clear adds twice the cleared cells times the number of lines and boxes
 * cleared at once. The level is cleared at its target score; it fails when nothing in the tray fits.
 */
object BlockSudokuRules {
    const val SIZE = 9
    const val BOX = 3
    const val TRAY = 3
    val VERSION = GeneratorVersion(1)

    fun targetScore(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 300
            Difficulty.MEDIUM -> 600
            Difficulty.HARD -> 900
            Difficulty.EXPERT -> 1200
        }

    /** The shapes a difficulty deals, each with its weight; every rotation is equally likely. */
    fun weights(difficulty: Difficulty): List<Pair<List<BlockPiece>, Int>> =
        when (difficulty) {
            Difficulty.EASY ->
                listOf(DOT to 3, DOMINO to 6, I3 to 6, L3 to 6, SQUARE to 4, I4 to 3, L4 to 3, T4 to 3)
            Difficulty.MEDIUM ->
                listOf(
                    DOT to 2,
                    DOMINO to 4,
                    I3 to 5,
                    L3 to 5,
                    SQUARE to 5,
                    I4 to 4,
                    L4 to 5,
                    T4 to 4,
                    S4 to 3,
                    I5 to 2,
                    BIG_L to 2,
                )
            Difficulty.HARD ->
                listOf(
                    DOT to 2,
                    DOMINO to 4,
                    I3 to 5,
                    L3 to 5,
                    SQUARE to 5,
                    I4 to 4,
                    L4 to 5,
                    T4 to 4,
                    S4 to 3,
                    I5 to 2,
                    BIG_L to 2,
                    U5 to 1,
                )
            Difficulty.EXPERT ->
                listOf(
                    DOT to 2,
                    DOMINO to 4,
                    I3 to 5,
                    L3 to 5,
                    SQUARE to 5,
                    I4 to 4,
                    L4 to 5,
                    T4 to 4,
                    S4 to 4,
                    I5 to 3,
                    BIG_L to 3,
                    U5 to 2,
                    PLUS to 1,
                )
        }

    /** Whether [piece] with its top-left at ([row], [column]) lies on the board over empty cells only. */
    fun canPlace(
        board: List<Boolean>,
        piece: BlockPiece,
        row: Int,
        column: Int,
    ): Boolean =
        piece.cells.all { cell ->
            val r = row + cell.row
            val c = column + cell.column
            r in 0 until SIZE && c in 0 until SIZE && !board[r * SIZE + c]
        }

    /** Whether [piece] fits anywhere on [board]. */
    fun fitsAnywhere(
        board: List<Boolean>,
        piece: BlockPiece,
    ): Boolean = (0..SIZE - piece.height).any { row -> (0..SIZE - piece.width).any { column -> canPlace(board, piece, row, column) } }

    /** The cells a placement would clear, for the host's preview; empty when it clears nothing or does not fit. */
    fun clearedBy(
        board: List<Boolean>,
        piece: BlockPiece,
        row: Int,
        column: Int,
    ): Set<BlockCell> {
        if (!canPlace(board, piece, row, column)) return emptySet()
        val placed = board.toMutableList()
        piece.cells.forEach { placed[(row + it.row) * SIZE + column + it.column] = true }
        return fullUnits(placed).first
    }

    /** Every cell of a full row, column, or 3x3 box, and how many such units there are. */
    fun fullUnits(board: List<Boolean>): Pair<Set<BlockCell>, Int> {
        val cells = LinkedHashSet<BlockCell>()
        var units = 0
        for (row in 0 until SIZE) {
            if ((0 until SIZE).all { board[row * SIZE + it] }) {
                units++
                (0 until SIZE).forEach { cells += BlockCell(row, it) }
            }
        }
        for (column in 0 until SIZE) {
            if ((0 until SIZE).all { board[it * SIZE + column] }) {
                units++
                (0 until SIZE).forEach { cells += BlockCell(it, column) }
            }
        }
        for (boxRow in 0 until SIZE / BOX) {
            for (boxColumn in 0 until SIZE / BOX) {
                val boxCells = (0 until BOX * BOX).map { BlockCell(boxRow * BOX + it / BOX, boxColumn * BOX + it % BOX) }
                if (boxCells.all { board[it.row * SIZE + it.column] }) {
                    units++
                    cells += boxCells
                }
            }
        }
        return cells to units
    }

    private fun shape(vararg rows: String): List<BlockPiece> {
        val base =
            rows.flatMapIndexed { row, line ->
                line.mapIndexedNotNull { column, c ->
                    if (c ==
                        '#'
                    ) {
                        BlockCell(row, column)
                    } else {
                        null
                    }
                }
            }
        val rotations = LinkedHashSet<List<BlockCell>>()
        var current = base
        repeat(4) {
            current = normalized(current.map { BlockCell(it.column, -it.row) })
            rotations += current
        }
        return rotations.map(::BlockPiece)
    }

    private fun normalized(cells: List<BlockCell>): List<BlockCell> {
        val top = cells.minOf { it.row }
        val left = cells.minOf { it.column }
        return cells.map { BlockCell(it.row - top, it.column - left) }.sortedWith(compareBy({ it.row }, { it.column }))
    }

    private val DOT = shape("#")
    private val DOMINO = shape("##")
    private val I3 = shape("###")
    private val L3 = shape("##", "#.")
    private val SQUARE = shape("##", "##")
    private val I4 = shape("####")
    private val L4 = shape("#..", "###") + shape("..#", "###")
    private val T4 = shape("###", ".#.")
    private val S4 = shape(".##", "##.") + shape("##.", ".##")
    private val I5 = shape("#####")
    private val BIG_L = shape("#..", "#..", "###")
    private val U5 = shape("#.#", "###")
    private val PLUS = shape(".#.", "###", ".#.")
}

/**
 * The Block Sudoku game for one level: [seed] fixes every deal (deal k draws from its own derived
 * project random stream, so the sequence never depends on where the pieces were placed), and
 * [difficulty] fixes the shapes and the target score.
 */
class BlockSudokuEngine(
    private val seed: PuzzleSeed,
    private val difficulty: Difficulty,
) {
    private val weights = BlockSudokuRules.weights(difficulty)
    private val totalWeight = weights.sumOf { it.second }

    fun start(): BlockSudokuState =
        BlockSudokuState(
            board = List(BlockSudokuRules.SIZE * BlockSudokuRules.SIZE) { false },
            tray = dealt(0),
            deal = 1,
            score = 0,
            targetScore = BlockSudokuRules.targetScore(difficulty),
            status = BlockSudokuStatus.IN_PROGRESS,
        )

    fun canPlace(
        state: BlockSudokuState,
        piece: BlockPiece,
        row: Int,
        column: Int,
    ): Boolean = BlockSudokuRules.canPlace(state.board, piece, row, column)

    /** Whether [piece] fits anywhere on the board. */
    fun fitsAnywhere(
        state: BlockSudokuState,
        piece: BlockPiece,
    ): Boolean = BlockSudokuRules.fitsAnywhere(state.board, piece)

    /** Places tray piece [trayIndex] with its top-left at ([row], [column]); an illegal move changes nothing. */
    fun place(
        state: BlockSudokuState,
        trayIndex: Int,
        row: Int,
        column: Int,
    ): BlockSudokuState {
        if (state.status.isTerminal) return state
        val piece = state.tray.getOrNull(trayIndex) ?: return state
        if (!canPlace(state, piece, row, column)) return state
        val board = state.board.toMutableList()
        piece.cells.forEach { board[(row + it.row) * BlockSudokuRules.SIZE + column + it.column] = true }
        val (cleared, units) = BlockSudokuRules.fullUnits(board)
        cleared.forEach { board[it.row * BlockSudokuRules.SIZE + it.column] = false }
        val gain = piece.size + cleared.size * 2 * units
        var tray =
            state.tray
                .toMutableList<BlockPiece?>()
                .also { it[trayIndex] = null }
                .toList()
        var deal = state.deal
        if (tray.all { it == null }) {
            tray = dealt(deal)
            deal += 1
        }
        val score = state.score + gain
        val next =
            state.copy(
                board = board,
                tray = tray,
                deal = deal,
                score = score,
                placements = state.placements + 1,
                lastCleared = cleared,
                lastGain = gain,
            )
        val status =
            when {
                score >= state.targetScore -> BlockSudokuStatus.SOLVED
                tray.none { it != null && fitsAnywhere(next, it) } -> BlockSudokuStatus.FAILED
                else -> BlockSudokuStatus.IN_PROGRESS
            }
        return next.copy(status = status)
    }

    private fun dealt(deal: Int): List<BlockPiece?> {
        val random = PuzzleRandomV1(PuzzleSeed(seed.value xor ((deal + 1).toLong() * DEAL_STREAM_STEP)))
        return List(BlockSudokuRules.TRAY) {
            var pick = random.nextInt(totalWeight)
            val shapes = weights.first { (_, weight) -> (pick < weight).also { if (!it) pick -= weight } }.first
            shapes[random.nextInt(shapes.size)]
        }
    }

    private companion object {
        /** The golden-ratio step that separates each deal's random stream. */
        const val DEAL_STREAM_STEP = -7046029254386353131L
    }
}
