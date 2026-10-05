package com.stanisryz.logica.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.stanisryz.logica.ui.components.ContinueAdAvailability
import com.stanisryz.logica.ui.components.ContinueOfferDialog
import com.stanisryz.logica.ui.components.ContinueOfferKind

/** The rewarded placement behind the second chance; null in hosts without ads. */
internal val LocalWebSecondChanceAd = staticCompositionLocalOf<WebRewardedPlacementController?> { null }

/**
 * The Web host of the shared second-chance offer: a watched ad calls [onContinue], ending the
 * level calls [onDecline]. Without an ad placement only ending the level is offered.
 */
@Composable
internal fun WebSecondChanceDialog(
    onContinue: () -> Unit,
    onDecline: () -> Unit,
    kind: ContinueOfferKind = ContinueOfferKind.THIRD_MISTAKE,
) {
    PauseGameKeysWhileShown()
    val placement = LocalWebSecondChanceAd.current
    val state = placement?.state?.collectAsState()?.value
    ContinueOfferDialog(
        availability =
            when (state) {
                null, WebRewardedAdState.Unavailable, WebRewardedAdState.Error, WebRewardedAdState.Cooldown ->
                    ContinueAdAvailability.UNAVAILABLE
                WebRewardedAdState.Showing -> ContinueAdAvailability.LOADING
                else -> ContinueAdAvailability.READY
            },
        onWatch = { placement?.requestReward(onGranted = onContinue) },
        onRetry = { placement?.requestReward(onGranted = onContinue) },
        onDecline = onDecline,
        kind = kind,
    )
}
