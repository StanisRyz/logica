package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The game controls as one floating dock at the bottom of the screen, under the thumb: equal
 * tiles with an icon over a one-word caption, the selected tool filled with the accent, and counts
 * as a badge in the tile's corner. Every game with tools uses it (Sudoku adds its digits inside
 * the same [GameDock]).
 */
@Composable
fun PuzzleToolBar(
    tools: List<PuzzleTool>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GameDock(modifier.widthIn(max = DOCK_TILE_MAX_WIDTH * tools.size)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DOCK_GAP)) {
            tools.forEach { tool -> DockTile(tool, enabled, Modifier.weight(1f)) }
        }
    }
}

/** The floating card every game's controls sit in: lighter than the screen in both themes. */
@Composable
fun GameDock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > LIGHT_SURFACE_LUMINANCE
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DOCK_CORNER),
        color = if (light) colors.surfaceContainerLowest else colors.surfaceContainer,
        shadowElevation = DOCK_ELEVATION,
    ) {
        Box(Modifier.padding(DOCK_PADDING)) { content() }
    }
}

@Composable
private fun DockTile(
    tool: PuzzleTool,
    enabled: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val active = enabled && tool.enabled
    val selected = tool.selected == true
    val light = colors.surface.luminance() > LIGHT_SURFACE_LUMINANCE
    val background =
        when {
            selected -> colors.primary
            light -> colors.surfaceContainerLow
            else -> colors.surfaceContainerHigh
        }
    val content =
        when {
            selected -> colors.onPrimary
            active -> colors.onSurface
            else -> colors.onSurface.copy(alpha = DISABLED_ALPHA)
        }
    val roomy = LocalRoomyGameplayControls.current
    Box(
        modifier =
            modifier
                .height(if (roomy) ROOMY_TILE_HEIGHT else TILE_HEIGHT)
                .clip(RoundedCornerShape(TILE_CORNER))
                .background(background)
                .then(
                    if (tool.selected == null) {
                        Modifier.clickable(enabled = active, role = Role.Button, onClick = tool.onClick)
                    } else {
                        Modifier.toggleable(value = selected, enabled = active, role = Role.Tab, onValueChange = { tool.onClick() })
                    },
                ).semantics {
                    contentDescription = tool.label
                    tool.stateDescription?.let { stateDescription = it }
                },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Every symbol gets the same box, so the captions line up whatever the icon.
                Box(Modifier.height(SYMBOL_HEIGHT), contentAlignment = Alignment.Center) { tool.symbol() }
                tool.caption?.let { caption ->
                    Text(
                        text = caption,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        tool.badge?.let { badge ->
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(BADGE_MARGIN),
                containerColor = colors.error,
                contentColor = colors.onError,
            ) { Text(badge) }
        }
    }
}

private val DOCK_CORNER = 26.dp
private val DOCK_ELEVATION = 4.dp
private val DOCK_PADDING = 8.dp
private val DOCK_GAP = 8.dp
private val DOCK_TILE_MAX_WIDTH = 160.dp
private val TILE_HEIGHT = 60.dp
private val ROOMY_TILE_HEIGHT = 68.dp
private val TILE_CORNER = 18.dp
private val BADGE_MARGIN = 6.dp
private val SYMBOL_HEIGHT = 28.dp
private const val MAX_BADGE_COUNT = 99
private const val DISABLED_ALPHA = 0.38f
private const val LIGHT_SURFACE_LUMINANCE = 0.5f

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
