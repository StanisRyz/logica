package com.stanisryz.logica.puzzle.core.game2048

data class Game2048LineMerge(
    val values: List<Int>,
    val scoreGained: Long,
)

data class Game2048BoardMove(
    val board: List<Int>,
    val scoreGained: Long,
)

data class Game2048TracedBoardMove(
    val board: List<Int>,
    val scoreGained: Long,
    val trace: Game2048MoveTrace?,
)

object Game2048Rules {
    fun isValidCellValue(value: Int): Boolean = value == 0 || (value >= 2 && value and (value - 1) == 0)

    /** Canonical toward-the-start merge used by every board direction. */
    fun mergeLine(line: List<Int>): Game2048LineMerge {
        val traced = mergeLineWithTrace(line)
        return Game2048LineMerge(traced.values, traced.scoreGained)
    }

    private fun mergeLineWithTrace(line: List<Int>): TracedLineMerge {
        require(line.size == Game2048State.BOARD_SIZE) { "2048 lines must contain four cells." }
        require(line.all(::isValidCellValue)) { "2048 line contains an invalid tile value." }
        val compact =
            line.mapIndexedNotNull { sourceIndex, value ->
                if (value == 0) null else IndexedTile(sourceIndex, value)
            }
        val merged = ArrayList<Int>(Game2048State.BOARD_SIZE)
        val movements = ArrayList<LineMovement>(compact.size)
        val merges = ArrayList<LineMerge>()
        var score = 0L
        var sourceOffset = 0
        var destinationOffset = 0
        while (sourceOffset < compact.size) {
            val first = compact[sourceOffset]
            val second = compact.getOrNull(sourceOffset + 1)?.takeIf { it.value == first.value }
            if (second != null) {
                val combined = first.value * 2
                merged += combined
                movements += LineMovement(first.sourceIndex, destinationOffset, first.value)
                movements += LineMovement(second.sourceIndex, destinationOffset, second.value)
                merges += LineMerge(first.sourceIndex, second.sourceIndex, destinationOffset, first.value, combined)
                score += combined.toLong()
                sourceOffset += 2
            } else {
                merged += first.value
                movements += LineMovement(first.sourceIndex, destinationOffset, first.value)
                sourceOffset += 1
            }
            destinationOffset += 1
        }
        while (merged.size < Game2048State.BOARD_SIZE) merged += 0
        return TracedLineMerge(merged, score, movements, merges)
    }

    fun move(
        board: List<Int>,
        direction: Game2048Direction,
    ): Game2048BoardMove {
        val traced = moveWithTrace(board, direction)
        return Game2048BoardMove(traced.board, traced.scoreGained)
    }

    fun moveWithTrace(
        board: List<Int>,
        direction: Game2048Direction,
    ): Game2048TracedBoardMove {
        require(board.size == Game2048State.CELL_COUNT) { "2048 board must contain exactly 16 cells." }
        require(board.all(::isValidCellValue)) { "2048 board contains an invalid tile value." }
        val moved = MutableList(Game2048State.CELL_COUNT) { 0 }
        val movements = mutableListOf<Game2048TileMovement>()
        val merges = mutableListOf<Game2048MergeTrace>()
        var score = 0L
        repeat(Game2048State.BOARD_SIZE) { lineIndex ->
            val indices = directedIndices(lineIndex, direction)
            val result = mergeLineWithTrace(indices.map(board::get))
            indices.forEachIndexed { resultIndex, boardIndex -> moved[boardIndex] = result.values[resultIndex] }
            movements +=
                result.movements.map { movement ->
                    Game2048TileMovement(
                        sourceIndex = indices[movement.sourceIndex],
                        destinationIndex = indices[movement.destinationIndex],
                        value = movement.value,
                    )
                }
            merges +=
                result.merges.map { merge ->
                    Game2048MergeTrace(
                        firstSourceIndex = indices[merge.firstSourceIndex],
                        secondSourceIndex = indices[merge.secondSourceIndex],
                        destinationIndex = indices[merge.destinationIndex],
                        sourceValue = merge.sourceValue,
                        resultingValue = merge.resultingValue,
                    )
                }
            score += result.scoreGained
        }
        val trace =
            if (moved == board) {
                null
            } else {
                Game2048MoveTrace(
                    direction = direction,
                    movements = movements,
                    merges = merges,
                    spawnedTile = null,
                    scoreGained = score,
                )
            }
        return Game2048TracedBoardMove(moved, score, trace)
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

    /** V1 terminal evaluation: the target tile wins immediately, a dead board loses. */
    fun status(
        board: List<Int>,
        targetTile: Int,
    ): Game2048Status =
        when {
            board.any { it >= targetTile } -> Game2048Status.SOLVED
            hasLegalMove(board) -> Game2048Status.IN_PROGRESS
            else -> Game2048Status.FAILED
        }

    /**
     * V2 terminal evaluation: play continues while any legal move remains, however far past the
     * target the score already is, and the final score alone decides victory or defeat.
     */
    fun scoreStatus(
        board: List<Int>,
        score: Long,
        targetScore: Long,
    ): Game2048Status =
        when {
            hasLegalMove(board) -> Game2048Status.IN_PROGRESS
            score >= targetScore -> Game2048Status.SOLVED
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

    private data class IndexedTile(
        val sourceIndex: Int,
        val value: Int,
    )

    private data class LineMovement(
        val sourceIndex: Int,
        val destinationIndex: Int,
        val value: Int,
    )

    private data class LineMerge(
        val firstSourceIndex: Int,
        val secondSourceIndex: Int,
        val destinationIndex: Int,
        val sourceValue: Int,
        val resultingValue: Int,
    )

    private data class TracedLineMerge(
        val values: List<Int>,
        val scoreGained: Long,
        val movements: List<LineMovement>,
        val merges: List<LineMerge>,
    )
}
