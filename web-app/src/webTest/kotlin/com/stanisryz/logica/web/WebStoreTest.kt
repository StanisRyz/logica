package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.StoreItem
import com.stanisryz.logica.platform.StoreReward
import com.stanisryz.logica.platform.StoreRewardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.10 regressions: Player-scoped Store state never leaks across Players, a successful
 * purchase consumes gems and grants its inventory reward exactly once, and a failed purchase
 * never consumes anything while still being recorded for future analytics/cloud sync.
 */
class WebStoreTest {
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

    private fun economy(store: FakeEconomyStore): WebPlayerEconomyRepository =
        WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, store).also { it.loadLocal() }

    private fun storeRepository(store: FakeStoreStore): WebPlayerStoreRepository =
        WebPlayerStoreRepository(WebCatalogProgressScope.STANDALONE, store).also { it.loadLocal() }

    @Test
    fun playerScopedInventoryAndHistoryNeverLeakAcrossPlayers() {
        val economyA = economy(FakeEconomyStore())
        val storeA = storeRepository(FakeStoreStore())
        val economyB = economy(FakeEconomyStore())
        val storeB = storeRepository(FakeStoreStore())

        assertTrue(economyA.addGems(100))
        val processor =
            WebStoreProcessor({ economyA }, { storeA }, currentTimeMs = { 1_000L })
        val result =
            processor.purchase(
                StoreItem("hint_pack", 10, StoreReward(StoreRewardType.HINTS, 3)),
                playerId = "player-a",
            )
        assertIs<PurchaseResult.Success>(result)

        // Player A paid and received the reward; Player B keeps the untouched defaults.
        assertEquals(90, economyA.state.value.gems)
        assertEquals(3, storeA.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertEquals(com.stanisryz.logica.platform.EconomyPolicy.STARTING_GEMS, economyB.state.value.gems)
        assertEquals(0, storeB.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // Reloading each scope restores exactly that Player's own durable state.
        assertEquals(
            3,
            storeRepository(FakeStoreStore().also { it.snapshot = storeA.snapshot.value })
                .snapshot.value
                .quantityOf(STORE_INVENTORY_HINTS),
        )
        assertEquals(0, storeB.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        assertNull(storeB.snapshot.value.history.firstOrNull())
    }

    @Test
    fun successfulPurchaseConsumesGemsAndGrantsTheInventoryReward() {
        val economy = economy(FakeEconomyStore())
        val store = storeRepository(FakeStoreStore())
        assertTrue(economy.addGems(100))
        val processor = WebStoreProcessor({ economy }, { store }, currentTimeMs = { 42_000L })

        val item = StoreItem("hint_pack", 20, StoreReward(StoreRewardType.HINTS, 3))
        val result = processor.purchase(item, playerId = null)

        val success = assertIs<PurchaseResult.Success>(result)
        assertEquals(item, success.item)
        assertEquals(STORE_INVENTORY_HINTS, success.inventoryItemId)
        assertEquals(80, economy.state.value.gems)
        assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        val record = store.snapshot.value.history.single()
        assertEquals(PurchaseStatus.SUCCESS, record.status)
        assertEquals(item.id, record.itemId)
        assertEquals(20, record.priceGems)
        assertEquals(42_000L, record.timestampEpochMs)
    }

    @Test
    fun failedPurchaseConsumesNothingAndIsStillRecorded() {
        val economy = economy(FakeEconomyStore())
        val store = storeRepository(FakeStoreStore())
        val processor = WebStoreProcessor({ economy }, { store }, currentTimeMs = { 7_000L })

        // Zero-gem wallet cannot afford the pack.
        val item = StoreItem("hint_pack", 10, StoreReward(StoreRewardType.HINTS, 3))
        val failure = assertIs<PurchaseResult.Failure>(processor.purchase(item, playerId = "player-b"))
        assertEquals(PurchaseStatus.INSUFFICIENT_GEMS, failure.status)
        assertEquals(0, failure.availableGems)

        assertEquals(com.stanisryz.logica.platform.EconomyPolicy.STARTING_GEMS, economy.state.value.gems)
        assertEquals(EconomyPolicy.STARTING_LIVES, economy.state.value.lives)
        assertEquals(0, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

        // The failed attempt is durably recorded for analytics and future cloud sync.
        val record = store.snapshot.value.history.single()
        assertEquals(PurchaseStatus.INSUFFICIENT_GEMS, record.status)
        assertFalse(record.successful)

        // Unknown catalog ids fail without recording any price.
        assertIs<PurchaseResult.Failure>(processor.purchaseById("missing_item"))
    }
}
