package com.stanisryz.logica.store

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.FakeEconomyDao
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.economy.PlayerEconomy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The purchase logic with RuStore faked out. What is checked is the part the SDK cannot guarantee:
 * that only a confirmed payment ever moves the wallet, and that a purchase credited before its
 * finalization survived is never paid for twice.
 */
class GemPurchaseProcessorTest {
    @Test
    fun onlyAConfirmedPurchaseOfAKnownPackIsCredited() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 5))
            val gateway = FakeRuStorePayGateway()
            val processor = GemPurchaseProcessor(gateway, FakeEconomyRepository(dao))

            gateway.nextResult = confirmed("p-1", GemPack.GEMS_250.productId)
            assertEquals(GemPurchaseOutcome.Granted(GemPack.GEMS_250), processor.buy(GemPack.GEMS_250))
            assertEquals(250, dao.wallet(NOW).gems)
            assertEquals(listOf("p-1"), gateway.finalized)

            // Everything that is not a confirmed payment leaves the wallet exactly where it was.
            gateway.nextResult = StorePurchaseResult.Cancelled
            assertEquals(GemPurchaseOutcome.Cancelled, processor.buy(GemPack.GEMS_50))
            gateway.nextResult = StorePurchaseResult.Pending
            assertEquals(GemPurchaseOutcome.Processing, processor.buy(GemPack.GEMS_50))
            gateway.nextResult = StorePurchaseResult.Failed(IllegalStateException("network"))
            assertEquals(GemPurchaseOutcome.Failed, processor.buy(GemPack.GEMS_50))

            assertEquals(250, dao.wallet(NOW).gems)
            assertEquals(1, dao.events.size)

            // A confirmed purchase of a product this build does not know grants nothing and is left
            // open rather than acknowledged, so a later build could still honour it.
            gateway.nextResult = confirmed("p-2", "gems_9000")
            assertEquals(GemPurchaseOutcome.Failed, processor.buy(GemPack.GEMS_600))

            assertEquals(250, dao.wallet(NOW).gems)
            assertEquals(1, dao.events.size)
            assertEquals(listOf("p-1"), gateway.finalized)
        }

    @Test
    fun aPurchaseCreditedBeforeAFailedFinalizationIsNotPaidTwiceOnReconciliation() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 5))
            val gateway = FakeRuStorePayGateway(finalizationFailures = 1)
            val processor = GemPurchaseProcessor(gateway, FakeEconomyRepository(dao))

            // The gems land, then the process effectively dies before RuStore is told about it.
            gateway.nextResult = confirmed("p-1", GemPack.GEMS_250.productId)
            assertEquals(GemPurchaseOutcome.Granted(GemPack.GEMS_250), processor.buy(GemPack.GEMS_250))
            assertEquals(250, dao.wallet(NOW).gems)
            assertEquals(emptyList<String>(), gateway.finalized)

            // RuStore therefore still considers it undelivered and hands it back.
            gateway.unfinalized = listOf(StorePurchase("p-1", GemPack.GEMS_250.productId))
            val reconciled = processor.reconcile()

            assertTrue(reconciled.single() is EconomyGemPurchase.AlreadyGranted)
            assertEquals(250, dao.wallet(NOW).gems)
            assertEquals(1, dao.events.size)
            // Only the finalization was retried, and this time it stuck.
            assertEquals(listOf("p-1"), gateway.finalized)
        }

    private fun confirmed(
        purchaseId: String,
        productId: String,
    ) = StorePurchaseResult.Confirmed(StorePurchase(purchaseId, productId))

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

/** RuStore, reduced to the four things the processor asks of it. */
private class FakeRuStorePayGateway(
    private var finalizationFailures: Int = 0,
) : RuStorePayGateway {
    var nextResult: StorePurchaseResult = StorePurchaseResult.Cancelled
    var unfinalized: List<StorePurchase> = emptyList()
    val finalized = mutableListOf<String>()

    override suspend fun products(productIds: List<String>): List<StoreProduct> = productIds.map { StoreProduct(it, "99 ₽") }

    override suspend fun purchase(productId: String): StorePurchaseResult = nextResult

    override suspend fun unfinalizedPurchases(): List<StorePurchase> = unfinalized

    override suspend fun finalize(purchaseId: String) {
        if (finalizationFailures > 0) {
            finalizationFailures--
            error("RuStore did not accept the acknowledgement.")
        }
        finalized += purchaseId
        unfinalized = unfinalized.filterNot { it.purchaseId == purchaseId }
    }
}

/** The real DAO transactions behind the repository contract. */
private class FakeEconomyRepository(
    private val dao: FakeEconomyDao,
) : EconomyRepository {
    override fun observe(): Flow<PlayerEconomy> = flowOf(dao.wallet(NOW))

    override suspend fun refresh(): PlayerEconomy = dao.refresh(NOW)

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = dao.refillLifeWithGems(actionId, NOW)

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = dao.grantRewardedLife(actionId, NOW)

    override suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase = dao.grantPurchasedGems(purchaseId, productId, NOW)

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
