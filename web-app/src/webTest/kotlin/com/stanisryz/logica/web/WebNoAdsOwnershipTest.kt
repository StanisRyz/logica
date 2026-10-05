package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 2.1: «no ads» follows the live getPurchases() answer once it succeeded in the bind. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebNoAdsOwnershipTest {
    private val forgedLedger = WebPaymentsSnapshot(fulfilledTokens = mapOf("forged-token" to WebPaidProduct.NO_ADS.yandexProductId))

    private fun coordinator(
        scope: kotlinx.coroutines.CoroutineScope,
        provider: ScriptedPaymentsProvider,
        payments: WebPlayerPaymentsRepository,
        context: () -> WebPlayerContextToken?,
    ): WebPaymentsCoordinator {
        val revisions = WebPlayerStateRevisions()
        val economy =
            WebPlayerEconomyRepository(
                WebCatalogProgressScope.STANDALONE,
                MemoryEconomyStore(),
                revisions,
            ).also { it.loadLocal() }
        return WebPaymentsCoordinator(
            provider = provider,
            economyRepository = { economy },
            storeRepository = { null },
            paymentsRepository = { payments },
            journalStore = { MemoryFulfillmentJournal() },
            revisions = { revisions },
            unifiedSaveAccess = { ScriptedUnifiedSave() },
            currentPlayerContext = context,
            scope = scope,
        )
    }

    @Test
    fun aForgedLedgerEntryDoesNotRemoveAdsOnceGetPurchasesAnswered() =
        runTest {
            val payments =
                WebPlayerPaymentsRepository(
                    WebCatalogProgressScope.STANDALONE,
                    MemoryPaymentsStore(forgedLedger),
                ).also { it.loadLocal() }
            val provider = ScriptedPaymentsProvider().apply { pending = null }
            var context = WebPlayerContextToken(1L)
            val coordinator = coordinator(this, provider, payments) { context }

            // getPurchases() failed: the ledger still decides, as before.
            coordinator.reconcilePendingPurchases()
            assertTrue(coordinator.owns(WebPaidProduct.NO_ADS))

            // It answers without «no ads»: the forged entry no longer counts (and is kept).
            provider.pending = emptyList()
            coordinator.reconcilePendingPurchases()
            assertFalse(coordinator.owns(WebPaidProduct.NO_ADS))
            assertTrue(payments.snapshot.value.owns(WebPaidProduct.NO_ADS))

            // Another Player context never reads that answer.
            context = WebPlayerContextToken(2L)
            assertTrue(coordinator.owns(WebPaidProduct.NO_ADS))
        }

    @Test
    fun aRealPurchaseRemovesAds() =
        runTest {
            val payments = WebPlayerPaymentsRepository(WebCatalogProgressScope.STANDALONE, MemoryPaymentsStore()).also { it.loadLocal() }
            val provider = ScriptedPaymentsProvider().apply { pending = emptyList() }
            val coordinator = coordinator(this, provider, payments) { WebPlayerContextToken(1L) }
            coordinator.reconcilePendingPurchases()
            assertFalse(coordinator.owns(WebPaidProduct.NO_ADS))

            // Bought in this session: confirmed by the purchase itself before the next getPurchases().
            provider.purchaseResult =
                com.stanisryz.logica.platform.PaymentResult.Completed(
                    PaymentPurchaseSnapshot("real-token", WebPaidProduct.NO_ADS.yandexProductId),
                )
            coordinator.purchase(WebPaidProduct.NO_ADS)
            advanceUntilIdle()
            assertTrue(coordinator.owns(WebPaidProduct.NO_ADS))

            // And the next bind's getPurchases() lists it.
            provider.pending = listOf(PaymentPurchaseSnapshot("real-token", WebPaidProduct.NO_ADS.yandexProductId))
            coordinator.reconcilePendingPurchases()
            assertTrue(coordinator.owns(WebPaidProduct.NO_ADS))
            assertTrue(provider.consumedTokens.isEmpty())
        }
}
