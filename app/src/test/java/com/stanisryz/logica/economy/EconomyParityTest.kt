package com.stanisryz.logica.economy

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.ui.components.StarterPackContents
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android reads every economy number from the shared [EconomyPolicy] (Web's `WebEconomyParityTest`
 * checks the same policy), and the policy itself still holds today's numbers.
 */
class EconomyParityTest {
    @Test
    fun thePolicyKeepsTodaysNumbers() {
        assertEquals(
            listOf(10, 5, 5, 1, 3, 10, 4, 3, 10, 1, 1, 50, 150, 500, 100, 5),
            listOf(
                EconomyPolicy.STARTING_GEMS,
                EconomyPolicy.STARTING_LIVES,
                EconomyPolicy.MAXIMUM_LIVES,
                EconomyPolicy.FAILED_ATTEMPT_LIFE_COST,
                EconomyPolicy.STARTING_HINTS,
                EconomyPolicy.LIFE_REFILL_GEM_COST,
                EconomyPolicy.HINT_SINGLE_GEM_COST,
                EconomyPolicy.HINT_PACK_SIZE,
                EconomyPolicy.HINT_PACK_GEM_COST,
                EconomyPolicy.REWARDED_AD_GEMS,
                EconomyPolicy.REWARDED_AD_LIVES,
                EconomyPolicy.GEM_PACK_SMALL,
                EconomyPolicy.GEM_PACK_MEDIUM,
                EconomyPolicy.GEM_PACK_LARGE,
                EconomyPolicy.STARTER_PACK_GEMS,
                EconomyPolicy.STARTER_PACK_HINTS,
            ),
        )
        assertEquals(30L * 60L * 1000L, EconomyPolicy.LIFE_RESTORE_INTERVAL_MS)
    }

    @Test
    fun androidRulesPacksAndTheStarterPackComeFromThePolicy() {
        assertEquals(EconomyPolicy.STARTING_GEMS, EconomyRules.STARTING_GEMS)
        assertEquals(EconomyPolicy.STARTING_LIVES, EconomyRules.STARTING_LIVES)
        assertEquals(EconomyPolicy.MAXIMUM_LIVES, EconomyRules.MAX_LIVES)
        assertEquals(EconomyPolicy.STARTING_HINTS, EconomyRules.STARTING_HINTS)
        assertEquals(EconomyPolicy.LIFE_RESTORE_INTERVAL_MS, EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS)
        assertEquals(EconomyPolicy.LIFE_REFILL_GEM_COST, EconomyRules.LIFE_REFILL_GEM_COST)
        assertEquals(EconomyPolicy.REWARDED_AD_GEMS, EconomyRules.REWARDED_AD_GEMS)
        assertEquals(
            listOf(1 to EconomyPolicy.HINT_SINGLE_GEM_COST, EconomyPolicy.HINT_PACK_SIZE to EconomyPolicy.HINT_PACK_GEM_COST),
            HintOffer.entries.map { it.hints to it.gemCost },
        )
        assertEquals(
            listOf(EconomyPolicy.GEM_PACK_SMALL, EconomyPolicy.GEM_PACK_MEDIUM, EconomyPolicy.GEM_PACK_LARGE),
            GemPack.CATALOG.map { it.gems },
        )
        // What the shared card shows is what the purchase grants.
        assertEquals(StarterPackContents.GEMS to StarterPackContents.HINTS, GemPack.STARTER_PACK.gems to GemPack.STARTER_PACK.hints)
        assertEquals(
            EconomyPolicy.STARTER_PACK_GEMS to EconomyPolicy.STARTER_PACK_HINTS,
            StarterPackContents.GEMS to StarterPackContents.HINTS,
        )
    }
}
