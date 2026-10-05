package com.stanisryz.logica.ui

import com.stanisryz.logica.ui.components.GridCell
import com.stanisryz.logica.ui.components.boardCellsAlong
import org.junit.Assert.assertEquals
import org.junit.Test

/** The cells a pointer move passes, in order and once each, so a fast stroke skips nothing. */
class BoardDragPathTest {
    private val cell = 10f

    @Test
    fun aStraightMoveListsEveryCellInOrder() {
        assertEquals(
            listOf(GridCell(1, 0), GridCell(1, 1), GridCell(1, 2), GridCell(1, 3)),
            boardCellsAlong(5f, 15f, 35f, 15f, cell, gridSize = 6),
        )
        assertEquals(
            listOf(GridCell(3, 2), GridCell(2, 2), GridCell(1, 2)),
            boardCellsAlong(25f, 35f, 25f, 15f, cell, gridSize = 6),
        )
    }

    @Test
    fun aFastDiagonalMoveSkipsNoCellBetweenItsPoints() {
        val cells = boardCellsAlong(2f, 1f, 38f, 29f, cell, gridSize = 6)

        assertEquals(GridCell(0, 0), cells.first())
        assertEquals(GridCell(2, 3), cells.last())
        assertEquals(cells.size, cells.toSet().size)
        // Each step moves to an edge neighbour: no cell between the two points is jumped over.
        cells.zipWithNext().forEach { (a, b) ->
            assertEquals(1, kotlin.math.abs(a.row - b.row) + kotlin.math.abs(a.column - b.column))
        }
    }

    @Test
    fun cellsOffTheBoardAreLeftOut() {
        assertEquals(
            listOf(GridCell(0, 4), GridCell(0, 5)),
            boardCellsAlong(45f, 5f, 75f, 5f, cell, gridSize = 6),
        )
        assertEquals(emptyList<GridCell>(), boardCellsAlong(-5f, -5f, -1f, -1f, cell, gridSize = 6))
    }
}
