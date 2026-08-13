package com.stanisryz.logica.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * One selectable input tool: the value a tap will place, the pencil modifier, or a hint. Its name
 * is exposed to accessibility while the visual stays a compact, icon-only control.
 */
internal data class PuzzleTool(
    val label: String,
    val stateDescription: String?,
    val selected: Boolean?,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val symbol: @Composable () -> Unit,
)

/** The compact explicit input row shared by Balance and Crowns; it sits directly under the board. */
@Composable
internal fun PuzzleToolBar(
    tools: List<PuzzleTool>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tools.forEach { tool ->
            val toolModifier =
                Modifier
                    .then(
                        if (tool.selected == true) {
                            Modifier.border(SELECTED_RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ).semantics {
                        contentDescription = tool.label
                        tool.stateDescription?.let { stateDescription = it }
                        tool.selected?.let { selected = it }
                    }
            if (tool.selected == null) {
                FilledTonalIconButton(
                    onClick = tool.onClick,
                    enabled = enabled && tool.enabled,
                    modifier = toolModifier,
                ) { tool.symbol() }
            } else {
                FilledTonalIconToggleButton(
                    checked = tool.selected,
                    onCheckedChange = { tool.onClick() },
                    enabled = enabled && tool.enabled,
                    modifier = toolModifier,
                ) { tool.symbol() }
            }
        }
    }
}

private val SELECTED_RING_WIDTH = 2.dp
