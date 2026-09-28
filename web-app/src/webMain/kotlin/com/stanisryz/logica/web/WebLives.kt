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
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.EconomyState
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.delay

/**
 * Host-side view of the bound Player's lives. [guard] runs a Catalog attempt start only while at
 * least one life is left; Daily challenges never consume lives on Web and are never gated.
 */
internal class WebLivesUi(
    val state: EconomyState?,
    val guard: (start: () -> Unit) -> Unit,
)

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
            delay((dueAt - currentTimeMillis()).coerceIn(MIN_REFRESH_DELAY_MS, EconomyPolicy.LIFE_RESTORE_INTERVAL_MS))
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
            buildString {
                append("Жизни: ${state.lives} из ${EconomyPolicy.MAXIMUM_LIVES}")
                if (dueAt != null) append(" · новая через ${formatLifeCountdown(dueAt - now)}")
            },
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
        title = { Text("Жизни закончились") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                Text(
                    buildString {
                        if (dueAt != null) append("Новая жизнь появится через ${formatLifeCountdown(dueAt - now)}. ")
                        append("Жизнь можно получить за рекламу или восстановить в магазине. Задача дня доступна и без жизней.")
                    },
                )
                Button(
                    onClick = rewardedLife::requestReward,
                    enabled = rewardedLife.isRequestAllowed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (adState == WebRewardedAdState.Showing) "Идёт реклама…" else "+1 жизнь за рекламу")
                }
                if (adState != WebRewardedAdState.Idle && adState != WebRewardedAdState.Showing) {
                    val (message, color) = rewardedAdSubtitle(adState, "Реклама просмотрена: +1 жизнь.")
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenStore) { Text("В магазин") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}

/** Wall-clock time that advances once per second while [ticking]; used only for countdown text. */
@Composable
internal fun rememberNowMs(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = currentTimeMillis()
        while (ticking) {
            delay(COUNTDOWN_TICK_MS)
            now = currentTimeMillis()
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
