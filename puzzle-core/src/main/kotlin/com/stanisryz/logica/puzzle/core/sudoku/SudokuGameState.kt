package com.stanisryz.logica.puzzle.core.sudoku

data class SudokuPosition(
    val row: Int,
    val column: Int,
) {
    init {
        require(row in 0 until BOARD_SIZE && column in 0 until BOARD_SIZE) {
            "Sudoku position must be inside the 9x9 board."
        }
    }

    val index: Int get() = row * BOARD_SIZE + column

    companion object {
        const val BOARD_SIZE = 9

        fun fromIndex(index: Int): SudokuPosition {
            require(index in 0 until BOARD_SIZE * BOARD_SIZE) { "Sudoku cell index is out of range." }
            return SudokuPosition(index / BOARD_SIZE, index % BOARD_SIZE)
        }
    }
}

@JvmInline
value class SudokuCandidateMask(
    val bits: Int,
) {
    init {
        require(bits and ALL_BITS.inv() == 0) { "Sudoku candidate mask contains unsupported bits." }
    }

    fun contains(digit: Int): Boolean = bits and digitBit(digit) != 0

    fun toggle(digit: Int): SudokuCandidateMask = SudokuCandidateMask(bits xor digitBit(digit))

    fun remove(digit: Int): SudokuCandidateMask = SudokuCandidateMask(bits and digitBit(digit).inv())

    val digits: List<Int>
        get() = (1..9).filter(::contains)

    val isEmpty: Boolean get() = bits == 0

    companion object {
        val EMPTY = SudokuCandidateMask(0)
        internal const val ALL_BITS = 0x1FF

        internal fun digitBit(digit: Int): Int {
            require(digit in 1..9) { "Sudoku digit must be from 1 through 9." }
            return 1 shl (digit - 1)
        }
    }
}

enum class SudokuCellStatus {
    EMPTY,
    GIVEN,
    CORRECT,
    INCORRECT,
}

data class SudokuCellState(
    val value: Int,
    val status: SudokuCellStatus,
    val candidates: SudokuCandidateMask = SudokuCandidateMask.EMPTY,
) {
    init {
        require(value in 0..9) { "Sudoku cell value must be from 0 through 9." }
        require((status == SudokuCellStatus.EMPTY) == (value == 0)) {
            "Only an empty Sudoku cell may have value 0."
        }
        require(status == SudokuCellStatus.EMPTY || candidates.isEmpty) {
            "Only an empty Sudoku cell may contain candidates."
        }
    }
}

enum class SudokuGameStatus {
    IN_PROGRESS,
    SOLVED,
    FAILED,
    ;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}

class SudokuGameState internal constructor(
    val puzzleId: SudokuPuzzleId,
    cells: List<SudokuCellState>,
    val status: SudokuGameStatus,
    val mistakesUsed: Int,
    val hintsUsed: Int,
    val currentHint: SudokuHint?,
) {
    val cells: List<SudokuCellState> = cells.toList()

    init {
        require(this.cells.size == CELL_COUNT) { "Sudoku state must contain exactly 81 cells." }
        require(mistakesUsed in 0..MAX_MISTAKES) { "Sudoku mistakes must be within 0..$MAX_MISTAKES." }
        require(hintsUsed >= 0) { "Sudoku hints used must not be negative." }
    }

    fun cellAt(position: SudokuPosition): SudokuCellState = cells[position.index]

    fun isLocked(position: SudokuPosition): Boolean =
        when (cellAt(position).status) {
            SudokuCellStatus.GIVEN, SudokuCellStatus.CORRECT -> true
            SudokuCellStatus.EMPTY, SudokuCellStatus.INCORRECT -> false
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SudokuGameState &&
            puzzleId == other.puzzleId &&
            cells == other.cells &&
            status == other.status &&
            mistakesUsed == other.mistakesUsed &&
            hintsUsed == other.hintsUsed &&
            currentHint == other.currentHint

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + cells.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + mistakesUsed
        result = 31 * result + hintsUsed
        result = 31 * result + (currentHint?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val MAX_MISTAKES = 3
        const val CELL_COUNT = 81
    }
}
