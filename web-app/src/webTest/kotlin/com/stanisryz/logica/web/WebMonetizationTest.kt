package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdRewardDefinition
import com.stanisryz.logica.platform.AdShowResult
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.MonetizationAnalyticsEvent
import com.stanisryz.logica.platform.StoreRewardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 45.11 regressions: completed ad rewards flow through the Reward Service into Inventory
 * and Economy (never anywhere else), and the ad policy enforces its per-kind cooldowns so ads
 * cannot repeat immediately.
 */
class WebMonetizationTest {
    private class FakeStoreStore : WebStoreStore {
        var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT

        override fun load(): WebStoreSnapshot = snapshot

        override fun save(snapshot: WebStoreSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeEconomyStore : WebEconomyStore {
        var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    @Test
    fun rewardServiceAppliesHintsToInventoryAndLifeRestoresToWallet() {
        val economy =
            WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, FakeEconomyStore())
                .also { it.loadLocal() }
                .also { it.consumeLife() }
        val store =
            WebPlayerStoreRepository(WebCatalogProgressScope.STANDALONE, FakeStoreStore())
                .also { it.loadLocal() }
        val service = WebRewardService({ economy }, { store })

        assertTrue(service.apply(AdRewardDefinition(StoreRewardType.HINTS, 3)))
        assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        assertTrue(service.apply(AdRewardDefinition(StoreRewardType.LIFE_RESTORE, 1)))
        assertEquals(EconomyPolicy.STARTING_LIVES, economy.state.value.lives)
    }

    @Test
    fun rewardedControllerGatesByCooldownAndGrantsRewardsOnlyForCompletedAds() {
        val economy =
            WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, FakeEconomyStore())
                .also { it.loadLocal() }
                .also { it.consumeLife() }
        val store =
            WebPlayerStoreRepository(WebCatalogProgressScope.STANDALONE, FakeStoreStore())
                .also { it.loadLocal() }
        val rewardService = WebRewardService({ economy }, { store })
        val analytics = WebMonetizationAnalytics()
        val policy = WebAdPolicy()
        var now = 100_000L
        var providerResult: AdShowResult = AdShowResult.Completed
        val controller =
            WebRewardedAdController(
                provider = { _, onResult -> onResult(providerResult) },
                policy = policy,
                rewardService = rewardService,
                analytics = analytics,
                currentTimeMs = { now },
            )
        val reward = AdRewardDefinition(StoreRewardType.HINTS, 2)

        // First completed ad grants exactly the defined reward.
        var granted: Boolean? = null
        controller.requestReward(reward) { granted = it }
        assertEquals(true, granted)
        assertEquals(2, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // An immediate second request is blocked by the cooldown and changes nothing.
        providerResult = AdShowResult.Failed("must not be shown")
        controller.requestReward(reward) { granted = it }
        assertEquals(false, granted)
        assertEquals(2, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // After the cooldown a dismissed ad completes without granting anything.
        now += WebAdPolicy.DEFAULT_REWARDED_COOLDOWN_MS
        providerResult = AdShowResult.Dismissed
        controller.requestReward(reward) { granted = it }
        assertEquals(false, granted)
        assertEquals(2, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // After another cooldown a completed ad grants again, with the full event trail.
        now += WebAdPolicy.DEFAULT_REWARDED_COOLDOWN_MS
        providerResult = AdShowResult.Completed
        controller.requestReward(reward) { granted = it }
        assertEquals(true, granted)
        assertEquals(4, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertTrue(analytics.recentEvents.any { it.second == MonetizationAnalyticsEvent.AD_STARTED })
        assertTrue(analytics.recentEvents.any { it.second == MonetizationAnalyticsEvent.REWARD_GRANTED })
        // This test grants hints only; the wallet keeps the single consumed life unchanged.
        assertEquals(EconomyPolicy.STARTING_LIVES - 1, economy.state.value.lives)
    }
}
