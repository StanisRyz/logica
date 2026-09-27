package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.HintOffer
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** Asked for a hint with an empty stock: restock right here so the running attempt is kept. */
@Composable
internal fun HintsExhaustedDialog(
    economy: PlayerEconomy,
    onBuy: (HintOffer) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
        title = { Text(stringResource(R.string.hints_exhausted_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    text = stringResource(R.string.hints_exhausted_body, economy.gems),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HintOfferButtons(economy, onBuy)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hints_not_now)) } },
    )
}

/** One button per [HintOffer], enabled only while the wallet can pay for it. */
@Composable
internal fun HintOfferButtons(
    economy: PlayerEconomy,
    onBuy: (HintOffer) -> Unit,
) {
    HintOffer.entries.forEach { offer ->
        FilledTonalButton(
            onClick = { onBuy(offer) },
            enabled = economy.gems >= offer.gemCost,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (offer) {
                    HintOffer.SINGLE -> stringResource(R.string.hints_offer_single, offer.gemCost)
                    HintOffer.PACK -> stringResource(R.string.hints_offer_pack, offer.hints, offer.gemCost)
                },
            )
        }
    }
}
