package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdShowResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 45.14a hardening: rewarded sessions carry runtime identity and a captured Player
 * context (stale callbacks and account switches can never grant); interstitial double taps are
 * ignored while one transition owns navigation; cooldowns start only after actual exposure;
 * fullscreen ads drive the effective lifecycle; sticky banner desired/applied state reconciles.
 */
class WebAdsProductTest {
    private val standaloneScope = WebCatalogProgressScope.STANDALONE

    private class RecordingFullscreenActivity : WebFullscreenAdActivity {
        var active = false

        override fun setFullscreenAdActive(active: Boolean) {
            this.active = active
        }
    }

    private class ScriptedRewardedProvider : RewardedAdProvider {
        var showCalls = 0
        var onOpened: (() -> Unit)? = null
        var onResult: ((AdShowResult) -> Unit)? = null

        override fun show(
            onOpened: () -> Unit,
            onResult: (AdShowResult) -> Unit,
        ) {
            showCalls += 1
            this.onOpened = onOpened
            this.onResult = onResult
        }
    }

    private class ScriptedInterstitialProvider : InterstitialAdProvider {
        var showCalls = 0
        var onOpened: (() -> Unit)? = null
        var onResult: ((AdShowResult) -> Unit)? = null

        override fun show(
            onOpened: () -> Unit,
            onResult: (AdShowResult) -> Unit,
        ) {
            showCalls += 1
            this.onOpened = onOpened
            this.onResult = onResult
        }
    }

    private class FakeEconomyStore : WebEconomyStore {
        var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakePlayerItemStore : WebStoreStore {
        var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT

        override fun load(): WebStoreSnapshot = snapshot

        override fun save(snapshot: WebStoreSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeStickyBannerBridge : WebStickyBannerBridge {
        var showCalls = 0
        var hideCalls = 0
        var showSucceeds = true
        var platformShowing: Boolean? = null

        override fun showStickyBanner(): Boolean {
            showCalls += 1
            if (showSucceeds) platformShowing = true
            return showSucceeds
        }

        override fun hideStickyBanner(): Boolean {
            hideCalls += 1
            platformShowing = false
            return true
        }

        override suspend fun stickyBannerStatus(): Boolean? = platformShowing
    }

    private fun hintsStore(): WebPlayerStoreRepository =
        WebPlayerStoreRepository(standaloneScope, FakePlayerItemStore(), WebPlayerStateRevisions())
            .also { it.loadLocal() }

    private fun rewardedController(
        provider: ScriptedRewardedProvider,
        store: WebPlayerStoreRepository,
        policy: WebAdPolicy = WebAdPolicy(),
        activity: RecordingFullscreenActivity = RecordingFullscreenActivity(),
        context: () -> WebPlayerContextToken?,
        currentTimeMs: () -> Long = { 1_000L },
    ): WebStoreRewardedHintsController =
        WebStoreRewardedHintsController(
            provider = provider,
            policy = policy,
            rewardService =
                WebRewardService(
                    economyRepository = {
                        WebPlayerEconomyRepository(standaloneScope, FakeEconomyStore(), WebPlayerStateRevisions())
                            .also { it.loadLocal() }
                    },
                    storeRepository = { store },
                ),
            analytics = WebMonetizationAnalytics(),
            fullscreenAdActivity = activity,
            currentPlayerContext = context,
            currentTimeMs = currentTimeMs,
        )

    // Test 1: stale session callbacks and account switches can never grant hints.
    @Test
    fun rewardedStaleCallbacksAndPlayerSwitchesCannotGrant() {
        val store = hintsStore()
        val providerA = ScriptedRewardedProvider()
        val contextToken = WebPlayerContextToken(1L)
        var now = 1_000L
        val controller =
            rewardedController(providerA, store, context = { contextToken }, currentTimeMs = { now })

        // Session A starts, really opens, and terminates normally: exactly +3 hints once.
        controller.requestReward()
        assertEquals(WebRewardedHintState.Showing, controller.state.value)
        providerA.onOpened?.invoke() // real exposure: the rewarded cooldown begins HERE
        providerA.onResult?.invoke(AdShowResult.Completed)
        assertTrue(
            controller.state.value == WebRewardedHintState.RewardGranted,
            "A: state=${controller.state.value} hints=${store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS)}",
        )
        assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        val lateSessionACallback = checkNotNull(providerA.onResult)

        // An immediate second request sits inside the exposure-based cooldown window.
        controller.requestReward()
        assertEquals(WebRewardedHintState.Cooldown, controller.state.value)

        // After the cooldown Session B starts normally through the same placement: the
        // controller stamps this invocation with a NEW runtime session id.
        now += WebAdPolicy.DEFAULT_REWARDED_COOLDOWN_MS
        controller.requestReward()
        assertEquals(WebRewardedHintState.Showing, controller.state.value)
        assertFalse(controller.isRequestAllowed)
        val sessionBHandler = checkNotNull(providerA.onResult) // fresh handler for session B
        val sessionBOpened = checkNotNull(providerA.onOpened)

        // A late callback from session A (captured before B started) is ignored: it can
        // neither finish nor grant for the newer session.
        lateSessionACallback.invoke(AdShowResult.Completed)
        assertEquals(WebRewardedHintState.Showing, controller.state.value) // B untouched by A
        assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // Session B completes for the still-current context: grants exactly once.
        sessionBOpened.invoke()
        sessionBHandler.invoke(AdShowResult.Completed)
        assertTrue(
            controller.state.value == WebRewardedHintState.RewardGranted,
            "B: state=${controller.state.value} hints=${store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS)}",
        )
        assertEquals(6, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // Rewarded starts while Player A is bound; the context switches to Player B before
        // completion: Player B receives nothing and the old session finishes without granting.
        var currentContext: WebPlayerContextToken? = contextToken
        val switchableProvider = ScriptedRewardedProvider()
        val switchableController = rewardedController(switchableProvider, store, context = { currentContext })
        switchableController.requestReward()
        currentContext = WebPlayerContextToken(2L) // account switched mid-session
        switchableProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(WebRewardedHintState.Error, switchableController.state.value)
        assertEquals(6, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS)) // no +3 anywhere

        // The finished old session cannot grant afterwards either.
        switchableProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(6, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
    }

    // Test 2: double tap during an active interstitial is ignored; continuation runs exactly once.
    @Test
    fun interstitialDoubleTapIsIgnoredAndContinuationRunsExactlyOnce() {
        val provider = ScriptedInterstitialProvider()
        val activity = RecordingFullscreenActivity()
        val controller =
            WebInterstitialContinuationController(
                provider = provider,
                policy = WebAdPolicy(),
                analytics = WebMonetizationAnalytics(),
                fullscreenAdActivity = activity,
                currentTimeMs = { 1_000L },
            )
        var continuations = 0

        // First Next Level request starts exactly one transition/ad attempt.
        controller.runWithInterstitial("catalog_next_level_interstitial") { continuations++ }
        assertEquals(1, provider.showCalls)
        assertEquals(0, continuations) // navigation waits for the terminal result
        provider.onOpened?.invoke() // platform actually opens -> fullscreen activity + cooldown
        assertTrue(activity.active)

        // Second tap while the transition is active is fully ignored.
        controller.runWithInterstitial("catalog_next_level_interstitial") { continuations++ }
        assertEquals(1, provider.showCalls)

        // Terminal result executes the ORIGINAL continuation exactly once.
        provider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(1, continuations)
        assertFalse(activity.active)

        // Duplicate/late terminal callbacks execute nothing further.
        provider.onResult?.invoke(AdShowResult.Failed("late error"))
        provider.onResult?.invoke(AdShowResult.Dismissed)
        assertEquals(1, continuations)

        // Cooldown starts only after actual exposure: an Unavailable attempt (never opened)
        // leaves the policy free for the next request.
        val freshPolicy = WebAdPolicy()
        val unavailableProvider = ScriptedInterstitialProvider()
        val unavailableController =
            WebInterstitialContinuationController(
                provider = unavailableProvider,
                policy = freshPolicy,
                analytics = WebMonetizationAnalytics(),
                fullscreenAdActivity = RecordingFullscreenActivity(),
                currentTimeMs = { 1_000L },
            )
        var unavailableRuns = 0
        unavailableController.runWithInterstitial("catalog_next_level_interstitial") { unavailableRuns++ }
        unavailableProvider.onResult?.invoke(AdShowResult.Unavailable)
        assertEquals(1, unavailableRuns)

        val nextProvider = ScriptedInterstitialProvider()
        val nextController =
            WebInterstitialContinuationController(
                provider = nextProvider,
                policy = freshPolicy,
                analytics = WebMonetizationAnalytics(),
                fullscreenAdActivity = RecordingFullscreenActivity(),
                currentTimeMs = { 2_000L },
            )
        var nextRuns = 0
        nextController.runWithInterstitial("catalog_next_level_interstitial") { nextRuns++ }
        assertEquals(1, nextProvider.showCalls) // not blocked by the never-opened attempt
        nextProvider.onOpened?.invoke() // platform actually opens -> cooldown starts here
        nextProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(1, nextRuns)

        // A later request inside that cooldown window is denied and navigates without an ad.
        val deniedProvider = ScriptedInterstitialProvider()
        val deniedController =
            WebInterstitialContinuationController(
                provider = deniedProvider,
                policy = freshPolicy,
                analytics = WebMonetizationAnalytics(),
                fullscreenAdActivity = RecordingFullscreenActivity(),
                currentTimeMs = { 3_000L },
            )
        var deniedRuns = 0
        deniedController.runWithInterstitial("catalog_next_level_interstitial") { deniedRuns++ }
        assertEquals(0, deniedProvider.showCalls)
        assertEquals(1, deniedRuns)
    }

    // Test 3: effective lifecycle + sticky-banner desired/applied reconciliation.
    @Test
    fun effectiveLifecycleAndBannerReconciliationBehaveCorrectly() {
        // Effective lifecycle rule: fullscreen ads force INACTIVE even when everything else
        // allows ACTIVE; closing re-evaluates real visibility/focus instead of forcing ACTIVE.
        fun effective(
            fullscreenAdActive: Boolean,
            visible: Boolean,
            focused: Boolean,
        ): Boolean =
            WebEffectiveLifecycle.isActive(
                started = true,
                yandexPaused = false,
                fullscreenAdActive = fullscreenAdActive,
                browserVisible = visible,
                browserFocused = focused,
            )
        assertTrue(effective(fullscreenAdActive = false, visible = true, focused = true))
        assertFalse(effective(fullscreenAdActive = true, visible = true, focused = true))
        assertFalse(effective(fullscreenAdActive = false, visible = false, focused = true))
        assertFalse(effective(fullscreenAdActive = false, visible = true, focused = false))

        // Sticky banner desired/applied reconciliation (event-driven, never polled).
        runTest {
            val bridge = FakeStickyBannerBridge()
            val banner = WebStickyBannerController(bridge)

            // A failed show stays unapplied and remains retryable by later reconciliation.
            bridge.showSucceeds = false
            banner.applyVisibility(true)
            assertEquals(1, bridge.showCalls)
            banner.reconcile()
            assertEquals(2, bridge.showCalls)

            // Status healing: the platform reports it shows the banner anyway (transient
            // error), so reconciliation marks it applied WITHOUT another SDK call.
            bridge.platformShowing = true
            banner.reconcileUsingPlatformStatus()
            assertEquals(2, bridge.showCalls)

            // Applied == desired: repeated requests stop touching the SDK entirely.
            banner.reconcile()
            assertEquals(2, bridge.showCalls)

            // Hiding succeeds once and repeated identical hides are suppressed.
            banner.applyVisibility(false)
            assertEquals(1, bridge.hideCalls)
            banner.applyVisibility(false)
            assertEquals(1, bridge.hideCalls)
        }
    }
}
