package com.stanisryz.logica.ui.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
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
        Column(modifier = Modifier.size(maxWidth)) {
            repeat(puzzle.size) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(puzzle.size) { column ->
                        val position = BalancePosition(row, column)
                        BalanceCellView(
                            position = position,
                            value = game.board.cellAt(position),
                            isFixed = position in puzzle.fixedClues,
                            isConflict = position in conflictPositions,
                            isHintTarget = hint?.position == position,
                            isHintEvidence = position in (hint?.evidencePositions ?: emptySet()),
                            enabled =
                                position !in puzzle.fixedClues &&
                                    game.status != BalanceGameStatus.SOLVED &&
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
    isFixed: Boolean,
    isConflict: Boolean,
    isHintTarget: Boolean,
    isHintEvidence: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when {
            isConflict -> colors.errorContainer
            isHintTarget -> colors.tertiaryContainer
            isHintEvidence -> colors.secondaryContainer
            isFixed -> colors.surfaceVariant
            else -> colors.surface
        }
    val borderColor =
        when {
            isConflict -> colors.error
            isHintTarget -> colors.tertiary
            isHintEvidence -> colors.secondary
            else -> colors.outlineVariant
        }
    val description =
        stringResource(
            R.string.balance_cell_description,
            position.row + 1,
            position.column + 1,
            cellAccessibilityLabel(value),
            if (isFixed) stringResource(R.string.fixed_cell) else stringResource(R.string.editable_cell),
            if (isConflict) stringResource(R.string.conflict_suffix) else "",
        )
    Box(
        modifier =
            modifier
                .border(if (isHintTarget || isConflict) 2.dp else 1.dp, borderColor)
                .background(background)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.symbol(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (isFixed) FontWeight.Bold else FontWeight.Normal,
            color = if (isFixed) colors.onSurfaceVariant else colors.onSurface,
        )
        if (isFixed) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.fixed_cell),
                modifier = Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp).size(14.dp),
            )
        }
    }
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

private fun BalanceCell.symbol(): String =
    when (this) {
        BalanceCell.EMPTY -> ""
        BalanceCell.ZERO -> "○"
        BalanceCell.ONE -> "●"
    }
