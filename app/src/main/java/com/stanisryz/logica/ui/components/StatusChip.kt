package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A compact status badge. The icon plus the label carry the meaning, so a state is never
 * communicated by color alone.
 */
@Composable
internal fun StatusChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = CHIP_VERTICAL_PADDING)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHIP_ICON_SPACING),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(CHIP_ICON_SIZE))
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

private val CHIP_HORIZONTAL_PADDING = 10.dp
private val CHIP_VERTICAL_PADDING = 6.dp
private val CHIP_ICON_SPACING = 6.dp
private val CHIP_ICON_SIZE = 16.dp
