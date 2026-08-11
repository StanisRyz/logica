package com.stanisryz.logica.puzzle.core.sudoku

enum class SudokuHintTechnique {
    NAKED_SINGLE,
    HIDDEN_SINGLE_ROW,
    HIDDEN_SINGLE_COLUMN,
    HIDDEN_SINGLE_BLOCK,
    FALLBACK_REVEAL,
}

data class SudokuHint(
    val technique: SudokuHintTechnique,
    val position: SudokuPosition,
    val value: Int,
    /** Zero-based row, column, or block for hidden singles; null for cell-only techniques. */
    val unitIndex: Int? = null,
) {
    init {
        require(value in 1..9) { "A Sudoku hint must confirm a digit from 1 through 9." }
    }
}

/** Deterministic Stage 34 deductions over givens and confirmed player values only. */
class SudokuHintEngine {
    fun nextHint(
        puzzle: SudokuPuzzle,
        state: SudokuGameState,
    ): SudokuHint? {
        require(state.puzzleId == puzzle.id) { "Sudoku game state belongs to another puzzle." }
        val unresolved = state.cells.indices.filter { state.cells[it].status.isUnresolved }
        if (unresolved.isEmpty()) return null
        val candidateMasks = IntArray(SudokuGameState.CELL_COUNT) { index -> candidateBits(state, index) }

        unresolved.firstOrNull { candidateMasks[it].countOneBits() == 1 }?.let { index ->
            val value = candidateMasks[index].countTrailingZeroBits() + 1
            if (puzzle.solution[index].digitToInt() == value) {
                return SudokuHint(SudokuHintTechnique.NAKED_SINGLE, SudokuPosition.fromIndex(index), value)
            }
        }

        hiddenSingle(unresolved, candidateMasks, puzzle, UnitType.ROW)?.let { return it }
        hiddenSingle(unresolved, candidateMasks, puzzle, UnitType.COLUMN)?.let { return it }
        hiddenSingle(unresolved, candidateMasks, puzzle, UnitType.BLOCK)?.let { return it }

        val fallbackIndex = unresolved.first()
        return SudokuHint(
            SudokuHintTechnique.FALLBACK_REVEAL,
            SudokuPosition.fromIndex(fallbackIndex),
            puzzle.solution[fallbackIndex].digitToInt(),
        )
    }

    private fun hiddenSingle(
        unresolved: List<Int>,
        candidateMasks: IntArray,
        puzzle: SudokuPuzzle,
        unitType: UnitType,
    ): SudokuHint? {
        for (unit in 0 until 9) {
            for (digit in 1..9) {
                val bit = SudokuCandidateMask.digitBit(digit)
                val positions =
                    unresolved.filter { index ->
                        unitType.contains(unit, index) && candidateMasks[index] and bit != 0
                    }
                if (positions.size == 1 && puzzle.solution[positions.single()].digitToInt() == digit) {
                    return SudokuHint(
                        technique = unitType.technique,
                        position = SudokuPosition.fromIndex(positions.single()),
                        value = digit,
                        unitIndex = unit,
                    )
                }
            }
        }
        return null
    }

    private fun candidateBits(
        state: SudokuGameState,
        index: Int,
    ): Int {
        if (!state.cells[index].status.isUnresolved) return 0
        val position = SudokuPosition.fromIndex(index)
        var used = 0
        state.cells.forEachIndexed { otherIndex, cell ->
            if (!cell.status.isConfirmed) return@forEachIndexed
            val other = SudokuPosition.fromIndex(otherIndex)
            if (position.isPeerOf(other)) used = used or SudokuCandidateMask.digitBit(cell.value)
        }
        return SudokuCandidateMask.ALL_BITS and used.inv()
    }

    private enum class UnitType(
        val technique: SudokuHintTechnique,
    ) {
        ROW(SudokuHintTechnique.HIDDEN_SINGLE_ROW),
        COLUMN(SudokuHintTechnique.HIDDEN_SINGLE_COLUMN),
        BLOCK(SudokuHintTechnique.HIDDEN_SINGLE_BLOCK),
        ;

        fun contains(
            unit: Int,
            index: Int,
        ): Boolean {
            val position = SudokuPosition.fromIndex(index)
            return when (this) {
                ROW -> position.row == unit
                COLUMN -> position.column == unit
                BLOCK -> position.block == unit
            }
        }
    }
}

internal val SudokuCellStatus.isConfirmed: Boolean
    get() = this == SudokuCellStatus.GIVEN || this == SudokuCellStatus.CORRECT

internal val SudokuCellStatus.isUnresolved: Boolean
    get() = this == SudokuCellStatus.EMPTY || this == SudokuCellStatus.INCORRECT

internal val SudokuPosition.block: Int
    get() = (row / 3) * 3 + column / 3

internal fun SudokuPosition.isPeerOf(other: SudokuPosition): Boolean =
    this != other && (row == other.row || column == other.column || block == other.block)
