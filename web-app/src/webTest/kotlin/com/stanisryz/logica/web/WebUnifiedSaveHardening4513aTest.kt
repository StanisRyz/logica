package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PurchaseRecord
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.13a hardening: every real Store mutation receives a fresh Player-scoped revision,
 * an interrupted coupled purchase recovers idempotently from its durable journal, external
 * Economy/Store restore stays durable-first and pair-consistent, and unified ownership becomes
 * active only after a successful canonical write with bounded transient retries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebUnifiedSaveHardening4513aTest {
    private val standaloneScope = WebCatalogProgressScope.STANDALONE

    // Test 1: fresh revisions per real mutation; timelines never transfer between Players.
    @Test
    fun storeMutationsStampFreshPlayerScopedRevisions() =
        runTest {
            val revisionsA = WebPlayerStateRevisions()
            val economyA =
                WebPlayerEconomyRepository(standaloneScope, FakeEconomyStore(startingGems = 30), revisionsA)
                    .also { it.loadLocal() }
            val storeA =
                WebPlayerStoreRepository(standaloneScope, FakePlayerItemStore(), revisionsA).also { it.loadLocal() }

            // Initial state carries revision 0; no-op mutations never allocate one.
            assertEquals(0L, storeA.snapshot.value.revision)
            assertFalse(storeA.consumeInventory("absent_item"))
            assertEquals(0L, storeA.snapshot.value.revision)

            // Every actual durable change gets exactly one new revision.
            assertTrue(storeA.grantInventory(STORE_INVENTORY_HINTS, 2))
            assertEquals(1L, storeA.snapshot.value.revision)
            assertTrue(storeA.consumeInventory(STORE_INVENTORY_HINTS))
            assertEquals(2L, storeA.snapshot.value.revision)
            storeA.recordPurchase(PurchaseRecord(WebStoreCatalog.ITEM_HINT_PACK, 10, 5L, PurchaseStatus.SUCCESS))
            assertEquals(3L, storeA.snapshot.value.revision)

            // Player A's shared timeline keeps climbing through Economy mutations as well.
            repeat(7) { assertTrue(economyA.addGems(1)) }
            assertEquals(10L, economyA.currentSnapshot.revision)

            // Player B binds a FRESH context: their stored revision is 5, so the next B
            // mutation must become 6 — never an A-derived value like 11.
            val revisionsB = WebPlayerStateRevisions()
            WebPlayerEconomyRepository(standaloneScope, FakeEconomyStore(), revisionsB).also { it.loadLocal() }
            val seededStoreB =
                FakePlayerItemStore(
                    WebStoreSnapshot(inventory = mapOf(STORE_INVENTORY_HINTS to 1), revision = 5L),
                )
            val storeB =
                WebPlayerStoreRepository(standaloneScope, seededStoreB, revisionsB).also { it.loadLocal() }
            assertEquals(5L, storeB.snapshot.value.revision)
            assertTrue(storeB.consumeInventory(STORE_INVENTORY_HINTS))
            assertEquals(6L, storeB.snapshot.value.revision)
        }

    // Test 2: an interrupted coupled purchase recovers idempotently from its journal.
    @Test
    fun interruptedPurchaseRecoversExactlyOnceFromJournal() =
        runTest {
            val revisions = WebPlayerStateRevisions()
            val economyStoreFake = FakeEconomyStore(startingGems = 30)
            val economy =
                WebPlayerEconomyRepository(standaloneScope, economyStoreFake, revisions).also { it.loadLocal() }
            val store =
                WebPlayerStoreRepository(standaloneScope, FakePlayerItemStore(), revisions).also { it.loadLocal() }
            val journal = FakePurchaseTransactionStore()

            // The transaction mirrors one hint-pack purchase: one shared revision, absolute
            // final targets on both sides.
            val revision = revisions.next()
            val targetEconomy = economy.currentSnapshot.copy(gems = 20, revision = revision)
            val targetStore =
                store.snapshot.value.copy(
                    inventory = mapOf(STORE_INVENTORY_HINTS to 3),
                    history = listOf(PurchaseRecord(WebStoreCatalog.ITEM_HINT_PACK, 10, 1_000L, PurchaseStatus.SUCCESS)),
                    revision = revision,
                )
            val transaction =
                WebPurchaseTransaction(
                    id = "tx-1",
                    revision = revision,
                    itemId = WebStoreCatalog.ITEM_HINT_PACK,
                    priceGems = 10,
                    targetEconomy = targetEconomy,
                    targetStore = targetStore,
                )
            // The journaled transaction survives storage round-trips unchanged.
            assertEquals(transaction, WebPurchaseTransactionCodec.decode(WebPurchaseTransactionCodec.encode(transaction)))

            journal.save(transaction)
            // Crash simulation: journal + Economy target are durable; Store never happened.
            assertEquals(WebExternalRestoreResult.Applied, economy.applyExternal(targetEconomy))
            assertEquals(20, economy.currentSnapshot.gems)
            assertEquals(0, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))

            // Next bind: recovery finishes the remaining side and clears the journal.
            assertTrue(WebPurchaseTransactionRecovery.recover(transaction, economy, store, journal))
            assertEquals(20, economy.currentSnapshot.gems)
            assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
            assertEquals(1, store.snapshot.value.history.size)
            assertNull(journal.load())

            // Repeated recovery is idempotent: no double deduction, grant, or record.
            assertTrue(WebPurchaseTransactionRecovery.recover(transaction, economy, store, journal))
            assertEquals(20, economy.currentSnapshot.gems)
            assertEquals(3, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
            assertEquals(1, store.snapshot.value.history.size)
        }

    // Test 3: failed pair restore keeps the previous pair; establishment needs a real success.
    @Test
    fun restoreFailureKeepsLocalPairAndEstablishmentRetriesBounded() =
        runTest {
            // A newer cloud Economy/Store pair where one local side cannot be persisted:
            val revisions = WebPlayerStateRevisions()
            val economyStoreFake = FakeEconomyStore(startingGems = 0)
            val economy =
                WebPlayerEconomyRepository(standaloneScope, economyStoreFake, revisions).also { it.loadLocal() }
            assertTrue(economy.addGems(5))
            val previousGems = economy.currentSnapshot.gems
            val store =
                WebPlayerStoreRepository(standaloneScope, FailingPlayerItemStore(), revisions).also { it.loadLocal() }

            val cloudEconomy = economy.currentSnapshot.copy(gems = 100, revision = 9L)
            val cloudStore = store.snapshot.value.copy(inventory = mapOf(STORE_INVENTORY_HINTS to 3), revision = 9L)

            // Neither externally restored side becomes observable; the previous local pair
            // (including the rolled-back wallet) stays authoritative.
            assertFalse(WebEconomyStorePairApply.apply(economy, store, cloudEconomy, cloudStore))
            assertEquals(previousGems, economy.currentSnapshot.gems)
            assertEquals(0, store.snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
            assertEquals(1L, economy.currentSnapshot.revision)

            // Unified establishment: ACTIVE/SYNCED only after a real successful write.
            var failWrites = true
            var writes = 0
            val repository =
                object : SaveRepository {
                    override suspend fun load(): SaveData? = null

                    override suspend fun save(data: SaveData): Boolean {
                        writes += 1
                        return !failWrites
                    }
                }
            val scheduler =
                WebUnifiedSaveScheduler(
                    saveManager = WebSaveManager(listOf(ProbeSection()), repository),
                    scope = this,
                    isTokenCurrent = { true },
                )
            scheduler.restoreAndEstablish(WebPlayerContextToken(42L))
            assertFalse(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)

            // First bounded retry (~2s) still fails.
            advanceTimeBy(2_500)
            runCurrent()
            assertFalse(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.ERROR, scheduler.saveStatus.value)

            // The second bounded retry (~8s) succeeds and only now activates ownership.
            failWrites = false
            advanceUntilIdle()
            assertTrue(scheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.SYNCED, scheduler.saveStatus.value)

            // Bounded: a permanently failing context stops after the initial + two retries.
            var alwaysFailingWrites = 0
            val failingScheduler =
                WebUnifiedSaveScheduler(
                    saveManager =
                        WebSaveManager(
                            listOf(ProbeSection()),
                            object : SaveRepository {
                                override suspend fun load(): SaveData? = null

                                override suspend fun save(data: SaveData): Boolean {
                                    alwaysFailingWrites += 1
                                    return false
                                }
                            },
                        ),
                    scope = this,
                    isTokenCurrent = { true },
                )
            failingScheduler.restoreAndEstablish(WebPlayerContextToken(7L))
            advanceUntilIdle()
            assertFalse(failingScheduler.unifiedSaveActive)
            assertEquals(WebUnifiedSaveStatus.ERROR, failingScheduler.saveStatus.value)
            assertEquals(3, alwaysFailingWrites)
        }

    private class ProbeSection : WebSaveSection {
        override val id = "probe"

        override fun export(): ByteArray = byteArrayOf(1)

        override fun apply(payload: ByteArray) = Unit
    }

    private class FakeEconomyStore(
        startingGems: Int = -1,
    ) : WebEconomyStore {
        private var snapshot =
            if (startingGems >= 0) WebEconomySnapshot.DEFAULT.copy(gems = startingGems) else WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private open class FakePlayerItemStore(
        private var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT,
    ) : WebStoreStore {
        override fun load(): WebStoreSnapshot = snapshot

        override fun save(snapshot: WebStoreSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FailingPlayerItemStore : WebStoreStore {
        override fun load(): WebStoreSnapshot = WebStoreSnapshot.DEFAULT

        override fun save(snapshot: WebStoreSnapshot): Unit = throw IllegalStateException("Simulated browser storage failure.")
    }

    private class FakePurchaseTransactionStore : WebPurchaseTransactionStore {
        private var stored: WebPurchaseTransaction? = null

        override fun load(): WebPurchaseTransaction? = stored

        override fun save(transaction: WebPurchaseTransaction) {
            stored = transaction
        }

        override fun clear() {
            stored = null
        }
    }

    private companion object
}
