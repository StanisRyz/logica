package com.stanisryz.logica.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.floor

/** One cell of a square board grid. */
data class GridCell(
    val row: Int,
    val column: Int,
)

/** The host's side of a drag stroke over a board: the first cell, then each new cell once in path order. */
class BoardDragCallbacks<P>(
    val onStart: (P) -> Unit,
    val onCell: (P) -> Unit,
    val onEnd: () -> Unit,
)

/**
 * The cells a straight pointer move from ([fromX], [fromY]) to ([toX], [toY]) passes through, in
 * order, on a [gridSize] board of [cellSize] cells: a fast diagonal move skips none between the two
 * points. Cells outside the board are left out.
 */
fun boardCellsAlong(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    cellSize: Float,
    gridSize: Int,
): List<GridCell> {
    if (cellSize <= 0f || gridSize <= 0) return emptyList()
    val x0 = fromX / cellSize
    val y0 = fromY / cellSize
    val x1 = toX / cellSize
    val y1 = toY / cellSize
    var column = floor(x0).toInt()
    var row = floor(y0).toInt()
    val endColumn = floor(x1).toInt()
    val endRow = floor(y1).toInt()
    val dx = x1 - x0
    val dy = y1 - y0
    val stepColumn = if (dx > 0) 1 else -1
    val stepRow = if (dy > 0) 1 else -1
    // Grid traversal: advance across whichever cell edge the segment reaches first.
    var nextColumnEdge =
        if (dx > 0) {
            (column + 1 - x0) / dx
        } else if (dx < 0) {
            (x0 - column) / -dx
        } else {
            Float.MAX_VALUE
        }
    var nextRowEdge =
        if (dy > 0) {
            (row + 1 - y0) / dy
        } else if (dy < 0) {
            (y0 - row) / -dy
        } else {
            Float.MAX_VALUE
        }
    val columnDelta = if (dx != 0f) 1f / abs(dx) else Float.MAX_VALUE
    val rowDelta = if (dy != 0f) 1f / abs(dy) else Float.MAX_VALUE
    val cells = mutableListOf<GridCell>()

    fun add() {
        if (row in 0 until gridSize && column in 0 until gridSize) cells += GridCell(row, column)
    }
    add()
    var guard = abs(endColumn - column) + abs(endRow - row)
    while ((column != endColumn || row != endRow) && guard-- > 0) {
        if (nextColumnEdge < nextRowEdge) {
            column += stepColumn
            nextColumnEdge += columnDelta
        } else {
            row += stepRow
            nextRowEdge += rowDelta
        }
        add()
    }
    return cells
}

/**
 * Drag strokes over a [gridSize] board. A tap never starts one, so the cells' own clicks keep
 * working; once the pointer leaves its first cell the stroke reports that cell, then every new cell
 * it passes once, and consumes the gesture so no click or scroll follows. The board's size is read
 * on every gesture. A second finger, or a gesture something else (a scroll) already took, ends it.
 */
@Composable
fun Modifier.boardDragStrokes(
    gridSize: Int,
    callbacks: BoardDragCallbacks<GridCell>?,
): Modifier {
    val current by rememberUpdatedState(callbacks)
    if (callbacks == null) return this
    return pointerInput(gridSize) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val cellSize = size.width.toFloat() / gridSize

            fun cellAt(position: Offset): GridCell? =
                boardCellsAlong(position.x, position.y, position.x, position.y, cellSize, gridSize).firstOrNull()
            val start = cellAt(down.position) ?: return@awaitEachGesture
            val visited = mutableSetOf(start)
            var last = down.position
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.size > 1) break
                if (!dragging) {
                    if (!change.pressed || change.isConsumed) break
                    val cell = cellAt(change.position)
                    if (cell == null || cell == start) continue
                    dragging = true
                    current?.onStart?.invoke(start)
                }
                change.consume()
                if (!change.pressed) break
                boardCellsAlong(last.x, last.y, change.position.x, change.position.y, cellSize, gridSize)
                    .filter(visited::add)
                    .forEach { cell -> current?.onCell?.invoke(cell) }
                last = change.position
            }
            if (dragging) current?.onEnd?.invoke()
        }
    }
}
