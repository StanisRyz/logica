package com.stanisryz.logica.platform

/** Store-owned product metadata. Price text is already localized and formatted by the provider. */
internal data class PlatformProduct(
    val productId: String,
    val priceLabel: String,
)

/** A confirmed consumable transaction which the application has not finalized yet. */
internal data class PlatformPurchase(
    /** Provider-qualified durable key, for example `rustore:<purchaseId>`. */
    val transactionId: String,
    /** Raw provider ID used only to finalize the purchase with that provider. */
    val purchaseId: String,
    val productId: String,
)

/** Only [Confirmed] is eligible to enter the durable economy ledger. */
internal sealed interface PlatformPurchaseResult {
    data class Confirmed(
        val purchase: PlatformPurchase,
    ) : PlatformPurchaseResult

    data object Pending : PlatformPurchaseResult

    data object Cancelled : PlatformPurchaseResult

    data class Failed(
        val cause: Throwable,
    ) : PlatformPurchaseResult
}

internal class PlatformStoreException(
    message: String,
) : Exception(message)

/**
 * The consumable-purchase operations required by application code. Implementations translate
 * [finalize] to the provider's delivery acknowledgement, such as RuStore acknowledgement or
 * Yandex Games `consumePurchase()`.
 */
internal interface StoreGateway {
    suspend fun products(productIds: List<String>): List<PlatformProduct>

    suspend fun purchase(productId: String): PlatformPurchaseResult

    suspend fun unprocessedPurchases(): List<PlatformPurchase>

    suspend fun finalize(purchaseId: String)
}
