package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 2.1: a paid consumable is consumed after bounded flush retries, never paying twice. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebPaidConsumptionTest {
    private class Setup(
        scope: TestScope,
    ) {
        val provider = ScriptedPaymentsProvider()
        val unified = ScriptedUnifiedSave().apply { flushSucceeds = false }
        val revisions = WebPlayerStateRevisions()
        val economy =
            WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, MemoryEconomyStore(), revisions).also { it.loadLocal() }
        val payments = WebPlayerPaymentsRepository(WebCatalogProgressScope.STANDALONE, MemoryPaymentsStore()).also { it.loadLocal() }
        val coordinator =
            WebPaymentsCoordinator(
                provider = provider,
                economyRepository = { economy },
                storeRepository = { null },
                paymentsRepository = { payments },
                journalStore = { MemoryFulfillmentJournal() },
                revisions = { revisions },
                unifiedSaveAccess = { unified },
                currentPlayerContext = { WebPlayerContextToken(1L) },
                scope = scope,
            )
    }

    @Test
    fun aFailingFlushStillEndsInOneConsumeAndOneRewardWithoutHoldingTheStore() =
        runTest {
            val setup = Setup(this)
            val gemsBefore = setup.economy.currentSnapshot.gems
            setup.provider.purchaseResult =
                PaymentResult.Completed(PaymentPurchaseSnapshot("token-1", WebPaidProduct.GEMS_50.yandexProductId))

            setup.coordinator.purchase(WebPaidProduct.GEMS_50)
            runCurrent()
            // The reward is durable and the Store says so at once, while the flush is still retrying.
            assertEquals(WebPaidPurchaseState.Success, setup.coordinator.purchaseState.value)
            assertEquals(gemsBefore + 50, setup.economy.currentSnapshot.gems)
            assertTrue(setup.provider.consumedTokens.isEmpty())

            advanceTimeBy(2_500)
            runCurrent()
            assertEquals(2, setup.unified.flushCalls)
            assertTrue(setup.provider.consumedTokens.isEmpty())

            advanceUntilIdle()
            assertEquals(3, setup.unified.flushCalls)
            assertEquals(listOf("token-1"), setup.provider.consumedTokens.toList())
            assertEquals(gemsBefore + 50, setup.economy.currentSnapshot.gems)

            // The same token coming back from getPurchases() never pays again.
            setup.provider.pending = listOf(PaymentPurchaseSnapshot("token-1", WebPaidProduct.GEMS_50.yandexProductId))
            setup.coordinator.reconcilePendingPurchases()
            assertEquals(gemsBefore + 50, setup.economy.currentSnapshot.gems)
        }

    @Test
    fun noAdsIsNeverConsumed() =
        runTest {
            val setup = Setup(this)
            setup.provider.pending = listOf(PaymentPurchaseSnapshot("token-no-ads", WebPaidProduct.NO_ADS.yandexProductId))
            setup.coordinator.reconcilePendingPurchases()
            advanceUntilIdle()
            setup.unified.flushSucceeds = true
            setup.coordinator.reconcilePendingPurchases()
            advanceUntilIdle()
            assertTrue(
                setup.payments.snapshot.value
                    .owns(WebPaidProduct.NO_ADS),
            )
            assertTrue(setup.provider.consumedTokens.isEmpty())
        }
}
