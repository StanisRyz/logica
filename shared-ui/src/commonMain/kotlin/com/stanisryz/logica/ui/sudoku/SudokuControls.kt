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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import org.jetbrains.compose.resources.stringResource

@Composable
fun SudokuNumberPad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = SUDOKU_DIGIT_ROW_SPACING,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        (1..DIGIT_COUNT).chunked(DIGITS_PER_ROW).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { digit ->
                    SudokuDigitButton(digit = digit, enabled = enabled, onClick = { onDigit(digit) })
                }
            }
        }
    }
}

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
fun SudokuToolBar(
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
                pencilTool(isPencilMode, onToggle),
                PuzzleTool(
                    label = stringResource(Res.string.hint),
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

/** Tutorial mode exposes the same production Pencil control and semantics. */
@Composable
fun SudokuPencilToggle(
    isPencilMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleToolBar(
        tools = listOf(pencilTool(isPencilMode, onToggle)),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun pencilTool(
    isPencilMode: Boolean,
    onToggle: () -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = stringResource(Res.string.tool_pencil),
        stateDescription = stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off),
        selected = isPencilMode,
        onClick = onToggle,
        symbol = { Icon(Icons.Filled.Edit, contentDescription = null) },
    )

private const val DIGIT_COUNT = 9
private const val DIGITS_PER_ROW = 3

internal val SUDOKU_DIGIT_CONTROL_SIZE = 44.dp
internal val SUDOKU_DIGIT_ROW_SPACING = 8.dp
