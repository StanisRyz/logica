package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.15: paid consumable fulfillment is keyed by the opaque Yandex purchaseToken,
 * grants exactly +100 gems exactly once, survives interrupted local fulfillment through its
 * durable journal, and converges cloud-flush/consume retries without ever paying twice.
 */
class WebPaymentsTest {
    private val standaloneScope = WebCatalogProgressScope.STANDALONE

    private class FakeEconomyStore : WebEconomyStore {
        var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakePaymentsStore : WebPaymentsStore {
        var snapshot: WebPaymentsSnapshot = WebPaymentsSnapshot.EMPTY

        override fun load(): WebPaymentsSnapshot = snapshot

        override fun save(snapshot: WebPaymentsSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeJournalStore : WebPaymentsJournalStore {
        var stored: WebPendingPaymentFulfillment? = null

        override fun load(): WebPendingPaymentFulfillment? = stored

        override fun save(fulfillment: WebPendingPaymentFulfillment) {
            stored = fulfillment
        }

        override fun clear() {
            stored = null
        }
    }

    private class FakeUnifiedSaveAccess : WebUnifiedSaveAccess {
        override val unifiedSaveActive = true
        var flushSucceeds = true
        var flushCalls = 0

        override val saveStatus =
            kotlinx.coroutines.flow.MutableStateFlow(WebUnifiedSaveStatus.SYNCED)

        override fun markDirty() = Unit

        override suspend fun flushNow(): Boolean {
            flushCalls += 1
            return flushSucceeds
        }

        override fun invalidateContext() = Unit
    }

    private class FakePaymentsProvider : WebPaymentsProvider {
        var pending: List<PaymentPurchaseSnapshot> = emptyList()
        var consumeSucceeds = true
        val consumedTokens = mutableListOf<String>()

        override suspend fun catalog(): List<com.stanisryz.logica.platform.PaymentProductSnapshot>? = null

        override suspend fun purchase(productId: String): PaymentResult = PaymentResult.Unavailable

        override suspend fun pendingPurchases(): List<PaymentPurchaseSnapshot>? = pending

        override suspend fun consume(purchaseToken: String): Boolean {
            if (!consumeSucceeds) return false
            consumedTokens.add(purchaseToken)
            return true
        }
    }

    private fun economyRepository(store: FakeEconomyStore, revisions: WebPlayerStateRevisions): WebPlayerEconomyRepository =
        WebPlayerEconomyRepository(standaloneScope, store, revisions).also { it.loadLocal() }

    private fun paymentsRepository(store: FakePaymentsStore): WebPlayerPaymentsRepository =
        WebPlayerPaymentsRepository(standaloneScope, store).also { it.loadLocal() }

    private fun coordinator(
        economy: WebPlayerEconomyRepository,
        payments: WebPlayerPaymentsRepository,
        journal: FakeJournalStore,
        revisions: WebPlayerStateRevisions,
        unified: FakeUnifiedSaveAccess,
        provider: FakePaymentsProvider,
    ): WebPaymentsCoordinator =
        WebPaymentsCoordinator(
            provider = provider,
            economyRepository = { economy },
            paymentsRepository = { payments },
            journalStore = { journal },
            revisions = { revisions },
            unifiedSaveAccess = { unified },
            currentPlayerContext = { WebPlayerContextToken(7L) },
            scope = CoroutineScope(EmptyCoroutineContext),
        )

    // Test 1: one token grants exactly +100 gems exactly once.
    @Test
    fun samePurchaseTokenNeverGrantsTwice() =
        runTest {
            val revisions = WebPlayerStateRevisions()
            val economyFake = FakeEconomyStore()
            val paymentsFake = FakePaymentsStore()
            val economy = economyRepository(economyFake, revisions)
            val payments = paymentsRepository(paymentsFake)
            val coordinator =
                coordinator(economy, payments, FakeJournalStore(), revisions, FakeUnifiedSaveAccess(), FakePaymentsProvider())

            val outcome = coordinator.fulfillPurchase(PaymentPurchaseSnapshot("tok-1", "gems_small"))
            assertEquals(WebPaymentOutcome.Fulfilled, outcome)
            assertEquals(100, economy.currentSnapshot.gems)
            assertTrue(payments.isFulfilled("tok-1"))

            // Presenting the SAME token again must never pay a second time.
            val repeatOutcome =
                coordinator.fulfillPurchase(PaymentPurchaseSnapshot("tok-1", "gems_small"))
            assertEquals(WebPaymentOutcome.AlreadyFulfilled, repeatOutcome)
            assertEquals(100, economy.currentSnapshot.gems)
            assertEquals(1, payments.snapshot.value.fulfilledTokens.size)
        }

    // Test 2: an interrupted fulfillment is recovered exactly once from its durable journal.
    @Test
    fun interruptedFulfillmentRecoversExactlyOnce() =
        runTest {
            val revisions = WebPlayerStateRevisions()
            val economyFake = FakeEconomyStore()
            val paymentsFake = FakePaymentsStore()
            val journal = FakeJournalStore()
            val unified = FakeUnifiedSaveAccess()
            val provider = FakePaymentsProvider()

            // Crash mid-fulfillment: the journal and the economy side are durable on disk, the
            // in-memory repositories never saw anything (fresh process), payments never applied.
            val targetEconomy =
                WebEconomySnapshot(
                    gems = 100,
                    lives = 5,
                    nextLifeRestoreAtEpochMs = null,
                    revision = 3L,
                )
            val fulfillment =
                WebPendingPaymentFulfillment(
                    id = "pay-1",
                    purchaseToken = "tok-9",
                    productId = "gems_small",
                    targetEconomy = targetEconomy,
                    targetPayments =
                        WebPaymentsSnapshot(fulfilledTokens = mapOf("tok-9" to "gems_small")),
                )
            journal.save(fulfillment)
            economyFake.snapshot = targetEconomy

            // Fresh bind: nothing granted yet, then recovery lands both sides once.
            val economy = economyRepository(economyFake, revisions)
            val payments = paymentsRepository(paymentsFake)
            val coordinator = coordinator(economy, payments, journal, revisions, unified, provider)
            assertTrue(coordinator.recoverPendingFulfillment())
            assertEquals(100, economy.currentSnapshot.gems)
            assertTrue(payments.isFulfilled("tok-9"))
            assertNull(journal.stored) // committed: the journal cleared

            // A repeated recovery attempt is an idempotent no-op — no double grant.
            assertFalse(coordinator.recoverPendingFulfillment())
            assertEquals(100, economy.currentSnapshot.gems)
            assertEquals(1, payments.snapshot.value.fulfilledTokens.size)

            // The same token can never pay again through normal fulfillment either.
            val replayOutcome = coordinator.fulfillPurchase(PaymentPurchaseSnapshot("tok-9", "gems_small"))
            assertEquals(WebPaymentOutcome.AlreadyFulfilled, replayOutcome)
            assertEquals(100, economy.currentSnapshot.gems)
        }

    // Test 3: cloud flush gates consumption; failed consume retries through reconcile without
    // ever granting a second reward.
    @Test
    fun failedConsumeRetriesWithoutDoubleGrant() =
        runTest {
            val revisions = WebPlayerStateRevisions()
            val economyFake = FakeEconomyStore()
            val paymentsFake = FakePaymentsStore()
            val journal = FakeJournalStore()
            val unified = FakeUnifiedSaveAccess()
            val provider = FakePaymentsProvider().apply { consumeSucceeds = false }
            val economy = economyRepository(economyFake, revisions)
            val payments = paymentsRepository(paymentsFake)
            val coordinator = coordinator(economy, payments, journal, revisions, unified, provider)

            // Local fulfillment succeeds durably (no consume attempt happens here)...
            assertEquals(WebPaymentOutcome.Fulfilled, coordinator.fulfillPurchase(PaymentPurchaseSnapshot("tok-5", "gems_small")))
            assertEquals(100, economy.currentSnapshot.gems)
            assertNull(journal.stored)

            // ...but the post-bind reconcile's consumption fails while cloud flush works.
            provider.pending = listOf(PaymentPurchaseSnapshot("tok-5", "gems_small"))
            coordinator.reconcilePendingPurchases()
            assertEquals(0, provider.consumedTokens.size) // never consumed while failing
            assertEquals(100, economy.currentSnapshot.gems) // and never granted twice

            // Later reconcile: consume now succeeds exactly once for this token.
            provider.consumeSucceeds = true
            coordinator.reconcilePendingPurchases()
            assertEquals(listOf("tok-5"), provider.consumedTokens.toList())
            assertEquals(100, economy.currentSnapshot.gems)

            // A failed canonical flush gates consumption entirely: no consume call at all.
            val failingFlush =
                coordinator(
                    economy,
                    payments,
                    journal,
                    revisions,
                    FakeUnifiedSaveAccess().apply { flushSucceeds = false },
                    provider,
                )
            provider.pending = listOf(PaymentPurchaseSnapshot("tok-6", "gems_small"))
            failingFlush.reconcilePendingPurchases()
            assertFalse(provider.consumedTokens.contains("tok-6"))
        }

    /** Stage 45.16: an unknown pending productID stays recoverable but is never granted/consumed. */
    @Test
    fun unknownPendingProductIsRecordedButNeverGrantedOrConsumed() =
        runTest {
            val revisions = WebPlayerStateRevisions()
            val economy = economyRepository(FakeEconomyStore(), revisions)
            val payments = paymentsRepository(FakePaymentsStore())
            val provider = FakePaymentsProvider()
            val coordinator =
                coordinator(
                    economy,
                    payments,
                    FakeJournalStore(),
                    revisions,
                    FakeUnifiedSaveAccess(),
                    provider,
                )

            provider.pending = listOf(PaymentPurchaseSnapshot("tok-unknown", "mystery_pack"))
            coordinator.reconcilePendingPurchases()

            assertEquals(listOf(PaymentPurchaseSnapshot("tok-unknown", "mystery_pack")), coordinator.unknownProducts.value)
            assertEquals(0, provider.consumedTokens.size) // never consumed
            assertFalse(payments.isFulfilled("tok-unknown")) // never granted
            assertEquals(0, economy.currentSnapshot.gems)
        }
}
