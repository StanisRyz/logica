package com.stanisryz.logica.store

import com.stanisryz.logica.platform.PlatformPurchaseResult
import com.stanisryz.logica.platform.android.AndroidRuStoreAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A build made with `-Plogica.withoutRustore=true` has no RuStore SDK. Whatever its configuration,
 * it gets the unconfigured gateway, which offers nothing — the store then opens as unavailable, as
 * `GemStoreViewModelTest` checks for that gateway — and can complete no payment.
 */
class WithoutRustoreStoreTest {
    @Test
    fun evenAConfiguredBuildGetsTheUnconfiguredGateway() =
        runTest {
            val gateway = createRuStorePayGateway(consoleApplicationId = "1234567", sdkTheme = {})
            assertSame(UnconfiguredRuStorePayGateway, gateway)

            val store = AndroidRuStoreAdapter(gateway)
            assertTrue(store.products(listOf("gems_50")).isEmpty())
            assertTrue(store.unprocessedPurchases().isEmpty())
            assertTrue(store.purchase("gems_50") is PlatformPurchaseResult.Failed)
        }
}
