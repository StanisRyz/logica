package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_undo_offer_body
import com.stanisryz.logica.shared.ui.generated.resources.game_2048_undo_offer_title
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_body
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_decline
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_loading
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_retry
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_title
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_unavailable
import com.stanisryz.logica.shared.ui.generated.resources.second_chance_watch
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/** Where the host's rewarded ad stands while the second chance is on offer. */
enum class ContinueAdAvailability {
    LOADING,
    READY,
    UNAVAILABLE,
}

/** What the ad-paid second chance gives back: one mistake, or 2048's losing move. */
enum class ContinueOfferKind {
    THIRD_MISTAKE,
    UNDO_LAST_MOVE,
}

/**
 * The one second chance an attempt gets: at its third mistake watch a rewarded ad and one mistake is
 * taken back on the same board (2048: its losing move is taken back), or end the level as usual. Back
 * or a tap outside never decides for the player; only the two buttons do. Hosts own the ad and what
 * either answer does.
 */
@Composable
fun ContinueOfferDialog(
    availability: ContinueAdAvailability,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
    onDecline: () -> Unit,
    kind: ContinueOfferKind = ContinueOfferKind.THIRD_MISTAKE,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh), border = darkDialogEdge()) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            ) {
                StateArtworkImage(StateArtwork.SECOND_CHANCE, size = STATE_ARTWORK_DIALOG_SIZE)
                Text(
                    stringResource(
                        when (kind) {
                            ContinueOfferKind.THIRD_MISTAKE -> Res.string.second_chance_title
                            ContinueOfferKind.UNDO_LAST_MOVE -> Res.string.game_2048_undo_offer_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        when (kind) {
                            ContinueOfferKind.THIRD_MISTAKE -> Res.string.second_chance_body
                            ContinueOfferKind.UNDO_LAST_MOVE -> Res.string.game_2048_undo_offer_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                when (availability) {
                    ContinueAdAvailability.READY ->
                        Button(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.second_chance_watch))
                        }
                    ContinueAdAvailability.LOADING ->
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.second_chance_loading))
                        }
                    ContinueAdAvailability.UNAVAILABLE -> {
                        Text(
                            stringResource(Res.string.second_chance_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.second_chance_retry)) }
                    }
                }
                OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.second_chance_decline))
                }
            }
        }
    }
}
