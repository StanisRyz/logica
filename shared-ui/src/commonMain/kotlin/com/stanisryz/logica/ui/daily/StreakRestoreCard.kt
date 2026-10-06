package com.stanisryz.logica.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_loading
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_unavailable
import com.stanisryz.logica.shared.ui.generated.resources.streak_restore_body
import com.stanisryz.logica.shared.ui.generated.resources.streak_restore_missing_gems
import com.stanisryz.logica.shared.ui.generated.resources.streak_restore_title
import com.stanisryz.logica.shared.ui.generated.resources.streak_restore_watch_ad
import com.stanisryz.logica.ui.components.GemPriceButton
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Whether the rewarded ad of a Daily offer (a streak save, an archive day) can be shown now. */
enum class DailyRewardedAdState {
    READY,
    LOADING,
    UNAVAILABLE,
}

/**
 * The offer to save a Daily streak broken yesterday, under the Daily section of the Game Hub: the
 * streak it saves, the gem price as the button itself, and one rewarded ad that says it is an ad.
 * A balance below the price says what is missing. Hosts decide when it shows and what the buttons do.
 */
@Composable
fun StreakRestoreCard(
    streakLength: Int,
    gems: Int,
    price: Int,
    adState: DailyRewardedAdState,
    onRestoreWithGems: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.tertiaryContainer) {
        Column(Modifier.padding(LogicaSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.onTertiaryContainer)
                Spacer(Modifier.width(LogicaSpacing.text))
                Text(
                    pluralStringResource(Res.plurals.streak_restore_title, streakLength, streakLength),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onTertiaryContainer,
                )
            }
            Text(
                stringResource(Res.string.streak_restore_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onTertiaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                GemPriceButton(price = price, enabled = gems >= price, onClick = onRestoreWithGems)
                OutlinedButton(onClick = onWatchAd, enabled = adState == DailyRewardedAdState.READY) {
                    when (adState) {
                        DailyRewardedAdState.LOADING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            when (adState) {
                                DailyRewardedAdState.READY -> Res.string.streak_restore_watch_ad
                                DailyRewardedAdState.LOADING -> Res.string.second_chance_loading
                                DailyRewardedAdState.UNAVAILABLE -> Res.string.second_chance_unavailable
                            },
                        ),
                    )
                }
            }
            if (gems < price) {
                Text(
                    pluralStringResource(Res.plurals.streak_restore_missing_gems, price - gems, price - gems),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onTertiaryContainer,
                )
            }
        }
    }
}
