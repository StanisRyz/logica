package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdKind
import com.stanisryz.logica.platform.AdRewardDefinition
import com.stanisryz.logica.platform.AdShowResult
import com.stanisryz.logica.platform.MonetizationAnalyticsEvent
import com.stanisryz.logica.platform.StoreRewardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable production placement identities for the internal monetization analytics. */
internal object WebAdPlacements {
    const val STORE_HINT_REWARDED = "store_hint_rewarded"
    const val CATALOG_NEXT_LEVEL_INTERSTITIAL = "catalog_next_level_interstitial"
}

/**
 * One application-level fullscreen-ad lifecycle seam: while a rewarded/interstitial advertisement
 * is on screen the host is forced inactive (GameplayAPI stopped, audio paused through the same
 * Yandex lifecycle path), and every close re-evaluates real browser visibility/focus conditions
 * instead of blindly forcing ACTIVE.
 */
internal class WebFullscreenAdGate(
    private val isHostEligible: () -> Boolean,
) {
    private var showing = false
    private val mutableAdShowing = MutableStateFlow(false)
    private val mutableActive = MutableStateFlow(false)

    /** Whether a rewarded/interstitial advertisement currently owns the screen. */
    val adShowing: StateFlow<Boolean> = mutableAdShowing.asStateFlow()

    /** Effective host activity; gameplay/audio derive from this, never from ad callbacks alone. */
    val isActive: StateFlow<Boolean> = mutableActive.asStateFlow()

    init {
        refresh()
    }

    fun setAdShowing(value: Boolean) {
        showing = value
        mutableAdShowing.value = value
        refresh()
    }

    /** Re-evaluates after host signals change (visibility, focus, Yandex pause/resume). */
    fun refresh() {
        mutableActive.value = !showing && isHostEligible()
    }
}

/** Compact UI state of the Store's rewarded-hints placement. */
internal enum class WebRewardedHintState {
    Idle,
    Showing,
    RewardGranted,
    Dismissed,
    Unavailable,
    Error,
    Cooldown,
}

/**
 * The first production rewarded placement: Store -> controller -> Yandex rewarded provider ->
 * [WebRewardService] -> existing Store inventory -> normal durable-change unified save flow.
 *
 * Exactly-once guarantees: only one session may be active for the placement; duplicate SDK
 * terminal callbacks are ignored; a close callback never grants by itself; rewards apply only
 * for real provider completion and land as one ordinary Store inventory mutation (+3 hints)
 * with one fresh Player-scoped revision. Dismissed/Failed/Unavailable grant nothing.
 */
internal class WebStoreRewardedHintsController(
    private val provider: RewardedAdProvider,
    private val policy: WebAdPolicy,
    private val rewardService: WebRewardService,
    private val analytics: WebMonetizationAnalytics,
    private val adGate: WebFullscreenAdGate,
    private val currentTimeMs: () -> Long,
) {
    private val mutableState = MutableStateFlow(WebRewardedHintState.Idle)
    val state: StateFlow<WebRewardedHintState> = mutableState.asStateFlow()

    private var sessionActive = false
    private var rewardGrantedInSession = false
    private var terminalResultSeen = false

    val isRequestAllowed: Boolean
        get() = !sessionActive && mutableState.value != WebRewardedHintState.Showing

    fun requestReward() {
        if (!isRequestAllowed) return // double tap / one active session per placement
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.REWARDED, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            mutableState.value = WebRewardedHintState.Cooldown
            return
        }
        policy.markShown(AdKind.REWARDED, now)
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        sessionActive = true
        rewardGrantedInSession = false
        terminalResultSeen = false
        mutableState.value = WebRewardedHintState.Showing
        adGate.setAdShowing(true)
        provider.show(::onProviderResult)
    }

    private fun onProviderResult(result: AdShowResult) {
        if (terminalResultSeen) return // duplicate SDK terminal callbacks grant nothing extra
        terminalResultSeen = true
        sessionActive = false
        adGate.setAdShowing(false)
        when (result) {
            AdShowResult.Completed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_COMPLETED)
                // Exactly one +3 hints grant per successful session, through the ordinary
                // durable Store inventory mutation path (fresh revision, unified save dirty).
                val granted =
                    if (rewardGrantedInSession) {
                        true
                    } else {
                        rewardGrantedInSession = rewardService.apply(HINT_REWARD)
                        if (rewardGrantedInSession) {
                            analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.REWARD_GRANTED)
                        }
                        rewardGrantedInSession
                    }
                mutableState.value =
                    if (granted) WebRewardedHintState.RewardGranted else WebRewardedHintState.Error
            }
            AdShowResult.Dismissed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedHintState.Dismissed
            }
            AdShowResult.Unavailable -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedHintState.Unavailable
            }
            is AdShowResult.Failed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedHintState.Error
            }
        }
    }

    companion object {
        /** The explicit, always-disclosed exchange for this placement. */
        val HINT_REWARD = AdRewardDefinition(rewardType = StoreRewardType.HINTS, amount = 3)
    }
}

/**
 * The interstitial continuation controller: eligibility is checked against [WebAdPolicy], one ad
 * attempt may run, and the requested continuation runs EXACTLY once no matter how many terminal
 * callbacks arrive or whether any ad appears at all. Advertisements never block navigation.
 */
internal class WebInterstitialContinuationController(
    private val provider: InterstitialAdProvider,
    private val policy: WebAdPolicy,
    private val analytics: WebMonetizationAnalytics,
    private val adGate: WebFullscreenAdGate,
    private val currentTimeMs: () -> Long,
) {
    private var attemptActive = false

    fun runWithInterstitial(
        placementId: String,
        continuation: () -> Unit,
    ) {
        if (attemptActive) {
            // A second request during an in-flight attempt must not queue another transition.
            continuation()
            return
        }
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.INTERSTITIAL, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            continuation()
            return
        }
        policy.markShown(AdKind.INTERSTITIAL, now)
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        attemptActive = true
        var continued = false

        fun continueOnce() {
            if (continued) return
            continued = true
            attemptActive = false
            continuation()
        }

        adGate.setAdShowing(true)
        provider.show { result ->
            adGate.setAdShowing(false)
            when (result) {
                AdShowResult.Completed -> analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_COMPLETED)
                else -> analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
            }
            continueOnce()
        }
    }
}

/**
 * Sticky-banner visibility controller at the host boundary. The banner itself is rendered by
 * Yandex Games — nothing is drawn in Compose. The last requested state is tracked so route or
 * recomposition churn never issues redundant SDK transitions, and unsupported APIs are no-ops.
 */
internal class WebStickyBannerController(
    private val bridge: YandexGamesBridge,
) {
    private var lastRequestedVisible: Boolean? = null

    fun applyVisibility(visible: Boolean) {
        if (lastRequestedVisible == visible) return
        lastRequestedVisible = visible
        if (visible) bridge.showStickyBanner() else bridge.hideStickyBanner()
    }
}
