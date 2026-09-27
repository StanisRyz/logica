package com.stanisryz.logica.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.hints_left
import org.jetbrains.compose.resources.stringResource

data class PuzzleTool(
    val label: String,
    val stateDescription: String?,
    val selected: Boolean?,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val symbol: @Composable () -> Unit,
    /** A short count drawn on the button's corner, outside its clipped circle. */
    val badge: String? = null,
)

/** Compact explicit puzzle input shared by the migrated Balance surface and Android peers. */
@Composable
fun PuzzleToolBar(
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
            BadgedBox(badge = { tool.badge?.let { Badge { Text(it) } } }) {
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
}

private val SELECTED_RING_WIDTH = 2.dp
private const val MAX_BADGE_COUNT = 99

/**
 * The shared Hint action. Hints are a consumable inventory item, so a non-null [hintCount] shows
 * the remaining stock as a badge; onboarding surfaces without an inventory pass `null`.
 */
@Composable
fun hintTool(
    hintCount: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = stringResource(Res.string.hint),
        stateDescription = hintCount?.let { stringResource(Res.string.hints_left, it) },
        selected = null,
        enabled = enabled,
        onClick = onClick,
        symbol = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
        badge = hintCount?.let { if (it > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else it.toString() },
    )
