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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
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
import com.stanisryz.logica.shared.ui.generated.resources.no_ads_owned
import com.stanisryz.logica.shared.ui.generated.resources.no_ads_subtitle
import com.stanisryz.logica.shared.ui.generated.resources.no_ads_title
import com.stanisryz.logica.shared.ui.generated.resources.profile_gems
import com.stanisryz.logica.shared.ui.generated.resources.profile_hints_short
import com.stanisryz.logica.shared.ui.generated.resources.profile_lives
import com.stanisryz.logica.shared.ui.generated.resources.starter_pack_lives_full
import com.stanisryz.logica.shared.ui.generated.resources.starter_pack_subtitle
import com.stanisryz.logica.shared.ui.generated.resources.starter_pack_title
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
                BalanceMetric(Icons.Rounded.Diamond, gems.toString(), stringResource(Res.string.profile_gems), Modifier.weight(1f))
                BalanceMetric(Icons.Rounded.Favorite, "$lives/$maximumLives", stringResource(Res.string.profile_lives), Modifier.weight(1f))
                hints?.let {
                    BalanceMetric(
                        Icons.Rounded.Lightbulb,
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
                        .background(if (highlighted) colors.surfaceBright else colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                // A blank subtitle would still take a line and push the title above the button.
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                }
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
        Icon(Icons.Rounded.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(price.toString())
    }
}

private val ROW_PADDING = 14.dp
private val ICON_BOX = 40.dp

/** What the one-time starter pack holds, the same on both platforms. */
object StarterPackContents {
    const val GEMS = 100
    const val HINTS = 5
}

/**
 * The one-time starter pack, offered until it is bought: its contents as three tiles and the
 * platform price as the button ([priceContent]). [message] reports the running or last payment.
 */
@Composable
fun StarterPackCard(
    enabled: Boolean,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    priceContent: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(ICON_BOX).clip(CircleShape).background(colors.surfaceBright),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.starter_pack_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        message ?: stringResource(Res.string.starter_pack_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PackTile(
                    Icons.Rounded.Diamond,
                    "+${StarterPackContents.GEMS}",
                    stringResource(Res.string.profile_gems),
                    Modifier.weight(1f),
                )
                PackTile(
                    Icons.Rounded.Lightbulb,
                    "+${StarterPackContents.HINTS}",
                    stringResource(Res.string.profile_hints_short),
                    Modifier.weight(1f),
                )
                PackTile(
                    Icons.Rounded.Favorite,
                    stringResource(Res.string.starter_pack_lives_full),
                    stringResource(Res.string.profile_lives),
                    Modifier.weight(1f),
                )
            }
            Button(onClick = onBuy, enabled = enabled, modifier = Modifier.fillMaxWidth()) { priceContent() }
        }
    }
}

@Composable
private fun PackTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceBright)
            .padding(vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "$value $label" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

/**
 * «No ads»: a permanent purchase that turns off interstitials and banners (rewarded ads stay, the
 * player's own choice). Once owned the row says so instead of offering it again.
 */
@Composable
fun NoAdsRow(
    owned: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    priceContent: @Composable () -> Unit,
) {
    StoreItemRow(
        icon = Icons.Rounded.Block,
        title = stringResource(Res.string.no_ads_title),
        subtitle = message ?: stringResource(if (owned) Res.string.no_ads_owned else Res.string.no_ads_subtitle),
        modifier = modifier,
    ) {
        if (owned) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onBuy, enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp)) { priceContent() }
        }
    }
}
