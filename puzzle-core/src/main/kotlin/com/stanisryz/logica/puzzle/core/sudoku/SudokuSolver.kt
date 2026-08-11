package com.stanisryz.logica.puzzle.core.sudoku

enum class SudokuSolveStatus {
    INVALID_GIVENS,
    NO_SOLUTION,
    UNIQUE,
    MULTIPLE_SOLUTIONS,
}

data class SudokuSolveResult(
    val status: SudokuSolveStatus,
    val solution: String?,
)

/** Validation solver. Search stops as soon as a second solution is found. */
object SudokuSolver {
    fun solve(givens: String): SudokuSolveResult {
        if (givens.length != CELL_COUNT || givens.any { it !in '0'..'9' }) {
            return SudokuSolveResult(SudokuSolveStatus.INVALID_GIVENS, null)
        }

        val board = IntArray(CELL_COUNT)
        val rows = IntArray(UNIT_SIZE)
        val columns = IntArray(UNIT_SIZE)
        val blocks = IntArray(UNIT_SIZE)
        val empty = IntArray(givens.count { it == '0' })
        var emptyCount = 0

        givens.forEachIndexed { index, character ->
            val value = character.digitToInt()
            board[index] = value
            if (value == 0) {
                empty[emptyCount++] = index
            } else {
                val row = index / UNIT_SIZE
                val column = index % UNIT_SIZE
                val block = blockIndex(row, column)
                val bit = 1 shl value
                if (rows[row] and bit != 0 || columns[column] and bit != 0 || blocks[block] and bit != 0) {
                    return SudokuSolveResult(SudokuSolveStatus.INVALID_GIVENS, null)
                }
                rows[row] = rows[row] or bit
                columns[column] = columns[column] or bit
                blocks[block] = blocks[block] or bit
            }
        }

        var solutionCount = 0
        var firstSolution: String? = null

        fun search(depth: Int) {
            if (solutionCount >= MAX_SOLUTIONS) return
            if (depth == empty.size) {
                solutionCount += 1
                if (firstSolution == null) firstSolution = board.joinToString(separator = "")
                return
            }

            var bestOffset = -1
            var bestCandidates = 0
            var bestCount = UNIT_SIZE + 1
            for (offset in depth until empty.size) {
                val index = empty[offset]
                val row = index / UNIT_SIZE
                val column = index % UNIT_SIZE
                val block = blockIndex(row, column)
                val candidates = ALL_DIGITS_MASK and (rows[row] or columns[column] or blocks[block]).inv()
                val count = candidates.countOneBits()
                if (count < bestCount) {
                    bestOffset = offset
                    bestCandidates = candidates
                    bestCount = count
                    if (count <= 1) break
                }
            }
            if (bestCount == 0) return

            val swap = empty[depth]
            empty[depth] = empty[bestOffset]
            empty[bestOffset] = swap
            val index = empty[depth]
            val row = index / UNIT_SIZE
            val column = index % UNIT_SIZE
            val block = blockIndex(row, column)
            var candidates = bestCandidates
            while (candidates != 0 && solutionCount < MAX_SOLUTIONS) {
                val bit = candidates and -candidates
                candidates -= bit
                board[index] = bit.countTrailingZeroBits()
                rows[row] = rows[row] or bit
                columns[column] = columns[column] or bit
                blocks[block] = blocks[block] or bit
                search(depth + 1)
                rows[row] = rows[row] xor bit
                columns[column] = columns[column] xor bit
                blocks[block] = blocks[block] xor bit
                board[index] = 0
            }
            empty[bestOffset] = empty[depth]
            empty[depth] = swap
        }

        search(0)
        return when (solutionCount) {
            0 -> SudokuSolveResult(SudokuSolveStatus.NO_SOLUTION, null)
            1 -> SudokuSolveResult(SudokuSolveStatus.UNIQUE, firstSolution)
            else -> SudokuSolveResult(SudokuSolveStatus.MULTIPLE_SOLUTIONS, firstSolution)
        }
    }

    private fun blockIndex(
        row: Int,
        column: Int,
    ): Int = (row / BLOCK_SIZE) * BLOCK_SIZE + column / BLOCK_SIZE

    private const val UNIT_SIZE = 9
    private const val BLOCK_SIZE = 3
    private const val CELL_COUNT = 81
    private const val ALL_DIGITS_MASK = 0x3FE
    private const val MAX_SOLUTIONS = 2
}
