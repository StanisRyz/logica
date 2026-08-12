package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition

@Composable
internal fun SudokuBoard(
    game: SudokuGameState,
    selectedCell: SudokuPosition?,
    onCellSelected: (SudokuPosition) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedValue = selectedCell?.let(game::cellAt)?.value?.takeIf { it != 0 }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier =
            modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val cellSize = size.width / BOARD_SIZE
                    for (line in 0..BOARD_SIZE) {
                        val coordinate = line * cellSize
                        val strong = line % BLOCK_SIZE == 0
                        val stroke = if (strong) STRONG_GRID_WIDTH.toPx() else THIN_GRID_WIDTH.toPx()
                        val color = if (strong) colors.outline else colors.outlineVariant
                        drawLine(color, Offset(coordinate, 0f), Offset(coordinate, size.height), stroke, StrokeCap.Square)
                        drawLine(color, Offset(0f, coordinate), Offset(size.width, coordinate), stroke, StrokeCap.Square)
                    }
                },
    ) {
        val cellWidth = maxWidth / BOARD_SIZE
        /*
         * Both text sizes are derived from the cell the board actually got, and converted through
         * the current density rather than written as fixed `sp`: a cell does not grow with the
         * system font scale, so a candidate expressed in scaled units is exactly what leaves the
         * digits clipped or hanging outside their cell on a real device.
         */
        val candidateTextSize =
            with(LocalDensity.current) {
                (cellWidth * CANDIDATE_TEXT_RATIO).coerceIn(CANDIDATE_MIN_TEXT, CANDIDATE_MAX_TEXT).toSp()
            }
        val valueTextSize =
            with(LocalDensity.current) {
                (cellWidth * VALUE_TEXT_RATIO).coerceIn(VALUE_MIN_TEXT, VALUE_MAX_TEXT).toSp()
            }
        Column(Modifier.size(maxWidth)) {
            repeat(BOARD_SIZE) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(BOARD_SIZE) { column ->
                        val position = SudokuPosition(row, column)
                        val cell = game.cellAt(position)
                        SudokuCell(
                            position = position,
                            cell = cell,
                            isSelected = position == selectedCell,
                            isPeer = selectedCell?.let(position::isPeerOrSameUnit) == true,
                            isSameNumber =
                                selectedValue != null &&
                                    position != selectedCell &&
                                    cell.status.isConfirmedValue &&
                                    cell.value == selectedValue,
                            isHintTarget = game.currentHint?.position == position,
                            enabled = enabled && !game.status.isTerminal,
                            candidateTextSize = candidateTextSize,
                            valueTextSize = valueTextSize,
                            onClick = { onCellSelected(position) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SudokuCell(
    position: SudokuPosition,
    cell: SudokuCellState,
    isSelected: Boolean,
    isPeer: Boolean,
    isSameNumber: Boolean,
    isHintTarget: Boolean,
    enabled: Boolean,
    candidateTextSize: TextUnit,
    valueTextSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when {
            cell.status == SudokuCellStatus.INCORRECT -> colors.errorContainer
            isSelected -> colors.primaryContainer
            isHintTarget -> colors.tertiaryContainer
            isSameNumber -> colors.secondaryContainer
            cell.status == SudokuCellStatus.GIVEN -> colors.surfaceVariant
            isPeer -> colors.surfaceContainerLow
            else -> colors.surface
        }
    val stateLabel =
        stringResource(
            when (cell.status) {
                SudokuCellStatus.EMPTY -> R.string.editable_cell
                SudokuCellStatus.GIVEN -> R.string.fixed_cell
                SudokuCellStatus.CORRECT -> R.string.confirmed_cell
                SudokuCellStatus.INCORRECT -> R.string.incorrect_cell
            },
        )
    val valueLabel = if (cell.value == 0) stringResource(R.string.cell_empty) else cell.value.toString()
    val candidateSuffix =
        if (cell.candidates.isEmpty) {
            ""
        } else {
            stringResource(R.string.pencil_marks_suffix, cell.candidates.digits.joinToString())
        }
    val selectedSuffix = if (isSelected) stringResource(R.string.sudoku_selected_suffix) else ""
    val description =
        stringResource(
            R.string.sudoku_cell_description,
            position.row + 1,
            position.column + 1,
            valueLabel,
            stateLabel,
            selectedSuffix + candidateSuffix,
        )
    Box(
        modifier =
            modifier
                .background(background)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics {
                    contentDescription = description
                    if (enabled) role = Role.Button
                }.drawWithContent {
                    drawContent()
                    if (isSelected) {
                        drawRect(colors.primary, style = Stroke(width = SELECTED_WIDTH.toPx()))
                    } else if (isHintTarget) {
                        drawRect(colors.tertiary, style = Stroke(width = HINT_WIDTH.toPx()))
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (cell.status == SudokuCellStatus.EMPTY) {
            SudokuCandidates(cell, candidateTextSize)
        } else {
            Text(
                text = cell.value.toString(),
                fontSize = valueTextSize,
                lineHeight = valueTextSize,
                fontWeight = if (cell.status.isConfirmedValue) FontWeight.Bold else FontWeight.Medium,
                color = if (cell.status == SudokuCellStatus.INCORRECT) colors.onErrorContainer else colors.onSurface,
                maxLines = 1,
                style = LocalTextStyle.current.merge(COMPACT_CELL_TEXT_STYLE),
            )
        }
        when (cell.status) {
            SudokuCellStatus.GIVEN -> CellStatusIcon(Icons.Filled.Lock, colors.onSurfaceVariant, Alignment.TopEnd)
            SudokuCellStatus.CORRECT -> CellStatusIcon(Icons.Filled.Check, colors.primary, Alignment.TopEnd)
            SudokuCellStatus.INCORRECT -> CellStatusIcon(Icons.Filled.PriorityHigh, colors.error, Alignment.TopStart)
            SudokuCellStatus.EMPTY -> Unit
        }
    }
}

/**
 * The 3x3 candidate grid, kept strictly inside its own cell. Each digit is a single centred line
 * whose line box is its own font size — the inherited body line height is taller than a third of a
 * cell, which is what used to push the lower candidates over the grid lines and out of the cell.
 */
@Composable
private fun SudokuCandidates(
    cell: SudokuCellState,
    textSize: TextUnit,
) {
    Column(Modifier.fillMaxSize().padding(CANDIDATE_PADDING)) {
        repeat(BLOCK_SIZE) { candidateRow ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(BLOCK_SIZE) { candidateColumn ->
                    val digit = candidateRow * BLOCK_SIZE + candidateColumn + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (cell.candidates.contains(digit)) {
                            Text(
                                text = digit.toString(),
                                fontSize = textSize,
                                lineHeight = textSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.merge(COMPACT_CELL_TEXT_STYLE),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CellStatusIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    alignment: Alignment,
) {
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.align(alignment).padding(2.dp).size(STATUS_ICON_SIZE),
    )
}

private fun SudokuPosition.isPeerOrSameUnit(selected: SudokuPosition): Boolean =
    this != selected &&
        (row == selected.row || column == selected.column || blockIndex == selected.blockIndex)

private val SudokuPosition.blockIndex: Int
    get() = (row / BLOCK_SIZE) * BLOCK_SIZE + column / BLOCK_SIZE

private val SudokuCellStatus.isConfirmedValue: Boolean
    get() = this == SudokuCellStatus.GIVEN || this == SudokuCellStatus.CORRECT

/** No font padding and no extra leading: the glyph gets the whole box a small cell can spare. */
private val COMPACT_CELL_TEXT_STYLE =
    TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
    )

private const val BOARD_SIZE = 9
private const val BLOCK_SIZE = 3
private const val CANDIDATE_TEXT_RATIO = 0.24f
private const val VALUE_TEXT_RATIO = 0.54f
private val CANDIDATE_PADDING = 1.dp
private val CANDIDATE_MIN_TEXT = 6.dp
private val CANDIDATE_MAX_TEXT = 11.dp
private val VALUE_MIN_TEXT = 16.dp
private val VALUE_MAX_TEXT = 26.dp
private val THIN_GRID_WIDTH = 0.5.dp
private val STRONG_GRID_WIDTH = 2.dp
private val SELECTED_WIDTH = 3.dp
private val HINT_WIDTH = 2.dp
private val STATUS_ICON_SIZE = 10.dp
