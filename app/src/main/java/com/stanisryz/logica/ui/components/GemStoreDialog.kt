package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.store.GemPackOffer
import com.stanisryz.logica.store.GemPurchaseOutcome
import com.stanisryz.logica.store.GemStoreState
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The one Gem Store in the product, reached from the gem balance in the top bar and from the lives
 * dialog when a refill is out of reach. It sells gem packs and nothing else.
 *
 * Every price on screen is RuStore's own formatted label; the application never states an amount of
 * money. The gem counts beside them come from the local [GemPack] table, so what a pack is worth is
 * this build's number rather than something read out of store metadata.
 */
@Composable
internal fun GemStoreDialog(
    economy: PlayerEconomy,
    state: GemStoreState,
    onBuy: (GemPack) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Diamond, contentDescription = null) },
        title = { Text(stringResource(R.string.gem_store_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                // The balance stays visible in every state, including a store that cannot load.
                Text(
                    text = stringResource(R.string.gem_store_balance, economy.gems),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (state) {
                    GemStoreState.Loading -> GemStoreLoading()
                    GemStoreState.Unavailable -> GemStoreUnavailable(onRetry)
                    is GemStoreState.Ready -> GemStoreOffers(state, onBuy)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun GemStoreLoading() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE))
        SupportingText(stringResource(R.string.gem_store_loading))
    }
}

@Composable
private fun GemStoreUnavailable(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        SupportingText(stringResource(R.string.gem_store_unavailable))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.gem_store_retry)) }
    }
}

@Composable
private fun GemStoreOffers(
    state: GemStoreState.Ready,
    onBuy: (GemPack) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        state.offers.forEach { offer ->
            GemPackRow(
                offer = offer,
                // One payment at a time: every Buy is disabled while any of them is running.
                enabled = state.purchasing == null,
                isPurchasing = state.purchasing == offer.pack,
                onBuy = { onBuy(offer.pack) },
            )
        }
        state.outcome?.let { GemPurchaseMessage(it) }
    }
}

@Composable
private fun GemPackRow(
    offer: GemPackOffer,
    enabled: Boolean,
    isPurchasing: Boolean,
    onBuy: () -> Unit,
) {
    LogicaCard(verticalSpacing = LogicaSpacing.text) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Diamond, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Text(
                text = stringResource(R.string.gem_store_pack_gems, offer.pack.gems),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBuy, enabled = enabled) {
                // The price label is RuStore's, formatted and localized by the store itself.
                Text(if (isPurchasing) stringResource(R.string.gem_store_purchasing) else offer.priceLabel)
            }
        }
    }
}

@Composable
private fun GemPurchaseMessage(outcome: GemPurchaseOutcome) {
    SupportingText(
        when (outcome) {
            is GemPurchaseOutcome.Granted -> stringResource(R.string.gem_store_granted, outcome.pack.gems)
            GemPurchaseOutcome.Processing -> stringResource(R.string.gem_store_processing)
            GemPurchaseOutcome.Cancelled -> stringResource(R.string.gem_store_cancelled)
            GemPurchaseOutcome.Failed -> stringResource(R.string.gem_store_failed)
        },
    )
}

private val ICON_SIZE = 20.dp
private val INDICATOR_SIZE = 18.dp
