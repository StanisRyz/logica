package com.stanisryz.logica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.ads.RewardedAdState
import com.stanisryz.logica.economy.EconomyClock
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The compact wallet shown on every gameplay-relevant screen: lives out of the maximum and the gem
 * balance. Both carry a Material icon plus their number, never an emoji, and the whole row opens the
 * lives detail.
 */
@Composable
internal fun EconomyBar(
    economy: PlayerEconomy,
    onOpenLives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            R.string.economy_resources_description,
            economy.lives,
            EconomyRules.MAX_LIVES,
            economy.gems,
        )
    Row(
        modifier =
            modifier
                .clickable(onClick = onOpenLives)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(
            icon = if (economy.isGameplayAllowed) Icons.Filled.Favorite else Icons.Filled.HeartBroken,
            label = stringResource(R.string.economy_lives_short, economy.lives, EconomyRules.MAX_LIVES),
            contentColor =
                if (economy.isGameplayAllowed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
        StatusChip(icon = Icons.Filled.Diamond, label = economy.gems.toString())
    }
}

/**
 * The lives detail: how many are left, when the next one comes back, and what a gem refill costs.
 * Deliberately a small dialog rather than a store screen.
 *
 * At zero lives it also carries the one advertising offer in the product: an optional rewarded ad
 * worth `+1` life. The offer is the last thing added and the first thing that may fail, so the
 * countdown and the gem refill above it stay readable and usable whether or not an ad exists.
 */
@Composable
internal fun LivesDialog(
    economy: PlayerEconomy,
    rewardedState: RewardedAdState,
    onRestoreLife: () -> Unit,
    onWatchRewardedAd: () -> Unit,
    onRetryRewardedAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val countdown = rememberLifeCountdown(economy)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
        title = { Text(stringResource(R.string.economy_lives_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    text =
                        buildString {
                            appendLine(
                                stringResource(R.string.economy_lives_value, economy.lives, EconomyRules.MAX_LIVES),
                            )
                            if (economy.isFull) {
                                append(stringResource(R.string.economy_lives_full))
                            } else {
                                appendLine(stringResource(R.string.economy_next_life_in, countdown.orEmpty()))
                                append(
                                    if (economy.canRefillLifeWithGems) {
                                        stringResource(R.string.economy_refill_cost, EconomyRules.LIFE_REFILL_GEM_COST)
                                    } else {
                                        stringResource(
                                            R.string.economy_not_enough_gems,
                                            EconomyRules.LIFE_REFILL_GEM_COST,
                                            economy.gems,
                                        )
                                    },
                                )
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Only at zero lives: the offer exists to unblock gameplay, never to top a wallet up.
                if (!economy.isGameplayAllowed) {
                    RewardedLifeOffer(rewardedState, onWatchRewardedAd, onRetryRewardedAd)
                }
            }
        },
        confirmButton = {
            if (!economy.isFull) {
                TextButton(onClick = onRestoreLife, enabled = economy.canRefillLifeWithGems) {
                    Text(stringResource(R.string.economy_restore_for_gems, EconomyRules.LIFE_REFILL_GEM_COST))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * The rewarded offer, one state at a time: loading is announced and disabled, a loaded ad is the
 * only enabled action, a showing ad refuses a second request, and an unavailable one says so and
 * offers a single deliberate retry instead of hammering the network.
 */
@Composable
private fun RewardedLifeOffer(
    state: RewardedAdState,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        Text(
            text =
                stringResource(
                    when (state) {
                        RewardedAdState.UNAVAILABLE -> R.string.economy_rewarded_ad_unavailable
                        RewardedAdState.SHOWING -> R.string.economy_rewarded_ad_showing
                        RewardedAdState.READY -> R.string.economy_rewarded_ad_offer
                        RewardedAdState.IDLE, RewardedAdState.LOADING -> R.string.economy_rewarded_ad_loading
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state == RewardedAdState.UNAVAILABLE) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.economy_rewarded_ad_retry)) }
        } else {
            Button(onClick = onWatch, enabled = state == RewardedAdState.READY) {
                Icon(Icons.Filled.Slideshow, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                Text(
                    text = stringResource(R.string.economy_rewarded_ad_watch),
                    modifier = Modifier.padding(start = LogicaSpacing.text),
                )
            }
        }
    }
}

/**
 * The zero-life state, shown wherever gameplay is currently blocked. It always names the wait and
 * offers the gem refill; the saved puzzle behind it stays untouched.
 */
@Composable
internal fun ZeroLivesCard(
    economy: PlayerEconomy,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (economy.isGameplayAllowed) return
    val countdown = rememberLifeCountdown(economy)
    LogicaCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        verticalSpacing = LogicaSpacing.text,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.HeartBroken, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Text(
                text = stringResource(R.string.economy_no_lives_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(stringResource(R.string.economy_no_lives_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.economy_next_life_in, countdown.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!economy.canRefillLifeWithGems) {
            Text(
                text =
                    stringResource(
                        R.string.economy_not_enough_gems,
                        EconomyRules.LIFE_REFILL_GEM_COST,
                        economy.gems,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = onRestoreLife, enabled = economy.canRefillLifeWithGems) {
            Text(stringResource(R.string.economy_restore_for_gems, EconomyRules.LIFE_REFILL_GEM_COST))
        }
    }
}

/**
 * What one finished attempt did to the wallet. It is only ever rendered once the Room transaction
 * that persisted the result reported success.
 */
@Composable
internal fun EconomyResultFeedback(
    isSolved: Boolean,
    lives: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSolved) Icons.Filled.Diamond else Icons.Filled.HeartBroken,
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text =
                if (isSolved) {
                    stringResource(R.string.economy_reward_gem, EconomyRules.SOLVED_GEM_REWARD)
                } else {
                    stringResource(R.string.economy_penalty_life, lives, EconomyRules.MAX_LIVES)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The live `m:ss` wait for the next regenerated life, or `null` while the wallet is full. */
@Composable
private fun rememberLifeCountdown(economy: PlayerEconomy): String? {
    var nowEpochMillis by remember { mutableLongStateOf(EconomyClock.SYSTEM.nowEpochMillis()) }
    LaunchedEffect(economy) {
        while (economy.nextLifeAtEpochMillis != null) {
            nowEpochMillis = EconomyClock.SYSTEM.nowEpochMillis()
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }
    val remaining = economy.millisUntilNextLife(nowEpochMillis) ?: return null
    val totalSeconds = (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / SECONDS_PER_MINUTE,
        totalSeconds % SECONDS_PER_MINUTE,
    )
}

private val ICON_SIZE = 20.dp
private const val COUNTDOWN_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
