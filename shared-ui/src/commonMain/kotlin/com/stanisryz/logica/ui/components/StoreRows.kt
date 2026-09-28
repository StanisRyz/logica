package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.profile_gems
import com.stanisryz.logica.shared.ui.generated.resources.profile_hints_short
import com.stanisryz.logica.shared.ui.generated.resources.profile_lives
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * The Store balance on both platforms: gems, lives, and hints on one line, with an optional host
 * line under them (the next-life countdown).
 */
@Composable
fun StoreBalanceCard(
    gems: Long,
    lives: Int,
    maximumLives: Int,
    hints: Int?,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardContent).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            Row(Modifier.fillMaxWidth()) {
                BalanceMetric(Icons.Filled.Diamond, gems.toString(), stringResource(Res.string.profile_gems), Modifier.weight(1f))
                BalanceMetric(Icons.Filled.Favorite, "$lives/$maximumLives", stringResource(Res.string.profile_lives), Modifier.weight(1f))
                hints?.let {
                    BalanceMetric(
                        Icons.Filled.Lightbulb,
                        it.toString(),
                        stringResource(Res.string.profile_hints_short),
                        Modifier.weight(1f),
                    )
                }
            }
            footnote?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BalanceMetric(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A quiet section label between groups of Store rows. */
@Composable
fun StoreSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = LogicaSpacing.text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One Store line: an icon, what it is and what it gives, and its action on the right. */
@Composable
fun StoreItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlighted: Boolean = false,
    action: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = if (highlighted) colors.primaryContainer else colors.surfaceContainerLow,
                contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(ROW_PADDING).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(ICON_BOX)
                        .clip(CircleShape)
                        .background(if (highlighted) colors.surface else colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = subtitleColor) }
            }
            action()
        }
    }
}

/** The price is the button: a gem icon and the cost, so one tap spends exactly what it says. */
@Composable
fun GemPriceButton(
    price: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Icon(Icons.Filled.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(price.toString())
    }
}

private val ROW_PADDING = 14.dp
private val ICON_BOX = 40.dp
