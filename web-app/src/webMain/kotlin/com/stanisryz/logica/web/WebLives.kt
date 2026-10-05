package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.EconomyState
import com.stanisryz.logica.ui.components.ContinueAdAvailability
import com.stanisryz.logica.ui.components.GameResultLifeOffer
import com.stanisryz.logica.ui.components.STATE_ARTWORK_DIALOG_SIZE
import com.stanisryz.logica.ui.components.StateArtwork
import com.stanisryz.logica.ui.components.StateArtworkImage
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_ad_showing
import com.stanisryz.logica.web.generated.resources.web_got_it
import com.stanisryz.logica.web.generated.resources.web_life_ad_button
import com.stanisryz.logica.web.generated.resources.web_life_ad_granted
import com.stanisryz.logica.web.generated.resources.web_lives_next
import com.stanisryz.logica.web.generated.resources.web_lives_status
import com.stanisryz.logica.web.generated.resources.web_no_lives_body
import com.stanisryz.logica.web.generated.resources.web_no_lives_next
import com.stanisryz.logica.web.generated.resources.web_no_lives_title
import com.stanisryz.logica.web.generated.resources.web_to_store
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/**
 * Host-side view of the bound Player's lives. [guard] runs a Catalog or Daily attempt start only
 * while at least one life is left, exactly like on Android.
 */
internal class WebLivesUi(
    val state: EconomyState?,
    val guard: (start: () -> Unit) -> Unit,
    val rewardedLife: WebRewardedPlacementController? = null,
)

/**
 * At zero lives the result card offers the next life's countdown and one life for a rewarded ad,
 * through the same placement as the no-lives dialog; with a life it offers nothing extra.
 */
@Composable
internal fun webResultLifeOffer(): GameResultLifeOffer? {
    val lives = LocalWebLives.current
    val placement = lives.rewardedLife
    val adState = placement?.state?.collectAsState()?.value
    val state = lives.state ?: return null
    if (state.lives > 0) return null
    return GameResultLifeOffer(
        nextLifeAtEpochMs = state.nextLifeRestoreAtEpochMs,
        nowEpochMs = webClock::now,
        ad =
            when (adState) {
                null, WebRewardedAdState.Unavailable, WebRewardedAdState.Error, WebRewardedAdState.Cooldown ->
                    ContinueAdAvailability.UNAVAILABLE
                WebRewardedAdState.Showing -> ContinueAdAvailability.LOADING
                else -> ContinueAdAvailability.READY
            },
        onWatchAd = { placement?.requestReward() },
        onRetryAd = { placement?.requestReward() },
    )
}

/** Without a bound wallet nothing is gated, matching the wallet's never-blocking foundation. */
internal val LocalWebLives = compositionLocalOf { WebLivesUi(state = null, guard = { start -> start() }) }

/**
 * The only regeneration timer on Web: while the effective lifecycle is active and a life is
 * missing, it persists due regeneration and then sleeps until the next life is due. No polling.
 */
@Composable
internal fun WebLivesRegenerationEffect(
    repository: WebPlayerEconomyRepository?,
    nextLifeRestoreAtEpochMs: Long?,
    active: Boolean,
) {
    // Keyed on the countdown too, so a new loss after a full wallet starts the timer again.
    LaunchedEffect(repository, nextLifeRestoreAtEpochMs, active) {
        if (repository == null || !active) return@LaunchedEffect
        while (true) {
            repository.refresh()
            val dueAt = repository.state.value.nextLifeRestoreAtEpochMs ?: break
            delay((dueAt - webClock.now()).coerceIn(MIN_REFRESH_DELAY_MS, EconomyPolicy.LIFE_RESTORE_INTERVAL_MS))
        }
    }
}

/** Compact lives line with the countdown to the next life, shown before a Catalog level starts. */
@Composable
internal fun WebLivesStatus(
    state: EconomyState,
    modifier: Modifier = Modifier,
) {
    val dueAt = state.nextLifeRestoreAtEpochMs
    val now = rememberNowMs(ticking = dueAt != null)
    Text(
        text =
            stringResource(WebRes.string.web_lives_status, state.lives, EconomyPolicy.MAXIMUM_LIVES) +
                (dueAt?.let { stringResource(WebRes.string.web_lives_next, formatLifeCountdown(it - now)) } ?: ""),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.lives > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun WebNoLivesDialog(
    state: EconomyState?,
    rewardedLife: WebRewardedPlacementController,
    onOpenStore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dueAt = state?.nextLifeRestoreAtEpochMs
    val now = rememberNowMs(ticking = dueAt != null)
    val adState by rewardedLife.state.collectAsState()
    // A life that came back (from the ad, regeneration, or a purchase) closes the dialog.
    val lives = state?.lives ?: 0
    LaunchedEffect(lives) { if (lives > 0) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StateArtworkImage(StateArtwork.NO_LIVES, size = STATE_ARTWORK_DIALOG_SIZE) },
        title = { Text(stringResource(WebRes.string.web_no_lives_title), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                Text(
                    (dueAt?.let { stringResource(WebRes.string.web_no_lives_next, formatLifeCountdown(it - now)) } ?: "") +
                        stringResource(WebRes.string.web_no_lives_body),
                )
                Button(
                    onClick = rewardedLife::requestReward,
                    enabled = rewardedLife.isRequestAllowed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (adState ==
                                WebRewardedAdState.Showing
                            ) {
                                WebRes.string.web_ad_showing
                            } else {
                                WebRes.string.web_life_ad_button
                            },
                        ),
                    )
                }
                if (adState != WebRewardedAdState.Idle && adState != WebRewardedAdState.Showing) {
                    val (message, color) = rewardedAdSubtitle(adState, stringResource(WebRes.string.web_life_ad_granted))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenStore) { Text(stringResource(WebRes.string.web_to_store)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(WebRes.string.web_got_it)) } },
    )
}

/** [webClock] time that advances once per second while [ticking]; used only for countdown text. */
@Composable
internal fun rememberNowMs(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(webClock.now()) }
    LaunchedEffect(ticking) {
        now = webClock.now()
        while (ticking) {
            delay(COUNTDOWN_TICK_MS)
            now = webClock.now()
        }
    }
    return now
}

/** "12:05" for the remaining wait; never negative and never above one regeneration interval. */
internal fun formatLifeCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceIn(0L, EconomyPolicy.LIFE_RESTORE_INTERVAL_MS) + 999L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val MIN_REFRESH_DELAY_MS = 1_000L
private const val COUNTDOWN_TICK_MS = 1_000L
