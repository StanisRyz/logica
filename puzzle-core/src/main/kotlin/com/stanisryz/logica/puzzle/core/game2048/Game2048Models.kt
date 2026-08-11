package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed

enum class Game2048Direction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

enum class Game2048Status {
    IN_PROGRESS,
    SOLVED,
    FAILED,
    ;

    val isTerminal: Boolean
        get() = this != IN_PROGRESS
}

@JvmInline
value class Game2048GeneratorVersion(
    val value: Int,
) {
    init {
        require(value > 0) { "2048 generator version must be positive." }
    }

    companion object {
        val V1 = Game2048GeneratorVersion(1)
    }
}

data class Game2048PuzzleId(
    val seed: PuzzleSeed,
    val difficulty: Difficulty,
    val generatorVersion: Game2048GeneratorVersion = Game2048GeneratorVersion.V1,
) {
    val targetTile: Int
        get() =
            when (difficulty) {
                Difficulty.EASY -> 256
                Difficulty.MEDIUM -> 512
                Difficulty.HARD -> 1024
                Difficulty.EXPERT -> 2048
            }
}

data class Game2048State(
    val puzzleId: Game2048PuzzleId,
    val board: List<Int>,
    val score: Long,
    val nextSpawnIndex: Long,
    val status: Game2048Status,
) {
    init {
        require(board.size == CELL_COUNT) { "2048 board must contain exactly $CELL_COUNT cells." }
        require(board.all(Game2048Rules::isValidCellValue)) { "2048 board contains an invalid tile value." }
        require(score >= 0L) { "2048 score must not be negative." }
        require(nextSpawnIndex >= 0L) { "2048 spawn index must not be negative." }
        require(status == Game2048Rules.status(board, puzzleId.targetTile)) {
            "2048 status does not match the board and target."
        }
    }

    val maximumTile: Int
        get() = board.maxOrNull() ?: 0

    fun cellAt(
        row: Int,
        column: Int,
    ): Int {
        require(row in 0 until BOARD_SIZE && column in 0 until BOARD_SIZE)
        return board[row * BOARD_SIZE + column]
    }

    companion object {
        const val BOARD_SIZE = 4
        const val CELL_COUNT = BOARD_SIZE * BOARD_SIZE
    }
}
