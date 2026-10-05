package com.stanisryz.logica.ui.blocksudoku

import com.stanisryz.logica.puzzle.core.blocksudoku.BlockCell
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockPiece
import com.stanisryz.logica.puzzle.core.blocksudoku.BlockSudokuRules
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where a dragged piece lands, from the exact position of its top-left corner measured in board
 * cells. The rounded cell wins when the piece fits there; otherwise the piece snaps to the nearest
 * anchor where it fits, at most half a cell further than rounding would move it on each axis. Null
 * when nothing fits that close. The landing preview and the drop both read this one answer.
 */
fun blockSudokuDropAnchor(
    board: List<Boolean>,
    piece: BlockPiece,
    exactRow: Float,
    exactColumn: Float,
): BlockCell? {
    val row = exactRow.roundToInt()
    val column = exactColumn.roundToInt()
    if (BlockSudokuRules.canPlace(board, piece, row, column)) return BlockCell(row, column)
    var best: BlockCell? = null
    var bestDistance = Float.MAX_VALUE
    for (candidateRow in row - 1..row + 1) {
        for (candidateColumn in column - 1..column + 1) {
            val rowOffset = candidateRow - exactRow
            val columnOffset = candidateColumn - exactColumn
            if (abs(rowOffset) > SNAP_REACH_CELLS || abs(columnOffset) > SNAP_REACH_CELLS) continue
            if (!BlockSudokuRules.canPlace(board, piece, candidateRow, candidateColumn)) continue
            val distance = rowOffset * rowOffset + columnOffset * columnOffset
            if (distance < bestDistance) {
                best = BlockCell(candidateRow, candidateColumn)
                bestDistance = distance
            }
        }
    }
    return best
}

/** Rounding moves a piece up to half a cell; snapping allows half a cell more. */
private const val SNAP_REACH_CELLS = 1f
