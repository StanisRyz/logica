package com.stanisryz.logica.puzzle.core.game2048

/** The destination chosen for one original tile by the canonical move calculation. */
data class Game2048TileMovement(
    val sourceIndex: Int,
    val destinationIndex: Int,
    val value: Int,
) {
    init {
        require(sourceIndex in 0 until Game2048State.CELL_COUNT)
        require(destinationIndex in 0 until Game2048State.CELL_COUNT)
        require(value != 0 && Game2048Rules.isValidCellValue(value))
    }
}

/** Two original tiles consumed by one canonical merge. */
data class Game2048MergeTrace(
    val firstSourceIndex: Int,
    val secondSourceIndex: Int,
    val destinationIndex: Int,
    val sourceValue: Int,
    val resultingValue: Int,
) {
    init {
        require(firstSourceIndex in 0 until Game2048State.CELL_COUNT)
        require(secondSourceIndex in 0 until Game2048State.CELL_COUNT)
        require(firstSourceIndex != secondSourceIndex)
        require(destinationIndex in 0 until Game2048State.CELL_COUNT)
        require(sourceValue != 0 && Game2048Rules.isValidCellValue(sourceValue))
        require(resultingValue == sourceValue * 2)
    }
}

/** The deterministic tile added after movement and merging. */
data class Game2048SpawnTrace(
    val destinationIndex: Int,
    val value: Int,
) {
    init {
        require(destinationIndex in 0 until Game2048State.CELL_COUNT)
        require(value == 2 || value == 4)
    }
}

/**
 * Transient presentation data for one valid move; it is never part of [Game2048State].
 * [movements] maps every original non-zero tile once, including tiles whose source and destination
 * are equal, so presentation never has to infer tile correspondence from board values.
 */
data class Game2048MoveTrace(
    val direction: Game2048Direction,
    val movements: List<Game2048TileMovement>,
    val merges: List<Game2048MergeTrace>,
    val spawnedTile: Game2048SpawnTrace?,
    val scoreGained: Long,
) {
    init {
        require(movements.map { it.sourceIndex }.distinct().size == movements.size) {
            "Each original 2048 tile must appear in the move trace exactly once."
        }
        require(scoreGained == merges.sumOf { it.resultingValue.toLong() }) {
            "2048 trace score must equal its merged tile values."
        }
    }
}

/** The authoritative engine state plus optional transient data; no-op moves have no trace. */
data class Game2048MoveTransition(
    val state: Game2048State,
    val trace: Game2048MoveTrace?,
)
