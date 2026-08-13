package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.FilledTonalButton
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
    Row(modifier = modifier.fillMaxWidth()) {
        (1..DIGIT_COUNT).forEach { digit ->
            FilledTonalButton(
                onClick = { onDigit(digit) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = MIN_KEY_HEIGHT),
            ) {
                Text(
                    text = digit.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
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

private val MIN_KEY_HEIGHT = 48.dp
private const val DIGIT_COUNT = 9
