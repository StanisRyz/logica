package com.stanisryz.logica.economy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gem-to-life exchange and the gameplay gate. The wallet is always re-read inside the
 * transaction, so neither a stale screen nor a repeated callback can produce an impossible balance.
 */
class EconomyRefillAndGateTest {
    @Test
    fun refillingWithGemsUnblocksGameplayAndKeepsTheRunningCountdown() =
        runBlocking {
            val dao =
                FakeEconomyDao(
                    PlayerEconomy(
                        gems = EconomyRules.LIFE_REFILL_GEM_COST,
                        lives = 0,
                        nextLifeAtEpochMillis = NOW + 4 * MINUTE + 30_000,
                    ),
                )
            assertFalse(dao.wallet(NOW).isGameplayAllowed)

            val refill = dao.refillLifeWithGems("action-1", NOW)

            val wallet = (refill as EconomyRefill.Applied).economy
            assertEquals(0, wallet.gems)
            assertEquals(1, wallet.lives)
            // Buying a life never restarts or cancels regeneration while the wallet is still missing lives.
            assertEquals(NOW + 4 * MINUTE + 30_000, wallet.nextLifeAtEpochMillis)
            assertTrue(wallet.isGameplayAllowed)
            assertEquals(1, dao.events.size)

            // The same purchase action arriving twice is a safe no-op, not a second life.
            val repeated = dao.refillLifeWithGems("action-1", NOW)
            assertEquals(EconomyRefillRejection.ALREADY_APPLIED, (repeated as EconomyRefill.Rejected).reason)
            assertEquals(0, dao.wallet(NOW).gems)
            assertEquals(1, dao.wallet(NOW).lives)
            assertEquals(1, dao.events.size)
        }

    @Test
    fun anUnaffordableOrPointlessRefillChangesNothing() =
        runBlocking {
            // One gem short of the centralized price is still short, whatever that price is.
            assertEquals(10, EconomyRules.LIFE_REFILL_GEM_COST)
            val almost = EconomyRules.LIFE_REFILL_GEM_COST - 1
            val poor = FakeEconomyDao(PlayerEconomy(gems = almost, lives = 0, nextLifeAtEpochMillis = NOW + MINUTE))

            val rejected = poor.refillLifeWithGems("action-1", NOW) as EconomyRefill.Rejected

            assertEquals(EconomyRefillRejection.NOT_ENOUGH_GEMS, rejected.reason)
            assertEquals(almost, poor.wallet(NOW).gems)
            assertEquals(0, poor.wallet(NOW).lives)
            assertEquals(0, poor.events.size)

            val full = FakeEconomyDao(PlayerEconomy(gems = 20, lives = EconomyRules.MAX_LIVES))
            val pointless = full.refillLifeWithGems("action-2", NOW) as EconomyRefill.Rejected

            assertEquals(EconomyRefillRejection.LIVES_FULL, pointless.reason)
            assertEquals(EconomyRules.MAX_LIVES, full.wallet(NOW).lives)
            assertEquals(20, full.wallet(NOW).gems)
            assertNull(full.wallet(NOW).nextLifeAtEpochMillis)
        }

    /** Refreshing a stale wallet is what re-enables a preserved session once a life is back. */
    @Test
    fun aRegeneratedLifeReEnablesGameplayWithoutTouchingTheSession() =
        runBlocking {
            val dao =
                FakeEconomyDao(PlayerEconomy(gems = 1, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))

            assertFalse(dao.refresh(NOW).isGameplayAllowed)

            val refreshed = dao.refresh(NOW + INTERVAL)

            assertTrue(refreshed.isGameplayAllowed)
            assertEquals(1, refreshed.lives)
            assertEquals(NOW + 2 * INTERVAL, refreshed.nextLifeAtEpochMillis)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}
