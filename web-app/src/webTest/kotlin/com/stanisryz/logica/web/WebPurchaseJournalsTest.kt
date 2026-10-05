package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 2.1: purchase journals are decided by revisions and never roll the wallet back. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebPurchaseJournalsTest {
    private class Wallet(
        economyStore: MemoryEconomyStore = MemoryEconomyStore(WebEconomySnapshot.DEFAULT.copy(gems = 50)),
        itemStore: MemoryItemStore = MemoryItemStore(),
        paymentsStore: MemoryPaymentsStore = MemoryPaymentsStore(),
    ) {
        val revisions = WebPlayerStateRevisions()
        val economyStore = economyStore
        val itemStore = itemStore
        val economy = WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, economyStore, revisions).also { it.loadLocal() }
        val store = WebPlayerStoreRepository(WebCatalogProgressScope.STANDALONE, itemStore, revisions).also { it.loadLocal() }
        val payments = WebPlayerPaymentsRepository(WebCatalogProgressScope.STANDALONE, paymentsStore).also { it.loadLocal() }
        val txJournal = MemoryTransactionJournal()
        val paymentJournal = MemoryFulfillmentJournal()
        val processor = WebStoreProcessor({ economy }, { store }, { 1_000L }, { revisions }, { txJournal })

        fun coordinator(): WebPaymentsCoordinator =
            WebPaymentsCoordinator(
                provider = ScriptedPaymentsProvider(),
                economyRepository = { economy },
                storeRepository = { store },
                paymentsRepository = { payments },
                journalStore = { paymentJournal },
                revisions = { revisions },
                unifiedSaveAccess = { ScriptedUnifiedSave() },
                currentPlayerContext = { WebPlayerContextToken(1L) },
                scope = CoroutineScope(EmptyCoroutineContext),
            )

        fun recoverPurchase(): Boolean = WebPurchaseTransactionRecovery.recover(txJournal.stored!!, economy, store, txJournal)
    }

    private val hintPack get() = WebStoreCatalog.itemById(WebStoreCatalog.ITEM_HINT_PACK)!!

    @Test
    fun aGemPurchaseJournalWithNothingNewerIsApplied() {
        val wallet = Wallet()
        wallet.itemStore.failSaves = true
        assertIs<PurchaseResult.Failure>(wallet.processor.purchase(hintPack, null))
        assertNotNull(wallet.txJournal.stored)
        assertEquals(50, wallet.economy.currentSnapshot.gems)

        // The next bind: nothing changed since (cur < R), so the journaled pair lands.
        wallet.itemStore.failSaves = false
        assertTrue(wallet.recoverPurchase())
        assertEquals(40, wallet.economy.currentSnapshot.gems)
        assertEquals(
            3,
            wallet.store.snapshot.value
                .quantityOf(STORE_INVENTORY_HINTS),
        )
        assertNull(wallet.txJournal.stored)
    }

    @Test
    fun anAlreadyAppliedGemPurchaseJournalOnlyClears() {
        val wallet = Wallet()
        wallet.txJournal.failClear = true
        assertIs<PurchaseResult.Success>(wallet.processor.purchase(hintPack, null))
        assertNotNull(wallet.txJournal.stored) // the clear failed silently

        wallet.txJournal.failClear = false
        assertTrue(wallet.recoverPurchase())
        assertEquals(40, wallet.economy.currentSnapshot.gems)
        assertEquals(
            3,
            wallet.store.snapshot.value
                .quantityOf(STORE_INVENTORY_HINTS),
        )
        assertEquals(1, wallet.store.snapshot.value.history.size)
        assertNull(wallet.txJournal.stored)
    }

    @Test
    fun aStaleGemPurchaseJournalNeverRollsLaterChangesBack() {
        val wallet = Wallet()
        wallet.itemStore.failSaves = true
        assertIs<PurchaseResult.Failure>(wallet.processor.purchase(hintPack, null)) // the player sees "failed"
        wallet.itemStore.failSaves = false

        // Later changes: a life lost and gems spent elsewhere.
        assertTrue(wallet.economy.consumeLife())
        assertTrue(wallet.economy.spendGems(7))
        val livesAfter = wallet.economy.currentSnapshot.lives

        assertTrue(wallet.recoverPurchase())
        assertEquals(43, wallet.economy.currentSnapshot.gems)
        assertEquals(livesAfter, wallet.economy.currentSnapshot.lives)
        assertEquals(
            0,
            wallet.store.snapshot.value
                .quantityOf(STORE_INVENTORY_HINTS),
        )
        assertNull(wallet.txJournal.stored)
    }

    @Test
    fun aStalePaidFulfillmentJournalGrantsTheRewardExactlyOnceOnTopOfTheCurrentWallet() {
        val wallet = Wallet()
        val coordinator = wallet.coordinator()
        val purchase = PaymentPurchaseSnapshot("token-50", WebPaidProduct.GEMS_50.yandexProductId)

        // The journal lands, the wallet write does not.
        wallet.economyStore.failSaves = true
        assertEquals(WebPaymentOutcome.PersistenceFailed, coordinator.fulfillPurchase(purchase))
        assertNotNull(wallet.paymentJournal.stored)
        wallet.economyStore.failSaves = false

        // Later changes make the journal's absolute wallet stale.
        assertTrue(wallet.economy.spendGems(5))
        wallet.paymentJournal.failClear = true // and its own clear fails too

        assertTrue(coordinator.recoverPendingFulfillment())
        assertEquals(95, wallet.economy.currentSnapshot.gems) // 50 - 5 + 50
        assertTrue(wallet.payments.isFulfilled("token-50"))

        // Recovering again (the journal survived its clear) adds nothing.
        wallet.paymentJournal.failClear = false
        assertNotNull(wallet.paymentJournal.stored)
        assertTrue(coordinator.recoverPendingFulfillment())
        assertEquals(95, wallet.economy.currentSnapshot.gems)
        assertNull(wallet.paymentJournal.stored)
        assertEquals(WebPaymentOutcome.AlreadyFulfilled, coordinator.fulfillPurchase(purchase))
        assertEquals(95, wallet.economy.currentSnapshot.gems)
    }

    @Test
    fun aPaidFulfillmentJournalWithNothingNewerAppliesItsTargets() {
        val wallet = Wallet()
        val coordinator = wallet.coordinator()
        wallet.economyStore.failSaves = true
        coordinator.fulfillPurchase(PaymentPurchaseSnapshot("token-150", WebPaidProduct.GEMS_150.yandexProductId))
        wallet.economyStore.failSaves = false

        assertTrue(coordinator.recoverPendingFulfillment())
        assertEquals(200, wallet.economy.currentSnapshot.gems)
        assertTrue(wallet.payments.isFulfilled("token-150"))
        assertNull(wallet.paymentJournal.stored)
    }

    @Test
    fun everyMutationAfterAReloadIsNewerThanAPendingJournal() =
        runTest {
            val economyStore = MemoryEconomyStore(WebEconomySnapshot.DEFAULT.copy(gems = 50))
            val itemStore = MemoryItemStore()
            val txJournal = MemoryTransactionJournal()
            txJournal.stored =
                WebPurchaseTransaction(
                    id = "tx-old",
                    revision = 10L,
                    itemId = WebStoreCatalog.ITEM_HINT_PACK,
                    priceGems = 10,
                    targetEconomy = WebEconomySnapshot.DEFAULT.copy(gems = 40, revision = 10L),
                    targetStore = WebStoreSnapshot(inventory = mapOf(STORE_INVENTORY_HINTS to 3), revision = 10L),
                )

            // Session 1: the journal cannot be applied (the wallet refuses writes), then the Store changes.
            economyStore.failSaves = true
            val first = session(economyStore, itemStore, txJournal)
            advanceUntilIdle()
            assertNotNull(txJournal.stored)
            assertTrue(checkNotNull(first.storeRepository).grantInventory(STORE_INVENTORY_HINTS, 1))
            assertTrue(checkNotNull(first.storeRepository).snapshot.value.revision > 10L)

            // Session 2 (a reload): the journal is stale against that change and is dropped.
            economyStore.failSaves = false
            val second = session(economyStore, itemStore, txJournal)
            advanceUntilIdle()
            assertNull(txJournal.stored)
            assertEquals(50, checkNotNull(second.economyRepository).currentSnapshot.gems)
            assertEquals(1, checkNotNull(second.storeRepository).snapshot.value.quantityOf(STORE_INVENTORY_HINTS))
        }

    private fun TestScope.session(
        economyStore: MemoryEconomyStore,
        itemStore: MemoryItemStore,
        txJournal: MemoryTransactionJournal,
    ): WebPlayerSessionController {
        lateinit var session: WebPlayerSessionController
        session =
            WebPlayerSessionController(
                playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                progressRepositoryFactory = { s ->
                    WebCatalogProgressRepository(
                        s,
                        object : WebCatalogProgressStore {
                            override fun load() = WebCatalogProgressSnapshot.EMPTY

                            override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
                        },
                    )
                },
                statisticsCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                statisticsRepositoryFactory = { s ->
                    WebStatisticsRepository(
                        s,
                        "browser-installation-000001",
                        object : WebStatisticsStore {
                            override fun load() = WebStatisticsSnapshot.EMPTY

                            override fun save(snapshot: WebStatisticsSnapshot) = Unit
                        },
                    )
                },
                dailyCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                dailyRepositoryFactory = { s ->
                    WebDailyRepository(
                        s,
                        object : WebDailyStore {
                            override fun load() = WebDailySnapshotV1.EMPTY

                            override fun save(snapshot: WebDailySnapshotV1) = Unit
                        },
                    ) { DailyDate(2026, 10, 5) }
                },
                playerContextEvents =
                    object : WebPlayerContextEvents {
                        override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) = Unit

                        override fun setPlayerContextChangedListener(listener: (() -> Unit)?) = Unit
                    },
                economyRepositoryFactory = { s -> WebPlayerEconomyRepository(s, economyStore, session.activeStateRevisions) },
                storeRepositoryFactory = { s -> WebPlayerStoreRepository(s, itemStore, session.activeStateRevisions) },
                purchaseTransactionStoreFactory = { txJournal },
                paymentsRepositoryFactory = { s -> WebPlayerPaymentsRepository(s, MemoryPaymentsStore()) },
                paymentsJournalStoreFactory = { MemoryFulfillmentJournal() },
                scope = this,
            )
        session.start()
        return session
    }
}
