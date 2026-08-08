package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleState

class BalanceState private constructor(
    val size: Int,
    cells: List<BalanceCell>,
) : PuzzleState {
    private val cells = cells.toList()

    init {
        val expectedCellCount = BalanceBoardConstraints.requireValidSize(size)
        require(this.cells.size == expectedCellCount) {
            "Expected $expectedCellCount cells, but found ${this.cells.size}."
        }
    }

    fun cellAt(position: BalancePosition): BalanceCell = cells[BalanceBoardConstraints.cellIndex(size, position)]

    fun withCell(
        position: BalancePosition,
        value: BalanceCell,
    ): BalanceState {
        val updatedCells = cells.toMutableList()
        updatedCells[BalanceBoardConstraints.cellIndex(size, position)] = value
        return BalanceState(size, updatedCells)
    }

    internal fun row(index: Int): List<BalanceCell> {
        BalanceBoardConstraints.requireLineIndex(size, index)
        val start = index * size
        return cells.subList(start, start + size)
    }

    internal fun column(index: Int): List<BalanceCell> {
        BalanceBoardConstraints.requireLineIndex(size, index)
        return List(size) { row -> cells[row * size + index] }
    }

    internal fun isComplete(): Boolean = BalanceCell.EMPTY !in cells

    override fun equals(other: Any?): Boolean = this === other || other is BalanceState && size == other.size && cells == other.cells

    override fun hashCode(): Int = 31 * size + cells.hashCode()

    override fun toString(): String = cells.chunked(size).joinToString(prefix = "[", postfix = "]")

    companion object {
        fun empty(size: Int): BalanceState {
            val cellCount = BalanceBoardConstraints.requireValidSize(size)
            return BalanceState(size, List(cellCount) { BalanceCell.EMPTY })
        }

        fun fromPuzzle(puzzle: BalancePuzzle): BalanceState {
            var state = empty(puzzle.size)
            puzzle.fixedClues.forEach { (position, value) ->
                state = state.withCell(position, value)
            }
            return state
        }

        fun fromRows(rows: List<List<BalanceCell>>): BalanceState {
            val size = rows.size
            BalanceBoardConstraints.requireValidSize(size)
            require(rows.all { it.size == size }) { "Board must be square." }
            return BalanceState(size, rows.flatten())
        }
    }
}
