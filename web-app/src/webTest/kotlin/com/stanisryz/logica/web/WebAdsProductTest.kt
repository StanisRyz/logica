package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdKind
import com.stanisryz.logica.platform.AdShowResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 45.14: the rewarded hint placement grants +3 hints exactly once and only for real
 * completion; the interstitial Next Level continuation runs exactly once under every outcome;
 * fullscreen advertisements force host inactivity and closing re-evaluates real conditions.
 */
class WebAdsProductTest {
    private val standaloneScope = WebCatalogProgressScope.STANDALONE

    private fun rewardService(store: WebPlayerStoreRepository): WebRewardService =
        WebRewardService(
            economyRepository = {
                WebPlayerEconomyRepository(standaloneScope, FakeEconomyStore(), WebPlayerStateRevisions())
                    .also { it.loadLocal() }
            },
            storeRepository = { store },
        )

    private fun hintsController(
        provider: ScriptedRewardedProvider,
        store: WebPlayerStoreRepository,
        policy: WebAdPolicy = WebAdPolicy(),
        gate: WebFullscreenAdGate = WebFullscreenAdGate { true },
    ): WebStoreRewardedHintsController =
        WebStoreRewardedHintsController(
            provider = provider,
            policy = policy,
            rewardService = rewardService(store),
            analytics = WebMonetizationAnalytics(),
            adGate = gate,
            currentTimeMs = { 1_000L },
        )

    // Test 1: rewarded completion grants +3 exactly once; every other outcome grants nothing.
    @Test
    fun rewardedHintsGrantExactlyOncePerSession() {
        val revisions = WebPlayerStateRevisions()
        val itemStoreFake = FakePlayerItemStore()
        val store =
            WebPlayerStoreRepository(standaloneScope, itemStoreFake, revisions).also { it.loadLocal() }

        // Completed (rewarded + close combined by the provider) grants exactly +3 hints once;
        // duplicate terminal callbacks are ignored.
        val completedProvider = ScriptedRewardedProvider()
        val completedGate = WebFullscreenAdGate { true }
        val completedController = hintsController(completedProvider, store, gate = completedGate)
        completedController.requestReward()
        assertEquals(WebRewardedHintState.Showing, completedController.state.value)
        assertTrue(completedGate.adShowing.value)
        assertEquals(1, completedProvider.showCalls)
        completedProvider.onResult?.invoke(AdShowResult.Completed)
        completedProvider.onResult?.invoke(AdShowResult.Completed) // duplicate SDK callback
        assertEquals(WebRewardedHintState.RewardGranted, completedController.state.value)
        assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertTrue(store.snapshot.value.revision > 0L) // one ordinary durable Store mutation
        assertFalse(completedGate.adShowing.value)

        // Dismissed / Failed / Unavailable grant nothing.
        val before = store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS)

        val dismissedProvider = ScriptedRewardedProvider()
        val dismissedController = hintsController(dismissedProvider, store)
        dismissedController.requestReward()
        dismissedProvider.onResult?.invoke(AdShowResult.Dismissed)
        assertEquals(WebRewardedHintState.Dismissed, dismissedController.state.value)

        val failedProvider = ScriptedRewardedProvider()
        val failedController = hintsController(failedProvider, store)
        failedController.requestReward()
        failedProvider.onResult?.invoke(AdShowResult.Failed("no fill"))
        assertEquals(WebRewardedHintState.Error, failedController.state.value)

        val unavailableProvider = ScriptedRewardedProvider()
        val unavailableController = hintsController(unavailableProvider, store)
        unavailableController.requestReward()
        unavailableProvider.onResult?.invoke(AdShowResult.Unavailable)
        assertEquals(WebRewardedHintState.Unavailable, unavailableController.state.value)

        assertEquals(before, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // Double tap while an ad session is active is ignored (one session per placement).
        val doubleTapProvider = ScriptedRewardedProvider()
        val doubleTapController = hintsController(doubleTapProvider, store)
        doubleTapController.requestReward()
        doubleTapController.requestReward() // ignored: session already showing
        assertEquals(1, doubleTapProvider.showCalls)
        doubleTapProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(before + 3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // Policy cooldown reports Cooldown without starting an ad.
        val cooldownPolicy = WebAdPolicy()
        val cooldownGate = WebFullscreenAdGate { true }
        val cooldownProvider = ScriptedRewardedProvider()
        val cooldownController =
            hintsController(cooldownProvider, store, policy = cooldownPolicy, gate = cooldownGate)
        cooldownController.requestReward()
        cooldownProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(before + 6, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        cooldownController.requestReward() // still inside the rewarded cooldown window
        assertEquals(WebRewardedHintState.Cooldown, cooldownController.state.value)
        assertFalse(cooldownGate.adShowing.value)
    }

    // Test 2: the Next Level continuation runs exactly once under every interstitial outcome.
    @Test
    fun interstitialContinuationRunsExactlyOnce() {
        fun controller(
            provider: ScriptedInterstitialProvider,
            policy: WebAdPolicy = WebAdPolicy(),
        ): WebInterstitialContinuationController =
            WebInterstitialContinuationController(
                provider = provider,
                policy = policy,
                analytics = WebMonetizationAnalytics(),
                adGate = WebFullscreenAdGate { true },
                currentTimeMs = { 1_000L },
            )

        // Completed then Failed (duplicate terminal callbacks): one continuation only.
        val completedProvider = ScriptedInterstitialProvider()
        val completedGate = WebFullscreenAdGate { true }
        val completedController =
            WebInterstitialContinuationController(
                provider = completedProvider,
                policy = WebAdPolicy(),
                analytics = WebMonetizationAnalytics(),
                adGate = completedGate,
                currentTimeMs = { 1_000L },
            )
        var continuations = 0
        completedController.runWithInterstitial("catalog_next_level_interstitial") { continuations++ }
        assertEquals(1, completedProvider.showCalls)
        assertTrue(completedGate.adShowing.value)
        completedProvider.onResult?.invoke(AdShowResult.Completed)
        completedProvider.onResult?.invoke(AdShowResult.Failed("late error"))
        assertEquals(1, continuations)
        assertFalse(completedGate.adShowing.value)

        // Unavailable / Dismissed each continue exactly once.
        for (outcome in listOf(AdShowResult.Unavailable, AdShowResult.Dismissed)) {
            val provider = ScriptedInterstitialProvider()
            val interstitial = controller(provider)
            var ran = 0
            interstitial.runWithInterstitial("catalog_next_level_interstitial") { ran++ }
            provider.onResult?.invoke(outcome)
            assertEquals(1, ran, "outcome=$outcome")
        }

        // Policy denial skips the ad entirely and continues immediately.
        val deniedPolicy = WebAdPolicy()
        deniedPolicy.markShown(AdKind.INTERSTITIAL, nowMs = 0L)
        val deniedController = controller(ScriptedInterstitialProvider(), policy = deniedPolicy)
        var deniedRuns = 0
        deniedController.runWithInterstitial("catalog_next_level_interstitial") { deniedRuns++ }
        assertEquals(1, deniedRuns)

        // A request during an in-flight attempt never queues a second transition.
        val inFlightProvider = ScriptedInterstitialProvider()
        val inFlightController = controller(inFlightProvider)
        var inFlightRuns = 0
        inFlightController.runWithInterstitial("catalog_next_level_interstitial") { inFlightRuns++ }
        inFlightController.runWithInterstitial("catalog_next_level_interstitial") { inFlightRuns++ }
        assertEquals(1, inFlightProvider.showCalls)
        inFlightProvider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(2, inFlightRuns) // first transition + immediate skip of the second request
    }

    // Test 3: fullscreen ads force inactivity; closing re-evaluates real host conditions.
    @Test
    fun fullscreenAdsPauseHostAndResumeOnlyWhenEligible() {
        var hostEligible = true
        val gate = WebFullscreenAdGate({ hostEligible })
        assertTrue(gate.isActive.value)
        assertFalse(gate.adShowing.value)

        // Ad open -> gameplay/lifecycle becomes inactive even though the host is eligible.
        gate.setAdShowing(true)
        assertTrue(gate.adShowing.value)
        assertFalse(gate.isActive.value)

        // Close while the page is hidden/unfocused -> stays inactive.
        hostEligible = false
        gate.setAdShowing(false)
        assertFalse(gate.adShowing.value)
        assertFalse(gate.isActive.value)

        // Close while the page is focused/visible -> normal active state can resume.
        hostEligible = true
        gate.setAdShowing(false)
        assertTrue(gate.isActive.value)

        // Host signals changing without an ad (visibility/focus/Yandex events) re-evaluate.
        hostEligible = false
        gate.refresh()
        assertFalse(gate.isActive.value)
        hostEligible = true
        gate.refresh()
        assertTrue(gate.isActive.value)
    }

    private class ScriptedRewardedProvider : RewardedAdProvider {
        var showCalls = 0
        var onResult: ((AdShowResult) -> Unit)? = null

        override fun show(onResult: (AdShowResult) -> Unit) {
            showCalls += 1
            this.onResult = onResult
        }
    }

    private class ScriptedInterstitialProvider : InterstitialAdProvider {
        var showCalls = 0
        var onResult: ((AdShowResult) -> Unit)? = null

        override fun show(onResult: (AdShowResult) -> Unit) {
            showCalls += 1
            this.onResult = onResult
        }
    }

    private class FakeEconomyStore(
        private var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT,
    ) : WebEconomyStore {
        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakePlayerItemStore(
        private var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT,
    ) : WebStoreStore {
        override fun load(): WebStoreSnapshot = snapshot

        override fun save(snapshot: WebStoreSnapshot) {
            this.snapshot = snapshot
        }
    }
}
