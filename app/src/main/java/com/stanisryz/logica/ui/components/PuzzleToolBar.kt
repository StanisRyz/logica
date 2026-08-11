package com.stanisryz.logica.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * One selectable input tool: the value a tap will place, or the pencil modifier. The caption is both
 * the visible label and the accessibility name, and the selected tool is marked by an outline ring
 * and a bold caption so the choice never depends on colour alone.
 */
internal data class PuzzleTool(
    val label: String,
    val stateDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val symbol: @Composable () -> Unit,
)

/** The explicit input row shared by Balance and Crowns; it sits directly under the board. */
@Composable
internal fun PuzzleToolBar(
    tools: List<PuzzleTool>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        tools.forEach { tool ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            ) {
                FilledTonalIconToggleButton(
                    checked = tool.selected,
                    onCheckedChange = { tool.onClick() },
                    enabled = enabled,
                    modifier =
                        Modifier
                            .then(
                                if (tool.selected) {
                                    Modifier.border(SELECTED_RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            ).semantics {
                                contentDescription = tool.label
                                stateDescription = tool.stateDescription
                                selected = tool.selected
                            },
                ) {
                    tool.symbol()
                }
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (tool.selected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The toggle button already announces this label; the caption is decorative.
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

private val SELECTED_RING_WIDTH = 2.dp
