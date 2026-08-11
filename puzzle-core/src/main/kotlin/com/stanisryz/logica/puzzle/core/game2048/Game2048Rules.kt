package com.stanisryz.logica.puzzle.core.game2048

data class Game2048LineMerge(
    val values: List<Int>,
    val scoreGained: Long,
)

data class Game2048BoardMove(
    val board: List<Int>,
    val scoreGained: Long,
)

object Game2048Rules {
    fun isValidCellValue(value: Int): Boolean = value == 0 || (value >= 2 && value and (value - 1) == 0)

    /** Canonical toward-the-start merge used by every board direction. */
    fun mergeLine(line: List<Int>): Game2048LineMerge {
        require(line.size == Game2048State.BOARD_SIZE) { "2048 lines must contain four cells." }
        require(line.all(::isValidCellValue)) { "2048 line contains an invalid tile value." }
        val compact = line.filter { it != 0 }
        val merged = ArrayList<Int>(Game2048State.BOARD_SIZE)
        var score = 0L
        var index = 0
        while (index < compact.size) {
            val value = compact[index]
            if (index + 1 < compact.size && compact[index + 1] == value) {
                val combined = value * 2
                merged += combined
                score += combined.toLong()
                index += 2
            } else {
                merged += value
                index += 1
            }
        }
        while (merged.size < Game2048State.BOARD_SIZE) merged += 0
        return Game2048LineMerge(merged, score)
    }

    fun move(
        board: List<Int>,
        direction: Game2048Direction,
    ): Game2048BoardMove {
        require(board.size == Game2048State.CELL_COUNT) { "2048 board must contain exactly 16 cells." }
        require(board.all(::isValidCellValue)) { "2048 board contains an invalid tile value." }
        val moved = MutableList(Game2048State.CELL_COUNT) { 0 }
        var score = 0L
        repeat(Game2048State.BOARD_SIZE) { lineIndex ->
            val indices = directedIndices(lineIndex, direction)
            val result = mergeLine(indices.map(board::get))
            indices.forEachIndexed { resultIndex, boardIndex -> moved[boardIndex] = result.values[resultIndex] }
            score += result.scoreGained
        }
        return Game2048BoardMove(moved, score)
    }

    fun hasLegalMove(board: List<Int>): Boolean {
        require(board.size == Game2048State.CELL_COUNT)
        if (board.any { it == 0 }) return true
        repeat(Game2048State.BOARD_SIZE) { row ->
            repeat(Game2048State.BOARD_SIZE - 1) { column ->
                if (board[index(row, column)] == board[index(row, column + 1)]) return true
            }
        }
        repeat(Game2048State.BOARD_SIZE) { column ->
            repeat(Game2048State.BOARD_SIZE - 1) { row ->
                if (board[index(row, column)] == board[index(row + 1, column)]) return true
            }
        }
        return false
    }

    fun status(
        board: List<Int>,
        targetTile: Int,
    ): Game2048Status =
        when {
            board.any { it >= targetTile } -> Game2048Status.SOLVED
            hasLegalMove(board) -> Game2048Status.IN_PROGRESS
            else -> Game2048Status.FAILED
        }

    private fun directedIndices(
        lineIndex: Int,
        direction: Game2048Direction,
    ): List<Int> {
        val natural =
            when (direction) {
                Game2048Direction.LEFT, Game2048Direction.RIGHT ->
                    List(Game2048State.BOARD_SIZE) { column -> index(lineIndex, column) }
                Game2048Direction.UP, Game2048Direction.DOWN ->
                    List(Game2048State.BOARD_SIZE) { row -> index(row, lineIndex) }
            }
        return when (direction) {
            Game2048Direction.LEFT, Game2048Direction.UP -> natural
            Game2048Direction.RIGHT, Game2048Direction.DOWN -> natural.reversed()
        }
    }

    private fun index(
        row: Int,
        column: Int,
    ): Int = row * Game2048State.BOARD_SIZE + column
}
