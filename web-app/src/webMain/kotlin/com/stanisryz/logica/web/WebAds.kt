package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdKind
import com.stanisryz.logica.platform.AdRewardDefinition
import com.stanisryz.logica.platform.AdShowResult
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.MonetizationAnalyticsEvent
import com.stanisryz.logica.platform.StoreRewardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable production placement identities for the internal monetization analytics. */
internal object WebAdPlacements {
    const val STORE_GEM_REWARDED = "store_gem_rewarded"
    const val CATALOG_NEXT_LEVEL_INTERSTITIAL = "catalog_next_level_interstitial"
    const val GAMEPLAY_TRANSITION_INTERSTITIAL = "gameplay_transition_interstitial"
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

/** Compact UI state of one rewarded placement. */
internal enum class WebRewardedAdState {
    Idle,
    Showing,
    RewardGranted,
    Dismissed,
    Unavailable,
    Error,
    Cooldown,
}

/**
 * One rewarded placement: UI -> controller -> Yandex rewarded provider -> [WebRewardService] ->
 * Economy wallet ([GEM_REWARD], [LIFE_REWARD]) -> normal
 * durable-change unified save flow. Each placement owns its own controller instance.
 *
 * Hardening (45.14a): every invocation owns a runtime-only session id; callbacks may mutate
 * state only while their session is still active, so late callbacks from a finished ad can
 * never finish or grant a newer one. The Player context is captured when the session starts
 * and re-validated right before granting, so an account switch can never pay the new Player
 * for an old session. Cooldowns begin only after the platform reports the ad actually opened.
 */
internal class WebRewardedPlacementController(
    /** What the placement pays into the wallet; null for the second chance, whose reward is the game going on. */
    private val reward: AdRewardDefinition?,
    private val provider: RewardedAdProvider,
    private val policy: WebAdPolicy,
    private val rewardService: WebRewardService,
    private val analytics: WebMonetizationAnalytics,
    private val fullscreenAdActivity: WebFullscreenAdActivity,
    private val currentPlayerContext: () -> WebPlayerContextToken?,
    private val currentTimeMs: () -> Long,
) {
    private val mutableState = MutableStateFlow(WebRewardedAdState.Idle)
    val state: StateFlow<WebRewardedAdState> = mutableState.asStateFlow()

    private var nextSessionId = 0L

    /** The currently active invocation, or null; runtime-only and never persisted. */
    private var activeSession: Long? = null

    val isRequestAllowed: Boolean
        get() = activeSession == null

    /** The caller's own grant for this request, run once on a valid reward (the second chance). */
    private var onGranted: (() -> Unit)? = null

    fun requestReward(onGranted: (() -> Unit)? = null) {
        if (!isRequestAllowed) return // double tap / one active session per placement
        this.onGranted = onGranted
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.REWARDED, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            mutableState.value = WebRewardedAdState.Cooldown
            return
        }
        val session = ++nextSessionId
        activeSession = session
        val capturedContext = currentPlayerContext()
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        mutableState.value = WebRewardedAdState.Showing
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
                        (reward == null || rewardService.apply(reward)).also { granted ->
                            if (granted) {
                                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.REWARD_GRANTED)
                            }
                        }
                if (granted) onGranted?.invoke()
                onGranted = null
                mutableState.value =
                    if (granted) WebRewardedAdState.RewardGranted else WebRewardedAdState.Error
            }
            AdShowResult.Dismissed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedAdState.Dismissed
            }
            AdShowResult.Unavailable -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedAdState.Unavailable
            }
            is AdShowResult.Failed -> {
                analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                mutableState.value = WebRewardedAdState.Error
            }
        }
    }

    companion object {
        /** The explicit, always-disclosed exchanges of the two placements. */
        val GEM_REWARD = AdRewardDefinition(rewardType = StoreRewardType.GEMS, amount = EconomyPolicy.REWARDED_AD_GEMS)
        val LIFE_REWARD = AdRewardDefinition(rewardType = StoreRewardType.LIFE_RESTORE, amount = EconomyPolicy.REWARDED_AD_LIVES)
    }
}

/**
 * The rewarded placements: +1 gem in the Store, +1 life in the Store and the no-lives dialog, and
 * the second chance after a third mistake, which pays nothing into the wallet.
 */
internal class WebRewardedAds(
    val gems: WebRewardedPlacementController,
    val life: WebRewardedPlacementController,
    val secondChance: WebRewardedPlacementController? = null,
)

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
    /** True once the bound Player owns «no ads»: every transition then continues with no ad. */
    private val adsRemoved: () -> Boolean = { false },
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
        if (runCatching(adsRemoved).getOrDefault(false)) {
            continuation()
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
    /** True once the bound Player owns «no ads»: the banner is then always hidden. */
    private val adsRemoved: () -> Boolean = { false },
) {
    private var desiredVisible: Boolean? = null
    private var appliedVisible: Boolean? = null

    /** Requests a new platform-side visibility; failures remain retryable via [reconcile]. */
    fun applyVisibility(visible: Boolean) {
        desiredVisible = visible && !runCatching(adsRemoved).getOrDefault(false)
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
