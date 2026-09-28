package com.stanisryz.logica.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
    /** A one-word visible name under the button; the full [label] stays the spoken name. */
    val caption: String? = null,
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
        verticalAlignment = Alignment.Top,
    ) {
        val colors = MaterialTheme.colorScheme
        // One accent: only the selected tool takes the primary family; everything else stays neutral.
        val buttonColors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            )
        val toggleColors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                checkedContainerColor = colors.primaryContainer,
                checkedContentColor = colors.onPrimaryContainer,
            )
        val roomy = LocalRoomyGameplayControls.current
        tools.forEach { tool ->
            val toolModifier =
                Modifier
                    .then(if (roomy) Modifier.size(ROOMY_TOOL_SIZE) else Modifier)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BadgedBox(badge = { tool.badge?.let { Badge { Text(it) } } }) {
                    if (tool.selected == null) {
                        FilledTonalIconButton(
                            onClick = tool.onClick,
                            enabled = enabled && tool.enabled,
                            modifier = toolModifier,
                            colors = buttonColors,
                        ) { tool.symbol() }
                    } else {
                        FilledTonalIconToggleButton(
                            checked = tool.selected,
                            onCheckedChange = { tool.onClick() },
                            enabled = enabled && tool.enabled,
                            modifier = toolModifier,
                            colors = toggleColors,
                        ) { tool.symbol() }
                    }
                }
                tool.caption?.let { caption ->
                    Text(
                        text = caption,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = if (roomy) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        color =
                            when {
                                tool.selected == true -> colors.primary
                                enabled && tool.enabled -> colors.onSurfaceVariant
                                else -> colors.onSurfaceVariant.copy(alpha = DISABLED_CAPTION_ALPHA)
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val SELECTED_RING_WIDTH = 2.dp
private val ROOMY_TOOL_SIZE = 56.dp
private const val MAX_BADGE_COUNT = 99
private const val DISABLED_CAPTION_ALPHA = 0.38f

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
        symbol = { Icon(Icons.Rounded.Lightbulb, contentDescription = null) },
        caption = stringResource(Res.string.hint),
        badge = hintCount?.let { if (it > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else it.toString() },
    )
