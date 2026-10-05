package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.StoreRewardType
import com.stanisryz.logica.ui.components.StarterPackContents
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Web Store, rewarded ads, and paid packs read the shared [EconomyPolicy], like Android. */
class WebEconomyParityTest {
    @Test
    fun theStoreAdsAndPacksComeFromThePolicy() {
        assertEquals(
            listOf(
                Triple(WebStoreCatalog.ITEM_HINT_SINGLE, EconomyPolicy.HINT_SINGLE_GEM_COST, 1),
                Triple(WebStoreCatalog.ITEM_HINT_PACK, EconomyPolicy.HINT_PACK_GEM_COST, EconomyPolicy.HINT_PACK_SIZE),
                Triple(WebStoreCatalog.ITEM_LIFE_RESTORE, EconomyPolicy.LIFE_REFILL_GEM_COST, 1),
            ),
            WebStoreCatalog.ITEMS.map { Triple(it.id, it.priceGems, it.reward.amount) },
        )
        assertEquals(
            StoreRewardType.GEMS to EconomyPolicy.REWARDED_AD_GEMS,
            WebRewardedPlacementController.GEM_REWARD.let {
                it.rewardType to
                    it.amount
            },
        )
        assertEquals(
            StoreRewardType.LIFE_RESTORE to EconomyPolicy.REWARDED_AD_LIVES,
            WebRewardedPlacementController.LIFE_REWARD.let { it.rewardType to it.amount },
        )
        assertEquals(
            listOf(EconomyPolicy.GEM_PACK_SMALL, EconomyPolicy.GEM_PACK_MEDIUM, EconomyPolicy.GEM_PACK_LARGE),
            WebPaidProduct.GEM_PACKS.map { it.gemReward },
        )
        // What the shared card shows is what the purchase grants.
        assertEquals(
            StarterPackContents.GEMS to StarterPackContents.HINTS,
            WebPaidProduct.STARTER_PACK.gemReward to WebPaidProduct.STARTER_PACK.hintReward,
        )
        assertEquals(
            EconomyPolicy.STARTER_PACK_GEMS to EconomyPolicy.STARTER_PACK_HINTS,
            StarterPackContents.GEMS to StarterPackContents.HINTS,
        )
    }
}
