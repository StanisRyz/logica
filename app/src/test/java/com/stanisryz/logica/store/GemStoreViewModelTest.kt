package com.stanisryz.logica.store

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.PlayerEconomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the store must survive rather than what it sells: a build RuStore was never configured for, an
 * SDK that fails, and a tab that is entered twice. The store is allowed to be unavailable; it is not
 * allowed to throw, to load twice, or to do any of this before the player opened it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GemStoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aBuildWithoutAConsoleApplicationIdOpensTheStoreAsUnavailable() =
        runTest(dispatcher) {
            // The gateway a checkout without `logica.rustoreConsoleAppId` gets. Nothing in it may
            // reach the SDK, which is why the theme it would need is an error here.
            val gateway = createRuStorePayGateway(consoleApplicationId = "", sdkTheme = { error("the SDK is never asked") })
            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, UnusedEconomyRepository))

            viewModel.open()
            advanceUntilIdle()

            assertEquals(GemStoreState.Unavailable, viewModel.state.value)
        }

    @Test
    fun nothingIsAskedOfRuStoreUntilTheStoreIsOpened() =
        runTest(dispatcher) {
            val gateway = RecordingRuStorePayGateway()

            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, UnusedEconomyRepository))
            advanceUntilIdle()

            assertEquals(emptyList<String>(), gateway.calls)

            viewModel.open()
            advanceUntilIdle()

            // Opening reconciles anything already paid for and only then loads the prices.
            assertEquals(listOf("unfinalizedPurchases", "products"), gateway.calls)
            assertEquals(3, (viewModel.state.value as GemStoreState.Ready).offers.size)
        }

    @Test
    fun aFailingSdkLeavesTheStoreUnavailableWithOneLoadAtATimeAndARetryThatWorks() =
        runTest(dispatcher) {
            val gateway = RecordingRuStorePayGateway(failure = IllegalStateException("RuStorePayClient is not created"))
            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, UnusedEconomyRepository))

            // Re-entering the tab while the first load is still running is not a second load.
            viewModel.open()
            viewModel.open()
            advanceUntilIdle()

            assertEquals(GemStoreState.Unavailable, viewModel.state.value)
            assertEquals(listOf("unfinalizedPurchases", "products"), gateway.calls)

            gateway.failure = null
            viewModel.open()
            advanceUntilIdle()

            assertTrue(viewModel.state.value is GemStoreState.Ready)
            assertEquals(
                listOf("unfinalizedPurchases", "products", "unfinalizedPurchases", "products"),
                gateway.calls,
            )
        }
}

/** RuStore reduced to what it was asked and when, plus a switch for making it fail. */
private class RecordingRuStorePayGateway(
    var failure: Throwable? = null,
) : RuStorePayGateway {
    val calls = mutableListOf<String>()

    override suspend fun products(productIds: List<String>): List<StoreProduct> {
        calls += "products"
        failure?.let { throw it }
        return productIds.map { StoreProduct(it, "99 ₽") }
    }

    override suspend fun purchase(productId: String): StorePurchaseResult {
        calls += "purchase"
        return StorePurchaseResult.Cancelled
    }

    override suspend fun unfinalizedPurchases(): List<StorePurchase> {
        calls += "unfinalizedPurchases"
        failure?.let { throw it }
        return emptyList()
    }

    override suspend fun finalize(purchaseId: String) {
        calls += "finalize"
    }
}

/** The wallet is not part of any of this: a store that cannot load never touches the economy. */
private object UnusedEconomyRepository : EconomyRepository {
    override fun observe(): Flow<PlayerEconomy> = emptyFlow()

    override suspend fun refresh(): PlayerEconomy = error("the store test never touches the wallet")

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = error("the store test never touches the wallet")

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = error("the store test never touches the wallet")

    override suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase = error("the store test never touches the wallet")
}
