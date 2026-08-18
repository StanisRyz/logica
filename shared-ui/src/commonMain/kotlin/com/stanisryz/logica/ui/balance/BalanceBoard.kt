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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.balance_cell_description
import com.stanisryz.logica.shared.ui.generated.resources.cell_empty
import com.stanisryz.logica.shared.ui.generated.resources.cell_one
import com.stanisryz.logica.shared.ui.generated.resources.cell_zero
import com.stanisryz.logica.shared.ui.generated.resources.confirmed_cell
import com.stanisryz.logica.shared.ui.generated.resources.conflict_suffix
import com.stanisryz.logica.shared.ui.generated.resources.editable_cell
import com.stanisryz.logica.shared.ui.generated.resources.fixed_cell
import com.stanisryz.logica.shared.ui.generated.resources.incorrect_cell
import com.stanisryz.logica.shared.ui.generated.resources.pencil_marks_suffix
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BalanceBoard(
    puzzle: BalancePuzzle,
    game: BalanceGameState,
    onCellTapped: (BalancePosition) -> Unit,
    enabledPositions: Set<BalancePosition>? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val conflictPositions = remember(game.violations) { game.violations.flatMapTo(mutableSetOf()) { it.affectedPositions } }
    val hint = game.currentHint
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Tutorial callers may offer unbounded height; in that case width remains the real limit.
        // Gameplay offers both dimensions, so a short host shrinks the board instead of clipping it.
        val boardSide = minOf(maxWidth, maxHeight)
        val pencilPieceSize =
            (boardSide / puzzle.size * PENCIL_PIECE_RATIO).coerceIn(MIN_PENCIL_PIECE_SIZE, MAX_PENCIL_PIECE_SIZE)
        Column(modifier = Modifier.size(boardSide)) {
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
                                enabled &&
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
    pencilPieceSize: Dp,
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
            isIncorrect || isConflict -> colors.errorContainer
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
                isFixed -> Res.string.fixed_cell
                isIncorrect -> Res.string.incorrect_cell
                isConfirmed -> Res.string.confirmed_cell
                else -> Res.string.editable_cell
            },
        )
    val description =
        stringResource(
            Res.string.balance_cell_description,
            position.row + 1,
            position.column + 1,
            cellAccessibilityLabel(value),
            stateLabel,
            if (isConflict) stringResource(Res.string.conflict_suffix) else "",
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
        stringResource(Res.string.pencil_marks_suffix, labels.joinToString(separator = ", "))
    }

@Composable
private fun cellAccessibilityLabel(value: BalanceCell): String = stringResource(value.accessibilityResource())

private fun BalanceCell.accessibilityResource(): StringResource =
    when (this) {
        BalanceCell.EMPTY -> Res.string.cell_empty
        BalanceCell.ZERO -> Res.string.cell_zero
        BalanceCell.ONE -> Res.string.cell_one
    }

internal fun BalanceCell.symbol(): String =
    when (this) {
        BalanceCell.EMPTY -> ""
        BalanceCell.ZERO -> "○"
        BalanceCell.ONE -> "●"
    }

/** Game pieces remain literal black/white and never invert with the Material theme. */
@Composable
fun BalancePiece(
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
