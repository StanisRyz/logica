package com.stanisryz.logica.economy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hints are a consumable stock: spent one per hint, restocked for gems, never negative or doubled. */
class HintInventoryTest {
    @Test
    fun aNewWalletStartsWithAFewHintsAndEachRequestSpendsExactlyOne() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy())
            assertEquals(EconomyRules.STARTING_HINTS, dao.wallet(NOW).hints)

            assertTrue(dao.consumeHint("hint-1", NOW) is EconomyHintUse.Used)
            // The same request repeated (a double tap, a retried call) spends nothing more.
            assertTrue(dao.consumeHint("hint-1", NOW) is EconomyHintUse.AlreadyUsed)
            assertEquals(EconomyRules.STARTING_HINTS - 1, dao.wallet(NOW).hints)
            assertEquals(-1, dao.events.getValue(EconomyEvent.hintUseEventId("hint-1")).hintDelta)

            repeat(EconomyRules.STARTING_HINTS - 1) { dao.consumeHint("more-$it", NOW) }
            assertEquals(0, dao.wallet(NOW).hints)
            assertTrue(dao.consumeHint("empty", NOW) is EconomyHintUse.NoHints)
            assertEquals(0, dao.wallet(NOW).hints)
        }

    @Test
    fun hintsAreBoughtSinglyOrAsAPackForGemsExactlyOnce() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 20, hints = 0))

            val single = dao.buyHintsWithGems("buy-1", HintOffer.SINGLE, NOW)
            assertTrue(single is EconomyHintPurchase.Applied)
            assertTrue(dao.buyHintsWithGems("buy-1", HintOffer.SINGLE, NOW) is EconomyHintPurchase.AlreadyApplied)
            assertTrue(dao.buyHintsWithGems("buy-2", HintOffer.PACK, NOW) is EconomyHintPurchase.Applied)

            val wallet = dao.wallet(NOW)
            assertEquals(HintOffer.SINGLE.hints + HintOffer.PACK.hints, wallet.hints)
            assertEquals(20 - HintOffer.SINGLE.gemCost - HintOffer.PACK.gemCost, wallet.gems)
            assertEquals(HintOffer.SINGLE.hints, dao.events.getValue(EconomyEvent.hintPurchaseEventId("buy-1")).hintDelta)

            // Not enough gems left for a pack: nothing moves.
            assertTrue(dao.buyHintsWithGems("buy-3", HintOffer.PACK, NOW) is EconomyHintPurchase.NotEnoughGems)
            assertEquals(wallet, dao.wallet(NOW))
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
