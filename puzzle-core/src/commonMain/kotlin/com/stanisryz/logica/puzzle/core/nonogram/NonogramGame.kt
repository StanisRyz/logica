package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.model.PuzzleMistakes

enum class NonogramGameStatus {
    IN_PROGRESS,
    SOLVED,
    FAILED,
    ;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}

/** What the player sees in one cell: still open, filled, or crossed out as empty. */
enum class NonogramCell {
    UNKNOWN,
    FILLED,
    CROSSED,
}

/** The tool a tap applies: fill the cell, or cross it out as empty. */
enum class NonogramTool {
    FILL,
    CROSS,
}

/**
 * One attempt. Every opened cell already shows its true value — a wrong tap costs a mistake and
 * opens the cell with the right value, marked in [mistakeCells] — so opened cells are final.
 */
data class NonogramGameState(
    val cells: List<NonogramCell>,
    val mistakeCells: Set<Int>,
    val status: NonogramGameStatus,
    val mistakesUsed: Int,
    val hintsUsed: Int,
) {
    init {
        require(mistakesUsed in 0..PuzzleMistakes.MAX_MISTAKES) { "Mistakes used must be within 0..${PuzzleMistakes.MAX_MISTAKES}." }
        require(hintsUsed >= 0) { "Hints used must not be negative." }
    }

    fun cellAt(
        size: Int,
        position: NonogramPosition,
    ): NonogramCell = cells[position.row * size + position.column]

    /** Filled cells found so far, the progress the header shows without revealing anything new. */
    val filledFound: Int get() = cells.count { it == NonogramCell.FILLED }

    /** Anything the player would lose by leaving: an opened cell, a mistake, or a hint. */
    fun hasMeaningfulProgress(initial: NonogramGameState): Boolean = cells != initial.cells || mistakesUsed > 0 || hintsUsed > 0
}

/**
 * The Nonogram rules. Opening a cell with the matching tool reveals it; a wrong tool costs one
 * mistake and reveals the true value instead, and the third mistake ends the attempt. A row or
 * column whose filled cells are all found crosses out its remaining cells by itself, and a line with
 * no runs at all starts crossed out. A hint opens the next cell line logic can prove.
 */
class NonogramGameEngine(
    private val puzzle: NonogramPuzzle,
) {
    private val size = puzzle.size

    fun start(): NonogramGameState {
        val cells = MutableList(size * size) { NonogramCell.UNKNOWN }
        for (line in 0 until size) {
            if (puzzle.rowClues[line].isEmpty()) for (column in 0 until size) cells[line * size + column] = NonogramCell.CROSSED
            if (puzzle.columnClues[line].isEmpty()) for (row in 0 until size) cells[row * size + line] = NonogramCell.CROSSED
        }
        return NonogramGameState(cells, emptySet(), NonogramGameStatus.IN_PROGRESS, mistakesUsed = 0, hintsUsed = 0)
    }

    /** Applies [tool] to one cell; opened cells and finished attempts ignore it. */
    fun mark(
        state: NonogramGameState,
        position: NonogramPosition,
        tool: NonogramTool,
    ): NonogramGameState {
        requireInside(position)
        if (state.status.isTerminal) return state
        val index = position.row * size + position.column
        if (state.cells[index] != NonogramCell.UNKNOWN) return state
        val truth = puzzle.solution[index]
        val correct = (tool == NonogramTool.FILL) == truth
        return open(
            state = state,
            index = index,
            mistake = !correct,
            hintsUsed = state.hintsUsed,
        )
    }

    /** Opens the next cell line logic proves from what is already open; counts as one hint. */
    fun revealHint(state: NonogramGameState): NonogramGameState {
        if (state.status.isTerminal) return state
        val index = hintCell(state) ?: return state
        return open(state = state, index = index, mistake = false, hintsUsed = state.hintsUsed + 1)
    }

    /** The cell a hint would open, or `null` when nothing is left to open. */
    fun hintCell(state: NonogramGameState): Int? {
        val knowledge = state.cells.map { it.toKnowledge() }
        for (row in 0 until size) {
            val line = List(size) { knowledge[row * size + it] }
            val solved = NonogramLineSolver.solveLine(puzzle.rowClues[row], line) ?: continue
            for (column in 0 until size) {
                if (line[column] == NonogramKnowledge.UNKNOWN &&
                    solved[column] != NonogramKnowledge.UNKNOWN
                ) {
                    return row * size + column
                }
            }
        }
        for (column in 0 until size) {
            val line = List(size) { knowledge[it * size + column] }
            val solved = NonogramLineSolver.solveLine(puzzle.columnClues[column], line) ?: continue
            for (row in 0 until size) {
                if (line[row] == NonogramKnowledge.UNKNOWN &&
                    solved[row] != NonogramKnowledge.UNKNOWN
                ) {
                    return row * size + column
                }
            }
        }
        return state.cells.indices.firstOrNull { state.cells[it] == NonogramCell.UNKNOWN && puzzle.solution[it] }
            ?: state.cells.indices.firstOrNull { state.cells[it] == NonogramCell.UNKNOWN }
    }

    private fun open(
        state: NonogramGameState,
        index: Int,
        mistake: Boolean,
        hintsUsed: Int,
    ): NonogramGameState {
        val cells = state.cells.toMutableList()
        cells[index] = if (puzzle.solution[index]) NonogramCell.FILLED else NonogramCell.CROSSED
        crossFinishedLines(cells, index / size, index % size)
        val mistakes = if (mistake) state.mistakesUsed + 1 else state.mistakesUsed
        val solved = puzzle.solution.indices.all { !puzzle.solution[it] || cells[it] == NonogramCell.FILLED }
        val status =
            when {
                solved -> NonogramGameStatus.SOLVED
                mistakes >= PuzzleMistakes.MAX_MISTAKES -> NonogramGameStatus.FAILED
                else -> NonogramGameStatus.IN_PROGRESS
            }
        if (status == NonogramGameStatus.SOLVED) {
            for (cell in cells.indices) if (cells[cell] == NonogramCell.UNKNOWN) cells[cell] = NonogramCell.CROSSED
        }
        return NonogramGameState(
            cells = cells,
            mistakeCells = if (mistake) state.mistakeCells + index else state.mistakeCells,
            status = status,
            mistakesUsed = mistakes,
            hintsUsed = hintsUsed,
        )
    }

    private fun crossFinishedLines(
        cells: MutableList<NonogramCell>,
        row: Int,
        column: Int,
    ) {
        val rowCells = (0 until size).map { row * size + it }
        val columnCells = (0 until size).map { it * size + column }
        listOf(rowCells, columnCells).forEach { line ->
            if (line.all { !puzzle.solution[it] || cells[it] == NonogramCell.FILLED }) {
                line.forEach { if (cells[it] == NonogramCell.UNKNOWN) cells[it] = NonogramCell.CROSSED }
            }
        }
    }

    private fun requireInside(position: NonogramPosition) {
        require(position.row in 0 until size && position.column in 0 until size) { "Position $position is outside the board." }
    }

    private fun NonogramCell.toKnowledge(): NonogramKnowledge =
        when (this) {
            NonogramCell.UNKNOWN -> NonogramKnowledge.UNKNOWN
            NonogramCell.FILLED -> NonogramKnowledge.FILLED
            NonogramCell.CROSSED -> NonogramKnowledge.EMPTY
        }
}
