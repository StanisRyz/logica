package com.stanisryz.logica.store

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.platform.PlatformProduct
import com.stanisryz.logica.platform.PlatformPurchase
import com.stanisryz.logica.platform.PlatformPurchaseResult
import com.stanisryz.logica.platform.StoreGateway
import com.stanisryz.logica.platform.android.AndroidRuStoreAdapter
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
            val gateway =
                AndroidRuStoreAdapter(
                    createRuStorePayGateway(consoleApplicationId = "", sdkTheme = { error("the SDK is never asked") }),
                )
            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, TEST_PRODUCTS, UnusedEconomyRepository))

            viewModel.open()
            advanceUntilIdle()

            assertEquals(GemStoreState.Unavailable, viewModel.state.value)
        }

    @Test
    fun nothingIsAskedOfRuStoreUntilTheStoreIsOpened() =
        runTest(dispatcher) {
            val gateway = RecordingStoreGateway()

            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, TEST_PRODUCTS, UnusedEconomyRepository))
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
            val gateway = RecordingStoreGateway(failure = IllegalStateException("RuStorePayClient is not created"))
            val viewModel = GemStoreViewModel(GemPurchaseProcessor(gateway, TEST_PRODUCTS, UnusedEconomyRepository))

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
private val TEST_PRODUCTS =
    GemPackProductMapping(
        mapOf(
            GemPack.GEMS_50 to "gems_50",
            GemPack.GEMS_250 to "gems_250",
            GemPack.GEMS_600 to "gems_600",
        ),
    )

private class RecordingStoreGateway(
    var failure: Throwable? = null,
) : StoreGateway {
    val calls = mutableListOf<String>()

    override suspend fun products(productIds: List<String>): List<PlatformProduct> {
        calls += "products"
        failure?.let { throw it }
        return productIds.map { PlatformProduct(it, "99 ₽") }
    }

    override suspend fun purchase(productId: String): PlatformPurchaseResult {
        calls += "purchase"
        return PlatformPurchaseResult.Cancelled
    }

    override suspend fun unprocessedPurchases(): List<PlatformPurchase> {
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
