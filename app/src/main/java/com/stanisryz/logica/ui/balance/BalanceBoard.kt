package com.stanisryz.logica.ui.balance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle

@Composable
fun BalanceBoard(
    puzzle: BalancePuzzle,
    game: BalanceGameState,
    onCellTapped: (BalancePosition) -> Unit,
    enabledPositions: Set<BalancePosition>? = null,
    modifier: Modifier = Modifier,
) {
    val conflictPositions = remember(game.violations) { game.violations.flatMapTo(mutableSetOf()) { it.affectedPositions } }
    val hint = game.currentHint
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Pencil marks have to stay readable inside one cell of whatever board size is on screen.
        val pencilPieceSize =
            (maxWidth / puzzle.size * PENCIL_PIECE_RATIO).coerceIn(MIN_PENCIL_PIECE_SIZE, MAX_PENCIL_PIECE_SIZE)
        Column(modifier = Modifier.size(maxWidth)) {
            repeat(puzzle.size) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(puzzle.size) { column ->
                        val position = BalancePosition(row, column)
                        BalanceCellView(
                            position = position,
                            value = game.board.cellAt(position),
                            status = game.statusAt(position),
                            pencilMarks = game.pencilMarksAt(position),
                            pencilPieceSize = pencilPieceSize,
                            isConflict = position in conflictPositions,
                            isHintTarget = hint?.position == position,
                            isHintEvidence = position in (hint?.evidencePositions ?: emptySet()),
                            enabled =
                                !game.isLocked(position) &&
                                    !game.status.isTerminal &&
                                    (enabledPositions == null || position in enabledPositions),
                            onClick = { onCellTapped(position) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCellView(
    position: BalancePosition,
    value: BalanceCell,
    status: BalanceCellStatus,
    pencilMarks: Set<BalanceCell>,
    pencilPieceSize: androidx.compose.ui.unit.Dp,
    isConflict: Boolean,
    isHintTarget: Boolean,
    isHintEvidence: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isFixed = status == BalanceCellStatus.FIXED
    val isIncorrect = status == BalanceCellStatus.INCORRECT
    val isConfirmed = status == BalanceCellStatus.CORRECT
    val background =
        when {
            isIncorrect -> colors.errorContainer
            isConflict -> colors.errorContainer
            isHintTarget -> colors.tertiaryContainer
            isHintEvidence -> colors.secondaryContainer
            isFixed -> colors.surfaceVariant
            isConfirmed -> colors.surfaceContainerHighest
            else -> colors.surface
        }
    val borderColor =
        when {
            isIncorrect || isConflict -> colors.error
            isHintTarget -> colors.tertiary
            isHintEvidence -> colors.secondary
            else -> colors.outlineVariant
        }
    val stateLabel =
        stringResource(
            when {
                isFixed -> R.string.fixed_cell
                isIncorrect -> R.string.incorrect_cell
                isConfirmed -> R.string.confirmed_cell
                else -> R.string.editable_cell
            },
        )
    val description =
        stringResource(
            R.string.balance_cell_description,
            position.row + 1,
            position.column + 1,
            cellAccessibilityLabel(value),
            stateLabel,
            if (isConflict) stringResource(R.string.conflict_suffix) else "",
        ) + pencilMarks.pencilAccessibilitySuffix()
    Box(
        modifier =
            modifier
                .border(if (isHintTarget || isConflict || isIncorrect) 2.dp else 1.dp, borderColor)
                .background(background)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BalancePiece(value, Modifier.fillMaxSize().padding(8.dp))
        if (isFixed) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp).size(14.dp),
            )
        }
        if (isIncorrect) {
            // A wrong value must be recognisable without relying on the error colour alone.
            Icon(
                imageVector = Icons.Filled.PriorityHigh,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.align(Alignment.TopStart).size(12.dp),
            )
        }
        if (pencilMarks.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                pencilMarks.sortedBy(BalanceCell::ordinal).forEach { mark ->
                    BalancePiece(mark, Modifier.size(pencilPieceSize))
                }
            }
        }
    }
}

@Composable
private fun Set<BalanceCell>.pencilAccessibilitySuffix(): String =
    if (isEmpty()) {
        ""
    } else {
        val labels = sortedBy(BalanceCell::ordinal).map { cellAccessibilityLabel(it) }
        stringResource(R.string.pencil_marks_suffix, labels.joinToString(separator = ", "))
    }

@Composable
private fun cellAccessibilityLabel(value: BalanceCell): String =
    stringResource(
        when (value) {
            BalanceCell.EMPTY -> R.string.cell_empty
            BalanceCell.ZERO -> R.string.cell_zero
            BalanceCell.ONE -> R.string.cell_one
        },
    )

internal fun BalanceCell.symbol(): String =
    when (this) {
        BalanceCell.EMPTY -> ""
        BalanceCell.ZERO -> "○"
        BalanceCell.ONE -> "●"
    }

/** Game pieces are invariant: they never inherit a Material content tint or invert with the theme. */
@Composable
internal fun BalancePiece(
    value: BalanceCell,
    modifier: Modifier = Modifier,
) {
    if (value == BalanceCell.EMPTY) return
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val pieceColor = if (value == BalanceCell.ONE) Color.Black else Color.White
        drawCircle(color = pieceColor, radius = radius)
        if (value == BalanceCell.ZERO) {
            drawCircle(color = Color.Black, radius = radius, style = Stroke(width = WHITE_PIECE_OUTLINE.toPx()))
        }
    }
}

private const val PENCIL_PIECE_RATIO = 0.24f
private val MIN_PENCIL_PIECE_SIZE = 7.dp
private val MAX_PENCIL_PIECE_SIZE = 12.dp
private val WHITE_PIECE_OUTLINE = 1.dp
