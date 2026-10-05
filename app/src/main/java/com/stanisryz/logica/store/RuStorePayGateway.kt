package com.stanisryz.logica.store

/** One gem pack as RuStore prices it. The label is the store's own formatted price, never ours. */
internal data class RuStoreProduct(
    val productId: String,
    val priceLabel: String,
)

/** A purchase that RuStore has confirmed and that has not been finalized yet. */
internal data class RuStorePurchase(
    val purchaseId: String,
    val productId: String,
)

/** How one payment attempt ended. Only [Confirmed] may ever reach the economy. */
internal sealed interface RuStorePurchaseResult {
    /** RuStore confirmed the payment; [purchase] is ready to be credited and then finalized. */
    data class Confirmed(
        val purchase: RuStorePurchase,
    ) : RuStorePurchaseResult

    /** The payment exists but is not settled yet; reconciliation finishes it later. */
    data object Pending : RuStorePurchaseResult

    /** The player backed out. A normal result, not a failure. */
    data object Cancelled : RuStorePurchaseResult

    data class Failed(
        val cause: Throwable,
    ) : RuStorePurchaseResult
}

/**
 * The whole RuStore surface the application uses, reduced to four operations. Everything above this
 * interface works with plain identifiers, which is what keeps billing types out of the economy and
 * lets the purchase logic be tested without the SDK.
 */
internal interface RuStorePayGateway {
    /** Prices for the requested products. A product RuStore does not return is unavailable. */
    suspend fun products(productIds: List<String>): List<RuStoreProduct>

    /** Opens RuStore's payment flow and waits for it to end. */
    suspend fun purchase(productId: String): RuStorePurchaseResult

    /** Confirmed consumable purchases RuStore still considers undelivered. */
    suspend fun unfinalizedPurchases(): List<RuStorePurchase>

    /** Tells RuStore the goods were delivered, which closes the purchase for good. */
    suspend fun finalize(purchaseId: String)
}

/**
 * Why the store cannot be shown. Every way the SDK can fail — never initialized, no usable console
 * application ID, RuStore missing from the device, a request that simply never answers — is converted
 * into this one type at the gateway boundary, so no RuStore exception reaches the processor, the
 * ViewModel, or Compose, and "not usable here" stays a normal outcome instead of a crash.
 */
internal class StoreUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The gateway for a build with no RuStore configuration. It offers nothing, which the store reads as
 * unavailable, and it makes no SDK call: there is no payment this build could complete.
 */
internal object UnconfiguredRuStorePayGateway : RuStorePayGateway {
    override suspend fun products(productIds: List<String>): List<RuStoreProduct> = emptyList()

    override suspend fun purchase(productId: String): RuStorePurchaseResult =
        RuStorePurchaseResult.Failed(StoreUnavailableException("This build has no RuStore console application ID."))

    override suspend fun unfinalizedPurchases(): List<RuStorePurchase> = emptyList()

    override suspend fun finalize(purchaseId: String) = Unit
}
