package com.stanisryz.logica.ui.nonogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.stanisryz.logica.puzzle.core.nonogram.NonogramCell
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGameState
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPosition
import com.stanisryz.logica.puzzle.core.nonogram.NonogramPuzzle
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.nonogram_board_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max

/**
 * The Nonogram board: run-length clues above and to the left of a square grid. A tap opens one
 * cell; dragging opens every cell along the row or column the drag started in, and stops at the
 * first mistake. The whole board, clues included, fits the square the layout offers.
 */
@Composable
fun NonogramBoard(
    puzzle: NonogramPuzzle,
    game: NonogramGameState,
    onCell: (NonogramPosition) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val currentGame by rememberUpdatedState(game)
    val currentOnCell by rememberUpdatedState(onCell)
    val interactive = enabled && !game.status.isTerminal
    val size = puzzle.size
    val maxRowRuns = max(1, puzzle.rowClues.maxOf { it.size })
    val maxColumnRuns = max(1, puzzle.columnClues.maxOf { it.size })
    // The clue gutter is the same on both sides so the whole board stays square.
    val gutterCells = max(maxRowRuns * ROW_CLUE_WIDTH, maxColumnRuns * COLUMN_CLUE_HEIGHT).coerceAtLeast(MIN_GUTTER_CELLS)
    val description = stringResource(Res.string.nonogram_board_description, size, size, game.filledFound, puzzle.filledCount)

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .semantics { contentDescription = description },
        ) {
            Canvas(
                modifier =
                    Modifier
                        .size(side)
                        .then(
                            if (interactive) {
                                Modifier.pointerInput(puzzle) {
                                    val cellPx = this.size.width / (size + gutterCells)
                                    val gutterPx = cellPx * gutterCells

                                    fun cellAt(offset: Offset): NonogramPosition? {
                                        val column = ((offset.x - gutterPx) / cellPx).toInt()
                                        val row = ((offset.y - gutterPx) / cellPx).toInt()
                                        if (offset.x < gutterPx || offset.y < gutterPx) return null
                                        return if (row in 0 until size && column in 0 until size) NonogramPosition(row, column) else null
                                    }
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val start = cellAt(down.position) ?: return@awaitEachGesture
                                        val mistakesAtStart = currentGame.mistakesUsed
                                        currentOnCell(start)
                                        var last = start
                                        var alongRow: Boolean? = null
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            if (currentGame.mistakesUsed != mistakesAtStart || currentGame.status.isTerminal) continue
                                            val raw = cellAt(change.position) ?: continue
                                            if (raw == last) continue
                                            if (alongRow == null) alongRow = abs(raw.column - start.column) >= abs(raw.row - start.row)
                                            val target =
                                                if (alongRow ==
                                                    true
                                                ) {
                                                    NonogramPosition(start.row, raw.column)
                                                } else {
                                                    NonogramPosition(raw.row, start.column)
                                                }
                                            // Every cell between the last one and the target, so a fast drag skips nothing.
                                            val path =
                                                if (alongRow == true) {
                                                    progression(last.column, target.column).map { NonogramPosition(start.row, it) }
                                                } else {
                                                    progression(last.row, target.row).map { NonogramPosition(it, start.column) }
                                                }
                                            for (position in path) {
                                                if (currentGame.mistakesUsed != mistakesAtStart) break
                                                currentOnCell(position)
                                            }
                                            last = target
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                val cell = this.size.width / (size + gutterCells)
                val gutter = cell * gutterCells
                drawBoard(
                    puzzle = puzzle,
                    game = game,
                    cell = cell,
                    gutter = gutter,
                    measurer = measurer,
                    colors =
                        BoardColors(
                            surface = colors.surface,
                            clueBand = colors.surfaceVariant,
                            ink = colors.onSurface,
                            doneInk = colors.onSurfaceVariant.copy(alpha = DONE_CLUE_ALPHA),
                            grid = colors.outlineVariant,
                            majorGrid = colors.outline,
                            filled = colors.primary,
                            cross = colors.onSurfaceVariant,
                            error = colors.error,
                            errorContainer = colors.errorContainer,
                        ),
                )
            }
        }
    }
}

private class BoardColors(
    val surface: Color,
    val clueBand: Color,
    val ink: Color,
    val doneInk: Color,
    val grid: Color,
    val majorGrid: Color,
    val filled: Color,
    val cross: Color,
    val error: Color,
    val errorContainer: Color,
)

private fun DrawScope.drawBoard(
    puzzle: NonogramPuzzle,
    game: NonogramGameState,
    cell: Float,
    gutter: Float,
    measurer: TextMeasurer,
    colors: BoardColors,
) {
    val size = puzzle.size
    val boardSide = cell * size
    drawRect(colors.clueBand, topLeft = Offset(gutter, 0f), size = Size(boardSide, gutter))
    drawRect(colors.clueBand, topLeft = Offset(0f, gutter), size = Size(gutter, boardSide))
    drawRect(colors.surface, topLeft = Offset(gutter, gutter), size = Size(boardSide, boardSide))

    // A line whose filled cells are all found greys its clue out.
    fun lineDone(indices: List<Int>): Boolean = indices.all { !puzzle.solution[it] || game.cells[it] == NonogramCell.FILLED }
    val fontSize =
        (cell * CLUE_FONT_RATIO)
            .toSp()
            .value
            .coerceIn(MIN_CLUE_SP, MAX_CLUE_SP)
            .sp

    for (column in 0 until size) {
        val done = lineDone((0 until size).map { it * size + column })
        val runs = puzzle.columnClues[column].ifEmpty { listOf(0) }
        runs.reversed().forEachIndexed { fromBottom, run ->
            val center = Offset(gutter + column * cell + cell / 2f, gutter - (fromBottom + 0.5f) * cell * COLUMN_CLUE_HEIGHT)
            drawClue(measurer, run.toString(), center, fontSize, if (done) colors.doneInk else colors.ink)
        }
    }
    for (row in 0 until size) {
        val done = lineDone((0 until size).map { row * size + it })
        val runs = puzzle.rowClues[row].ifEmpty { listOf(0) }
        runs.reversed().forEachIndexed { fromRight, run ->
            val center = Offset(gutter - (fromRight + 0.5f) * cell * ROW_CLUE_WIDTH, gutter + row * cell + cell / 2f)
            drawClue(measurer, run.toString(), center, fontSize, if (done) colors.doneInk else colors.ink)
        }
    }

    for (row in 0 until size) {
        for (column in 0 until size) {
            val index = row * size + column
            val topLeft = Offset(gutter + column * cell, gutter + row * cell)
            val mistake = index in game.mistakeCells
            if (mistake) drawRect(colors.errorContainer, topLeft = topLeft, size = Size(cell, cell))
            when (game.cells[index]) {
                NonogramCell.FILLED -> {
                    val inset = cell * FILL_INSET
                    drawRoundRect(
                        color = if (mistake) colors.error else colors.filled,
                        topLeft = topLeft + Offset(inset, inset),
                        size = Size(cell - inset * 2, cell - inset * 2),
                        cornerRadius = CornerRadius(cell * FILL_CORNER),
                    )
                }
                NonogramCell.CROSSED -> {
                    val inset = cell * CROSS_INSET
                    val stroke = (cell * CROSS_STROKE).coerceAtLeast(1f)
                    val color = if (mistake) colors.error else colors.cross
                    drawLine(color, topLeft + Offset(inset, inset), topLeft + Offset(cell - inset, cell - inset), stroke, StrokeCap.Round)
                    drawLine(color, topLeft + Offset(cell - inset, inset), topLeft + Offset(inset, cell - inset), stroke, StrokeCap.Round)
                }
                NonogramCell.UNKNOWN -> Unit
            }
        }
    }

    // Thin lines between cells, stronger every five cells and around the board.
    for (line in 0..size) {
        val major = line == 0 || line == size || line % MAJOR_EVERY == 0
        val color = if (major) colors.majorGrid else colors.grid
        val width = if (major) 2f else 1f
        val offset = gutter + line * cell
        drawLine(color, Offset(offset, if (major) 0f else gutter), Offset(offset, gutter + boardSide), width)
        drawLine(color, Offset(if (major) 0f else gutter, offset), Offset(gutter + boardSide, offset), width)
    }
}

private fun DrawScope.drawClue(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    fontSize: TextUnit,
    color: Color,
) {
    val layout = measurer.measure(text, TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = color))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

private fun progression(
    from: Int,
    to: Int,
): List<Int> = if (to >= from) (from + 1..to).toList() else (from - 1 downTo to).toList()

/** How wide one row clue number is, and how tall one column clue number, in cells. */
private const val ROW_CLUE_WIDTH = 0.62f
private const val COLUMN_CLUE_HEIGHT = 0.62f
private const val MIN_GUTTER_CELLS = 1.2f
private const val CLUE_FONT_RATIO = 0.5f
private const val MIN_CLUE_SP = 9f
private const val MAX_CLUE_SP = 20f
private const val DONE_CLUE_ALPHA = 0.45f
private const val FILL_INSET = 0.06f
private const val FILL_CORNER = 0.12f
private const val CROSS_INSET = 0.3f
private const val CROSS_STROKE = 0.07f
private const val MAJOR_EVERY = 5
