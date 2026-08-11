package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun SudokuNumberPad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
    ) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            ) {
                repeat(3) { column ->
                    val digit = row * 3 + column + 1
                    FilledTonalButton(
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = MIN_KEY_HEIGHT),
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SudokuPencilToggle(
    isPencilMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (isPencilMode) R.string.tool_on else R.string.tool_off)
    FilterChip(
        selected = isPencilMode,
        onClick = onToggle,
        enabled = enabled,
        label = { Text(stringResource(R.string.tool_pencil)) },
        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        modifier = modifier.semantics { stateDescription = state },
    )
}

private val MIN_KEY_HEIGHT = 48.dp
