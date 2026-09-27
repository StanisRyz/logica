package com.stanisryz.logica.platform.android

import com.stanisryz.logica.platform.PlatformProduct
import com.stanisryz.logica.platform.PlatformPurchase
import com.stanisryz.logica.platform.PlatformPurchaseResult
import com.stanisryz.logica.platform.PlatformStoreException
import com.stanisryz.logica.platform.StoreGateway
import com.stanisryz.logica.store.RuStorePayGateway
import com.stanisryz.logica.store.RuStorePurchaseResult
import kotlinx.coroutines.CancellationException

/** Keeps RuStore SDK behavior behind the platform-neutral purchase contract. */
internal class AndroidRuStoreAdapter(
    private val delegate: RuStorePayGateway,
) : StoreGateway {
    override suspend fun products(productIds: List<String>): List<PlatformProduct> =
        translated("Android store could not load products.") {
            delegate.products(productIds).map { PlatformProduct(it.productId, it.priceLabel) }
        }

    override suspend fun purchase(productId: String): PlatformPurchaseResult =
        try {
            when (val result = delegate.purchase(productId)) {
                is RuStorePurchaseResult.Confirmed ->
                    PlatformPurchaseResult.Confirmed(
                        PlatformPurchase(
                            transactionId = "rustore:${result.purchase.purchaseId}",
                            purchaseId = result.purchase.purchaseId,
                            productId = result.purchase.productId,
                        ),
                    )
                RuStorePurchaseResult.Pending -> PlatformPurchaseResult.Pending
                RuStorePurchaseResult.Cancelled -> PlatformPurchaseResult.Cancelled
                is RuStorePurchaseResult.Failed ->
                    PlatformPurchaseResult.Failed(PlatformStoreException("Android store purchase failed."))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            PlatformPurchaseResult.Failed(PlatformStoreException("Android store purchase failed."))
        }

    override suspend fun unprocessedPurchases(): List<PlatformPurchase> =
        translated("Android store could not reconcile purchases.") {
            delegate.unfinalizedPurchases().map {
                PlatformPurchase(
                    transactionId = "rustore:${it.purchaseId}",
                    purchaseId = it.purchaseId,
                    productId = it.productId,
                )
            }
        }

    override suspend fun finalize(purchaseId: String) {
        translated("Android store could not finalize the purchase.") { delegate.finalize(purchaseId) }
    }

    private suspend fun <T> translated(
        message: String,
        operation: suspend () -> T,
    ): T =
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            throw PlatformStoreException(message)
        }
}
