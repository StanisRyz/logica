package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar

@Composable
internal fun SudokuNumberPad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SUDOKU_DIGIT_ROW_SPACING),
    ) {
        (1..DIGIT_COUNT).chunked(DIGITS_PER_ROW).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SUDOKU_DIGIT_ROW_SPACING),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                row.forEach { digit ->
                    SudokuDigitButton(digit = digit, enabled = enabled, onClick = { onDigit(digit) })
                }
            }
        }
    }
}

/** A focused, padding-free circular key so every digit sits at the exact center of its control. */
@Composable
private fun SudokuDigitButton(
    digit: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(SUDOKU_DIGIT_CONTROL_SIZE),
        shape = CircleShape,
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun SudokuToolBar(
    isPencilMode: Boolean,
    onToggle: () -> Unit,
    onHint: () -> Unit,
    hintEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PuzzleToolBar(
        tools =
            listOf(
                PuzzleTool(
                    label = stringResource(R.string.tool_pencil),
                    stateDescription = stringResource(if (isPencilMode) R.string.tool_on else R.string.tool_off),
                    selected = isPencilMode,
                    onClick = onToggle,
                    symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
                ),
                PuzzleTool(
                    label = stringResource(R.string.hint),
                    stateDescription = null,
                    selected = null,
                    enabled = hintEnabled,
                    onClick = onHint,
                    symbol = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
                ),
            ),
        modifier = modifier,
        enabled = enabled,
    )
}

/** Tutorial mode exposes only Pencil, but keeps the production control's icon and semantics. */
@Composable
internal fun SudokuPencilToggle(
    isPencilMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleToolBar(
        tools =
            listOf(
                PuzzleTool(
                    label = stringResource(R.string.tool_pencil),
                    stateDescription = stringResource(if (isPencilMode) R.string.tool_on else R.string.tool_off),
                    selected = isPencilMode,
                    onClick = onToggle,
                    symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
                ),
            ),
        modifier = modifier,
        enabled = enabled,
    )
}

private const val DIGIT_COUNT = 9
private const val DIGITS_PER_ROW = 3
private const val KEYPAD_ROW_COUNT = DIGIT_COUNT / DIGITS_PER_ROW

internal val SUDOKU_DIGIT_CONTROL_SIZE = 44.dp
internal val SUDOKU_DIGIT_ROW_SPACING = 8.dp

internal val SudokuNumberPadHeight =
    SUDOKU_DIGIT_CONTROL_SIZE * KEYPAD_ROW_COUNT + SUDOKU_DIGIT_ROW_SPACING * (KEYPAD_ROW_COUNT - 1)
