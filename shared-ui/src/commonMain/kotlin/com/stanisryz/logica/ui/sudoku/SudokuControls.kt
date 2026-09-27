package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_auto_candidates
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_digits_left
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_erase
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_undo
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_auto
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_erase
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_undo
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import com.stanisryz.logica.ui.components.hintTool
import org.jetbrains.compose.resources.stringResource

@Composable
fun SudokuNumberPad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = SUDOKU_DIGIT_ROW_SPACING,
    remaining: ((Int) -> Int)? = null,
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
                    val left = remaining?.invoke(digit)
                    // A digit whose nine places are all confirmed has nothing left to place.
                    SudokuDigitButton(digit = digit, left = left, enabled = enabled && left != 0, onClick = { onDigit(digit) })
                }
            }
        }
    }
}

@Composable
private fun SudokuDigitButton(
    digit: Int,
    left: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val leftDescription = left?.let { stringResource(Res.string.sudoku_digits_left, it) }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier.size(SUDOKU_DIGIT_CONTROL_SIZE).semantics {
                leftDescription?.let { stateDescription = it }
            },
        shape = CircleShape,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = digit.toString(), style = MaterialTheme.typography.titleMedium)
            if (left != null && left > 0) {
                Text(
                    text = left.toString(),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SudokuToolBar(
    isPencilMode: Boolean,
    onToggle: () -> Unit,
    onErase: () -> Unit,
    eraseEnabled: Boolean,
    onAutoCandidates: () -> Unit,
    autoCandidatesEnabled: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    hintEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wrapTools: Boolean = false,
    hintCount: Int? = null,
) {
    val tools =
        listOf(
            pencilTool(isPencilMode, onToggle),
            PuzzleTool(
                label = stringResource(Res.string.sudoku_erase),
                stateDescription = null,
                selected = null,
                enabled = eraseEnabled,
                onClick = onErase,
                symbol = { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null) },
                caption = stringResource(Res.string.tool_caption_erase),
            ),
            PuzzleTool(
                label = stringResource(Res.string.sudoku_auto_candidates),
                stateDescription = null,
                selected = null,
                enabled = autoCandidatesEnabled,
                onClick = onAutoCandidates,
                symbol = { Text("1–9", style = MaterialTheme.typography.labelSmall) },
                caption = stringResource(Res.string.tool_caption_auto),
            ),
            PuzzleTool(
                label = stringResource(Res.string.sudoku_undo),
                stateDescription = null,
                selected = null,
                enabled = canUndo,
                onClick = onUndo,
                symbol = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                caption = stringResource(Res.string.tool_caption_undo),
            ),
            hintTool(hintCount = hintCount, enabled = hintEnabled, onClick = onHint),
        )

    if (wrapTools) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(COMPACT_TOOL_ROW_SPACING)) {
            PuzzleToolBar(tools.take(3), enabled = enabled)
            PuzzleToolBar(tools.drop(3), enabled = enabled)
        }
    } else {
        PuzzleToolBar(tools = tools, modifier = modifier, enabled = enabled)
    }
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
        caption = stringResource(Res.string.tool_pencil),
    )

private const val DIGIT_COUNT = 9
private const val DIGITS_PER_ROW = 3

internal val SUDOKU_DIGIT_CONTROL_SIZE = 48.dp
internal val SUDOKU_DIGIT_ROW_SPACING = 8.dp
private val COMPACT_TOOL_ROW_SPACING = 2.dp
