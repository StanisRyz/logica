package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
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
import com.stanisryz.logica.shared.ui.generated.resources.store_ad_gem
import com.stanisryz.logica.shared.ui.generated.resources.store_ad_life
import com.stanisryz.logica.shared.ui.generated.resources.store_gems_150
import com.stanisryz.logica.shared.ui.generated.resources.store_gems_50
import com.stanisryz.logica.shared.ui.generated.resources.store_gems_500
import com.stanisryz.logica.shared.ui.generated.resources.store_hint_pack
import com.stanisryz.logica.shared.ui.generated.resources.store_hint_single
import com.stanisryz.logica.shared.ui.generated.resources.store_life
import com.stanisryz.logica.shared.ui.generated.resources.store_no_ads
import com.stanisryz.logica.shared.ui.generated.resources.store_starter_pack
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
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
                BalanceMetric(GameIcon.GEM, gems.toString(), stringResource(Res.string.profile_gems), Modifier.weight(1f))
                BalanceMetric(GameIcon.lives(lives), "$lives/$maximumLives", stringResource(Res.string.profile_lives), Modifier.weight(1f))
                hints?.let {
                    BalanceMetric(
                        GameIcon.HINT,
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
    icon: GameIcon,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameIconImage(icon, size = 22.dp)
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

/** The Store's pictures: hosts pick one per row, while the drawables stay in the shared resources. */
enum class StoreArtwork(
    internal val drawable: DrawableResource,
) {
    GEMS_SMALL(Res.drawable.store_gems_50),
    GEMS_MEDIUM(Res.drawable.store_gems_150),
    GEMS_LARGE(Res.drawable.store_gems_500),
    STARTER_PACK(Res.drawable.store_starter_pack),
    NO_ADS(Res.drawable.store_no_ads),
    HINT(Res.drawable.store_hint_single),
    HINTS(Res.drawable.store_hint_pack),
    LIFE(Res.drawable.store_life),
    AD_GEM(Res.drawable.store_ad_gem),
    AD_LIFE(Res.drawable.store_ad_life),
    ;

    companion object {
        /** A bigger gem pack shows a bigger heap: a handful, a pouch, then a chest. */
        fun forGemPack(gems: Int): StoreArtwork =
            when {
                gems >= LARGE_PACK_GEMS -> GEMS_LARGE
                gems >= MEDIUM_PACK_GEMS -> GEMS_MEDIUM
                else -> GEMS_SMALL
            }

        /** One hint or a pack of them. */
        fun forHints(hints: Int): StoreArtwork = if (hints > 1) HINTS else HINT

        private const val MEDIUM_PACK_GEMS = 150
        private const val LARGE_PACK_GEMS = 500
    }
}

/** One Store line: its picture, what it is and what it gives, and its action on the right. */
@Composable
fun StoreItemRow(
    artwork: StoreArtwork,
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
            Image(
                imageResource(artwork.drawable),
                contentDescription = null,
                modifier = Modifier.size(ARTWORK),
                filterQuality = ArtworkFilterQuality,
            )
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
private val ARTWORK = 48.dp
private val STARTER_ARTWORK = 64.dp

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
                Image(
                    imageResource(StoreArtwork.STARTER_PACK.drawable),
                    contentDescription = null,
                    filterQuality = ArtworkFilterQuality,
                    modifier = Modifier.size(STARTER_ARTWORK),
                )
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
                    GameIcon.GEM,
                    "+${StarterPackContents.GEMS}",
                    stringResource(Res.string.profile_gems),
                    Modifier.weight(1f),
                )
                PackTile(
                    GameIcon.HINT,
                    "+${StarterPackContents.HINTS}",
                    stringResource(Res.string.profile_hints_short),
                    Modifier.weight(1f),
                )
                PackTile(
                    GameIcon.HEART,
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
    icon: GameIcon,
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
            GameIconImage(icon)
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
        artwork = StoreArtwork.NO_ADS,
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
