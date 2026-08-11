package com.stanisryz.logica.economy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rewarded ad's only economic effect. The ledger, not a UI flag, is what makes one watched ad
 * worth exactly one life, and the wallet is re-read inside the transaction so regeneration that
 * completed while the ad was on screen is kept rather than overwritten.
 */
class RewardedLifeEconomyTest {
    @Test
    fun oneRewardedShowGrantsOneLifeReopensGameplayAndCannotBeClaimedTwice() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))
            assertFalse(dao.wallet(NOW).isGameplayAllowed)

            val granted = dao.grantRewardedLife("ABC", NOW) as EconomyRewardedLife.Granted

            assertTrue(granted.lifeGranted)
            assertEquals(1, granted.economy.lives)
            // The ad costs nothing and never restarts the countdown that was already running.
            assertEquals(0, granted.economy.gems)
            assertEquals(NOW + INTERVAL, granted.economy.nextLifeAtEpochMillis)
            assertTrue(granted.economy.isGameplayAllowed)
            assertEquals(setOf("rewarded_ad:ABC"), dao.events.keys)
            assertEquals(EconomyEventType.REWARDED_AD_LIFE.name, dao.events.getValue("rewarded_ad:ABC").eventType)

            // The same show's reward callback arriving again is a no-op, not a second life.
            val repeated = dao.grantRewardedLife("ABC", NOW)

            assertTrue(repeated is EconomyRewardedLife.AlreadyGranted)
            assertEquals(1, dao.wallet(NOW).lives)
            assertEquals(1, dao.events.size)

            // An ad that started at zero lives while regeneration reached one during the show: the
            // earned life stacks on top of the regenerated one rather than replacing it.
            val raced = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))
            val stacked = raced.grantRewardedLife("DEF", NOW + INTERVAL) as EconomyRewardedLife.Granted

            assertEquals(2, stacked.economy.lives)

            // And the cap still wins if the wallet somehow filled up before the callback arrived.
            val full = FakeEconomyDao(PlayerEconomy(gems = 3, lives = EconomyRules.MAX_LIVES))
            val atCap = full.grantRewardedLife("GHI", NOW) as EconomyRewardedLife.Granted

            // The event is still recorded, so the consumed ad is never owed a life it cannot hold.
            assertFalse(atCap.lifeGranted)
            assertEquals(EconomyRules.MAX_LIVES, atCap.economy.lives)
            assertEquals(3, atCap.economy.gems)
            assertNull(atCap.economy.nextLifeAtEpochMillis)
            assertEquals(1, full.events.size)
            assertEquals(0, full.events.getValue("rewarded_ad:GHI").lifeDelta)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}
