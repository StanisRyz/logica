package com.stanisryz.logica.puzzle.core.nonogram

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleId

/** One cell of a square Nonogram board, row-major from the top left. */
data class NonogramPosition(
    val row: Int,
    val column: Int,
)

/**
 * An immutable Nonogram: a square picture whose filled cells the player has to find from the
 * run-length clues of every row and column. [solution] is the picture in row-major order.
 */
class NonogramPuzzle(
    override val id: PuzzleId,
    val size: Int,
    solution: List<Boolean>,
) : PuzzleDefinition {
    val solution: List<Boolean> = solution.toList()

    init {
        require(size in MIN_SIZE..MAX_SIZE) { "A Nonogram board is ${MIN_SIZE}x$MIN_SIZE to ${MAX_SIZE}x$MAX_SIZE." }
        require(this.solution.size == size * size) { "The picture must cover the whole board." }
        require(this.solution.any { it }) { "A Nonogram picture has at least one filled cell." }
    }

    /** Run lengths of every row, top to bottom; an empty row has no runs. */
    val rowClues: List<List<Int>> = List(size) { row -> NonogramClues.of(List(size) { column -> isFilled(row, column) }) }

    /** Run lengths of every column, left to right. */
    val columnClues: List<List<Int>> = List(size) { column -> NonogramClues.of(List(size) { row -> isFilled(row, column) }) }

    val filledCount: Int = this.solution.count { it }

    fun isFilled(
        row: Int,
        column: Int,
    ): Boolean = solution[row * size + column]

    fun isFilled(position: NonogramPosition): Boolean = isFilled(position.row, position.column)

    override fun equals(other: Any?): Boolean =
        this === other || other is NonogramPuzzle && id == other.id && size == other.size && solution == other.solution

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + size) + solution.hashCode()

    companion object {
        const val MIN_SIZE = 3
        const val MAX_SIZE = 20
    }
}

object NonogramClues {
    /** The lengths of the consecutive filled runs of one line, in order. */
    fun of(line: List<Boolean>): List<Int> {
        val runs = ArrayList<Int>()
        var current = 0
        line.forEach { filled ->
            if (filled) {
                current++
            } else if (current > 0) {
                runs += current
                current = 0
            }
        }
        if (current > 0) runs += current
        return runs
    }
}
