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

    @Test
    fun theStreakSaveReadsThePolicy() {
        // The Web streak save has no numbers of its own: the gem price, the shortest streak, and the
        // cooldown are these, exactly as on Android.
        assertEquals(
            listOf(15, 3, 7),
            listOf(EconomyPolicy.STREAK_RESTORE_GEMS, EconomyPolicy.STREAK_RESTORE_MIN_STREAK, EconomyPolicy.STREAK_RESTORE_COOLDOWN_DAYS),
        )
    }

    @Test
    fun theDailyArchiveReadsThePolicy() {
        // The Web archive has no numbers of its own: its window and its price are these, as on Android.
        assertEquals(listOf(30, 5), listOf(EconomyPolicy.DAILY_ARCHIVE_DAYS, EconomyPolicy.DAILY_ARCHIVE_UNLOCK_GEMS))
    }
}
