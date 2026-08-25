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
 * Write-side fullscreen-ad seam consumed by ad controllers and implemented by `WebHostLifecycle`.
 * While a rewarded/interstitial advertisement owns the screen the effective lifecycle becomes
 * INACTIVE (GameplayAPI stopped, audio paused); closing re-evaluates real browser visibility,
 * focus, and Yandex pause state instead of blindly forcing ACTIVE.
 */
internal fun interface WebFullscreenAdActivity {
    fun setFullscreenAdActive(active: Boolean)
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
 * Hardening (45.14a): every invocation owns a runtime-only session id; callbacks may mutate
 * state only while their session is still active, so late callbacks from a finished ad can
 * never finish or grant a newer one. The Player context is captured when the session starts
 * and re-validated right before granting, so an account switch can never pay the new Player
 * for an old session. Cooldowns begin only after the platform reports the ad actually opened.
 */
internal class WebStoreRewardedHintsController(
    private val provider: RewardedAdProvider,
    private val policy: WebAdPolicy,
    private val rewardService: WebRewardService,
    private val analytics: WebMonetizationAnalytics,
    private val fullscreenAdActivity: WebFullscreenAdActivity,
    private val currentPlayerContext: () -> WebPlayerContextToken?,
    private val currentTimeMs: () -> Long,
) {
    private val mutableState = MutableStateFlow(WebRewardedHintState.Idle)
    val state: StateFlow<WebRewardedHintState> = mutableState.asStateFlow()

    private var nextSessionId = 0L

    /** The currently active invocation, or null; runtime-only and never persisted. */
    private var activeSession: Long? = null

    val isRequestAllowed: Boolean
        get() = activeSession == null

    fun requestReward() {
        if (!isRequestAllowed) return // double tap / one active session per placement
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.REWARDED, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            mutableState.value = WebRewardedHintState.Cooldown
            return
        }
        val session = ++nextSessionId
        activeSession = session
        val capturedContext = currentPlayerContext()
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        mutableState.value = WebRewardedHintState.Showing
        provider.show(
            onOpened = {
                if (activeSession == session) {
                    // Cooldown begins only after real platform exposure.
                    policy.markShown(AdKind.REWARDED, currentTimeMs())
                    fullscreenAdActivity.setFullscreenAdActive(true)
                }
            },
            onResult = { result -> onProviderResult(session, capturedContext, result) },
        )
    }

    private fun onProviderResult(
        session: Long,
        capturedContext: WebPlayerContextToken?,
        result: AdShowResult,
    ) {
        if (activeSession != session) return // stale/late callback from another invocation
        activeSession = null
        fullscreenAdActivity.setFullscreenAdActive(false)
        when (result) {
            AdShowResult.Completed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_COMPLETED)
                // Re-validate the captured Player context immediately before granting: an
                // account switch must never let the new Player receive the old session reward.
                val contextStillValid =
                    capturedContext != null && capturedContext == currentPlayerContext()
                val granted =
                    contextStillValid &&
                        rewardService.apply(HINT_REWARD).also { granted ->
                            if (granted) {
                                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.REWARD_GRANTED)
                            }
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
 *
 * Hardening (45.14a): while an interstitial transition is active, further Next Level requests
 * are IGNORED entirely (never queued, never double-advanced); every invocation carries a
 * runtime session id so late callbacks from an older attempt cannot execute a newer
 * continuation; cooldowns begin only after the platform reports actual exposure.
 */
internal class WebInterstitialContinuationController(
    private val provider: InterstitialAdProvider,
    private val policy: WebAdPolicy,
    private val analytics: WebMonetizationAnalytics,
    private val fullscreenAdActivity: WebFullscreenAdActivity,
    private val currentTimeMs: () -> Long,
) {
    private var nextSessionId = 0L

    /** The currently active transition attempt, or null; runtime-only and never persisted. */
    private var activeAttempt: Long? = null

    fun runWithInterstitial(
        placementId: String,
        continuation: () -> Unit,
    ) {
        if (activeAttempt != null) {
            // A second tap while a transition is active is ignored: one user action must never
            // advance two Catalog levels.
            return
        }
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.INTERSTITIAL, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            continuation()
            return
        }
        val session = ++nextSessionId
        activeAttempt = session
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        var continued = false

        fun continueOnce() {
            if (continued) return
            continued = true
            if (activeAttempt == session) activeAttempt = null
            continuation()
        }

        provider.show(
            onOpened = {
                if (activeAttempt == session) {
                    // Cooldown begins only after real platform exposure.
                    policy.markShown(AdKind.INTERSTITIAL, currentTimeMs())
                    fullscreenAdActivity.setFullscreenAdActive(true)
                }
            },
            onResult = { result ->
                if (activeAttempt != session) return@show // stale/late callback from another attempt
                fullscreenAdActivity.setFullscreenAdActive(false)
                when (result) {
                    AdShowResult.Completed -> analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_COMPLETED)
                    else -> analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                }
                continueOnce()
            },
        )
    }
}

/** Read/write sticky-banner boundary; implemented by `YandexGamesBridge` only. */
internal interface WebStickyBannerBridge {
    fun showStickyBanner(): Boolean

    fun hideStickyBanner(): Boolean

    suspend fun stickyBannerStatus(): Boolean?
}

/**
 * Sticky-banner visibility controller at the host boundary. The banner itself is rendered by
 * Yandex Games — nothing is drawn in Compose.
 *
 * Hardening (45.14a): desired visibility and successfully-applied visibility are tracked
 * separately. A failed show/hide leaves the state unapplied so any later event-driven
 * reconciliation (route change, lifecycle ACTIVE, fullscreen close) retries it; identical
 * desired/applied states never issue redundant SDK calls; unsupported APIs are safe no-ops.
 */
internal class WebStickyBannerController(
    private val bridge: WebStickyBannerBridge,
) {
    private var desiredVisible: Boolean? = null
    private var appliedVisible: Boolean? = null

    /** Requests a new platform-side visibility; failures remain retryable via [reconcile]. */
    fun applyVisibility(visible: Boolean) {
        desiredVisible = visible
        reconcile()
    }

    /** Reconciles desired vs applied visibility; no polling — callers invoke this on events. */
    fun reconcile() {
        val desired = desiredVisible ?: return
        if (appliedVisible == desired) return
        val applied = if (desired) bridge.showStickyBanner() else bridge.hideStickyBanner()
        if (applied) appliedVisible = desired
    }

    /**
     * Heals unknown applied state from the platform status when supported (e.g., after a failed
     * transition or initialization); never queries on recomposition — call sites decide when.
     */
    suspend fun reconcileUsingPlatformStatus() {
        val desired = desiredVisible ?: return
        if (appliedVisible == desired) return
        val actual = bridge.stickyBannerStatus() ?: return
        appliedVisible = actual
        if (appliedVisible != desired) reconcile()
    }
}
