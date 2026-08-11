package com.stanisryz.logica.economy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a paid purchase does to the wallet. RuStore's purchase ID is the ledger key, so the same
 * payment arriving again — from a repeated callback, from reconciliation, or from a finalization
 * retry — is worth nothing the second time.
 */
class GemPurchaseEconomyTest {
    @Test
    fun oneConfirmedPurchaseGrantsItsPackOnceAndAnUnknownProductGrantsNothing() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 7, lives = 3, nextLifeAtEpochMillis = NOW + INTERVAL))

            val granted = dao.grantPurchasedGems("XYZ", "gems_250", NOW) as EconomyGemPurchase.Granted

            assertEquals(GemPack.GEMS_250, granted.pack)
            assertEquals(7 + 250, granted.economy.gems)
            // A purchase buys gems and only gems: lives and the running countdown are untouched.
            assertEquals(3, granted.economy.lives)
            assertEquals(NOW + INTERVAL, granted.economy.nextLifeAtEpochMillis)
            assertEquals(setOf("rustore:XYZ"), dao.events.keys)
            assertEquals(EconomyEventType.RUSTORE_GEM_PURCHASE.name, dao.events.getValue("rustore:XYZ").eventType)
            assertEquals(0, dao.events.getValue("rustore:XYZ").lifeDelta)

            // The same payment again adds nothing, whatever brought it back.
            val repeated = dao.grantPurchasedGems("XYZ", "gems_250", NOW)

            assertTrue(repeated is EconomyGemPurchase.AlreadyGranted)
            assertEquals(7 + 250, dao.wallet(NOW).gems)
            assertEquals(1, dao.events.size)

            // A product this build has no reward for is worth exactly nothing; nothing is inferred
            // from the ID, and no ledger row is written that would consume the purchase.
            val unknown = dao.grantPurchasedGems("ABC", "gems_9000", NOW) as EconomyGemPurchase.UnsupportedProduct

            assertEquals("gems_9000", unknown.productId)
            assertEquals(7 + 250, dao.wallet(NOW).gems)
            assertEquals(3, dao.wallet(NOW).lives)
            assertEquals(1, dao.events.size)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}
