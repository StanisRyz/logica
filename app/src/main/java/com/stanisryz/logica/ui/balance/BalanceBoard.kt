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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    val conflictPositions =
        remember(game.violations) {
            game.violations.flatMapTo(mutableSetOf()) { it.affectedPositions }
        }
    val hint = game.currentHint

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.size(maxWidth)) {
            repeat(puzzle.size) { row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    repeat(puzzle.size) { column ->
                        val position = BalancePosition(row, column)
                        val value = game.board.cellAt(position)
                        val isFixed = position in puzzle.fixedClues
                        BalanceCellView(
                            position = position,
                            value = value,
                            isFixed = isFixed,
                            isConflict = position in conflictPositions,
                            isHintTarget = hint?.position == position,
                            isHintEvidence = position in (hint?.evidencePositions ?: emptySet()),
                            enabled = !isFixed && game.status != BalanceGameStatus.SOLVED,
                            onClick = { onCellTapped(position) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
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
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor =
        when {
            isConflict -> colorScheme.errorContainer
            isHintTarget -> colorScheme.tertiaryContainer
            isHintEvidence -> colorScheme.secondaryContainer
            isFixed -> colorScheme.surfaceVariant
            else -> colorScheme.surface
        }
    val description =
        buildString {
            append("Строка ${position.row + 1}, столбец ${position.column + 1}. ")
            append("Значение: ${value.accessibilityLabel}. ")
            append(if (isFixed) "Фиксированная клетка." else "Редактируемая клетка.")
            if (isConflict) append(" Есть конфликт.")
            if (isHintTarget) append(" Цель подсказки.")
        }

    Box(
        modifier =
            modifier
                .border(0.5.dp, colorScheme.outlineVariant)
                .background(backgroundColor)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.symbol,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (isFixed) FontWeight.Bold else FontWeight.Normal,
            color = if (isFixed) colorScheme.onSurfaceVariant else colorScheme.onSurface,
        )
    }
}

private val BalanceCell.symbol: String
    get() =
        when (this) {
            BalanceCell.EMPTY -> ""
            BalanceCell.ZERO -> "○"
            BalanceCell.ONE -> "●"
        }

private val BalanceCell.accessibilityLabel: String
    get() =
        when (this) {
            BalanceCell.EMPTY -> "пусто"
            BalanceCell.ZERO -> "ноль"
            BalanceCell.ONE -> "один"
        }
