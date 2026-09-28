package com.stanisryz.logica.puzzle.core.nonogram

/** What is known about one cell while solving: nothing yet, filled, or certainly empty. */
enum class NonogramKnowledge {
    UNKNOWN,
    FILLED,
    EMPTY,
}

/**
 * Deterministic logic for Nonograms. [solveLine] finds every cell of one line that all placements of
 * its runs agree on; [solve] repeats that over rows and columns until nothing changes. A board it
 * completes has exactly one answer and needs no guessing, which is what the generator accepts.
 */
object NonogramLineSolver {
    /**
     * The line with every cell its clue forces filled in, or `null` when the known cells contradict
     * the clue. Cells that stay undecided remain [NonogramKnowledge.UNKNOWN].
     */
    fun solveLine(
        clue: List<Int>,
        line: List<NonogramKnowledge>,
    ): List<NonogramKnowledge>? {
        val length = line.size
        val runs = clue.size
        // feasible[i][k]: runs k.. fit into cells i.. given what is known.
        val feasible = Array(length + 2) { BooleanArray(runs + 1) }
        for (i in length downTo 0) {
            for (k in runs downTo 0) {
                feasible[i][k] =
                    if (k == runs) {
                        (i until length).none { line[it] == NonogramKnowledge.FILLED }
                    } else {
                        (i < length && line[i] != NonogramKnowledge.FILLED && feasible[i + 1][k]) ||
                            canPlace(clue, line, i, k) &&
                            feasible[next(clue, i, k, length)][k + 1]
                    }
            }
        }
        if (!feasible[0][0]) return null

        val canFill = BooleanArray(length)
        val canEmpty = BooleanArray(length)
        val visited = Array(length + 2) { BooleanArray(runs + 1) }
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to 0)
        while (stack.isNotEmpty()) {
            val (i, k) = stack.removeLast()
            if (visited[i][k]) continue
            visited[i][k] = true
            if (k == runs) {
                for (cell in i until length) canEmpty[cell] = true
                continue
            }
            if (i < length && line[i] != NonogramKnowledge.FILLED && feasible[i + 1][k]) {
                canEmpty[i] = true
                stack.addLast(i + 1 to k)
            }
            val after = next(clue, i, k, length)
            if (canPlace(clue, line, i, k) && feasible[after][k + 1]) {
                for (cell in i until i + clue[k]) canFill[cell] = true
                if (i + clue[k] < length) canEmpty[i + clue[k]] = true
                stack.addLast(after to k + 1)
            }
        }
        return List(length) { cell ->
            when {
                canFill[cell] && !canEmpty[cell] -> NonogramKnowledge.FILLED
                canEmpty[cell] && !canFill[cell] -> NonogramKnowledge.EMPTY
                else -> NonogramKnowledge.UNKNOWN
            }
        }
    }

    /**
     * Solves the whole board by line logic alone, starting from [known] (row-major, all unknown by
     * default). Returns the completed board, or `null` when line logic stalls or finds a contradiction.
     */
    fun solve(
        size: Int,
        rowClues: List<List<Int>>,
        columnClues: List<List<Int>>,
        known: List<NonogramKnowledge> = List(size * size) { NonogramKnowledge.UNKNOWN },
    ): List<NonogramKnowledge>? {
        val board = known.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            for (row in 0 until size) {
                val line = List(size) { board[row * size + it] }
                if (NonogramKnowledge.UNKNOWN !in line) continue
                val solved = solveLine(rowClues[row], line) ?: return null
                for (column in 0 until size) {
                    if (line[column] != solved[column]) {
                        board[row * size + column] = solved[column]
                        changed = true
                    }
                }
            }
            for (column in 0 until size) {
                val line = List(size) { board[it * size + column] }
                if (NonogramKnowledge.UNKNOWN !in line) continue
                val solved = solveLine(columnClues[column], line) ?: return null
                for (row in 0 until size) {
                    if (line[row] != solved[row]) {
                        board[row * size + column] = solved[row]
                        changed = true
                    }
                }
            }
        }
        return board.takeIf { NonogramKnowledge.UNKNOWN !in it }
    }

    private fun canPlace(
        clue: List<Int>,
        line: List<NonogramKnowledge>,
        start: Int,
        run: Int,
    ): Boolean {
        val end = start + clue[run]
        if (end > line.size) return false
        for (cell in start until end) if (line[cell] == NonogramKnowledge.EMPTY) return false
        return end == line.size || line[end] != NonogramKnowledge.FILLED
    }

    private fun next(
        clue: List<Int>,
        start: Int,
        run: Int,
        length: Int,
    ): Int = minOf(start + clue[run] + 1, length).coerceAtLeast(minOf(start + clue[run], length))
}
