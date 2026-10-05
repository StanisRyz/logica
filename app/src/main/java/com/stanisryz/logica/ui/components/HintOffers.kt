package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.HintOffer
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.ui.components.STATE_ARTWORK_DIALOG_SIZE
import com.stanisryz.logica.ui.components.StateArtwork
import com.stanisryz.logica.ui.components.StateArtworkImage
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** Asked for a hint with an empty stock: restock right here so the running attempt is kept. */
@Composable
internal fun HintsExhaustedDialog(
    economy: PlayerEconomy,
    onBuy: (HintOffer) -> Unit,
    onDismiss: () -> Unit,
    onOpenStore: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StateArtworkImage(StateArtwork.NO_HINTS, size = STATE_ARTWORK_DIALOG_SIZE) },
        title = { Text(stringResource(R.string.hints_exhausted_title), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    text = stringResource(R.string.hints_exhausted_body, economy.gems),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HintOfferButtons(economy, onBuy)
            }
        },
        // The Store opens over the running game, so topping up gems here never ends the attempt.
        confirmButton = {
            onOpenStore?.let { open ->
                TextButton(
                    onClick = {
                        onDismiss()
                        open()
                    },
                ) { Text(stringResource(R.string.hints_open_store)) }
            }
        },
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
                    HintOffer.PACK -> pluralStringResource(R.plurals.hints_offer_pack, offer.hints, offer.hints, offer.gemCost)
                },
            )
        }
    }
}
