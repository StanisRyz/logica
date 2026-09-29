package com.stanisryz.logica.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.stanisryz.logica.ads.RewardedAdState

/** The rewarded ad as the second-chance offer sees it; the shell provides it to every game. */
internal class SecondChanceAd(
    val state: RewardedAdState,
    val setVisible: (Boolean) -> Unit,
    val watch: (onGranted: () -> Unit) -> Unit,
    val retry: () -> Unit,
)

internal val LocalSecondChanceAd = staticCompositionLocalOf<SecondChanceAd?> { null }

/** True while the game on screen waits on its second-chance answer; the result card stays hidden. */
internal val LocalSecondChancePending = staticCompositionLocalOf { false }

/**
 * The Android host of the shared second-chance offer. While it is on screen the shell keeps the
 * rewarded ad loaded; a watched ad calls [onContinue], ending the level calls [onDecline].
 */
@Composable
internal fun SecondChanceDialog(
    onContinue: () -> Unit,
    onDecline: () -> Unit,
) {
    val ad = LocalSecondChanceAd.current
    DisposableEffect(ad?.setVisible) {
        ad?.setVisible?.invoke(true)
        onDispose { ad?.setVisible?.invoke(false) }
    }
    ContinueOfferDialog(
        availability =
            when (ad?.state) {
                RewardedAdState.READY -> ContinueAdAvailability.READY
                RewardedAdState.UNAVAILABLE, null -> ContinueAdAvailability.UNAVAILABLE
                else -> ContinueAdAvailability.LOADING
            },
        onWatch = { ad?.watch?.invoke(onContinue) },
        onRetry = { ad?.retry?.invoke() },
        onDecline = onDecline,
    )
}
