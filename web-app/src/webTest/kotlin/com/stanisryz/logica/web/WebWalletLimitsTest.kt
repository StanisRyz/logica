package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Stage 2.1: wallet values stay inside their invariants whatever is decoded or added. */
class WebWalletLimitsTest {
    @Test
    fun decodingClampsGemsAndLivesInsteadOfRejectingTheSnapshot() {
        val encoded = WebEconomyCodec.encode(WebEconomySnapshot.DEFAULT.copy(revision = 7L))
        // Gems at offset 5 (big-endian Int), lives in byte 9.
        val tampered = encoded.copyOf()
        tampered[5] = 0x7f
        tampered[6] = 0xff.toByte()
        tampered[7] = 0xff.toByte()
        tampered[8] = 0xff.toByte()
        tampered[9] = 0xff.toByte() // -1 lives
        val decoded = WebEconomyCodec.decode(tampered)!!
        assertEquals(EconomyPolicy.MAX_GEMS, decoded.gems)
        assertEquals(0, decoded.lives)
        assertEquals(7L, decoded.revision)

        val negativeGems = encoded.copyOf().also { it[5] = 0x80.toByte() } // Int.MIN_VALUE-ish
        assertEquals(0, WebEconomyCodec.decode(negativeGems)!!.gems)
        val tooManyLives = encoded.copyOf().also { it[9] = 9 }
        assertEquals(EconomyPolicy.MAXIMUM_LIVES, WebEconomyCodec.decode(tooManyLives)!!.lives)
    }

    @Test
    fun everyGemAdditionSaturatesAtTheMaximum() {
        assertEquals(EconomyPolicy.MAX_GEMS, saturatedGems(EconomyPolicy.MAX_GEMS - 1, 5))
        assertEquals(EconomyPolicy.MAX_GEMS, saturatedGems(EconomyPolicy.MAX_GEMS, Int.MAX_VALUE))

        val store = MemoryEconomyStore(WebEconomySnapshot.DEFAULT.copy(gems = EconomyPolicy.MAX_GEMS - 1))
        val economy = WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, store).also { it.loadLocal() }
        assertTrue(economy.grantGems(10))
        assertEquals(EconomyPolicy.MAX_GEMS, economy.currentSnapshot.gems)
        assertTrue(economy.addGems(Int.MAX_VALUE))
        economy.applyTerminalResult(PuzzleType.SUDOKU, Difficulty.EXPERT, solved = true)
        assertEquals(EconomyPolicy.MAX_GEMS, economy.currentSnapshot.gems)
        assertEquals(EconomyPolicy.MAX_GEMS, store.snapshot.gems)
    }

    @Test
    fun aHintPurchasePastTheStockLimitNeverStarts() {
        val economy =
            WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, MemoryEconomyStore(WebEconomySnapshot.DEFAULT.copy(gems = 100)))
                .also { it.loadLocal() }
        val itemStore = MemoryItemStore(WebStoreSnapshot(inventory = mapOf(STORE_INVENTORY_HINTS to MAX_STORE_INVENTORY - 2)))
        val store = WebPlayerStoreRepository(WebCatalogProgressScope.STANDALONE, itemStore).also { it.loadLocal() }
        val journal = MemoryTransactionJournal()
        val processor = WebStoreProcessor({ economy }, { store }, { 1_000L }, transactionStoreProvider = { journal })

        val pack = WebStoreCatalog.itemById(WebStoreCatalog.ITEM_HINT_PACK)!!
        assertFalse(WebStoreCatalog.fitsInventory(pack, store.snapshot.value))
        val refused = processor.purchase(pack, null)
        assertIs<PurchaseResult.Failure>(refused)
        assertEquals(PurchaseStatus.INVENTORY_FULL, refused.status)
        assertEquals(100, economy.currentSnapshot.gems)
        assertEquals(MAX_STORE_INVENTORY - 2, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertTrue(
            store.snapshot.value.history
                .isEmpty(),
        )
        assertEquals(null, journal.stored)

        // A single hint still fits, twice; then the stock is full.
        val single = WebStoreCatalog.itemById(WebStoreCatalog.ITEM_HINT_SINGLE)!!
        assertIs<PurchaseResult.Success>(processor.purchase(single, null))
        assertIs<PurchaseResult.Success>(processor.purchase(single, null))
        assertEquals(MAX_STORE_INVENTORY, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertEquals(PurchaseStatus.INVENTORY_FULL, (processor.purchase(single, null) as PurchaseResult.Failure).status)

        // A rewarded hint at a full stock grants nothing instead of failing to encode.
        assertFalse(store.grantInventory(STORE_INVENTORY_HINTS, 1))
    }
}
