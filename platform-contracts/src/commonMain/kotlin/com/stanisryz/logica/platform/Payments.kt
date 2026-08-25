package com.stanisryz.logica.platform

/**
 * Platform-neutral payment models for real-money consumables. No SDK, JS, or secret types may
 * enter this file: hosts translate their own store APIs into these snapshots/results, and
 * granted rewards always flow back through the application's Economy/domain systems.
 *
 * [PaymentPurchaseSnapshot.purchaseToken] is an opaque identifier supplied by the platform and
 * is the durable exactly-once identity for consumable fulfillment.
 */

/** One purchasable product as described by the platform catalog (price/currency included). */
data class PaymentProductSnapshot(
    val productId: String,
    val title: String?,
    val description: String?,
    /** Human-readable price string exactly as supplied by the platform (e.g. "59.00"). */
    val price: String?,
    /** Raw numeric price value string supplied by the platform, when available. */
    val priceValue: String?,
    /** ISO currency code supplied by the platform, when available. */
    val priceCurrencyCode: String?,
    /** Platform-hosted currency icon URL, when available (rendering stays host-side). */
    val priceCurrencyImageUrl: String?,
)

/** One platform-side purchase identified by its opaque [purchaseToken]. */
data class PaymentPurchaseSnapshot(
    val purchaseToken: String,
    val productId: String,
)

/** Outcome of one user-initiated interactive purchase attempt. */
sealed interface PaymentResult {
    /** The platform confirmed the purchase; [purchase] carries the durable fulfillment token. */
    data class Completed(
        val purchase: PaymentPurchaseSnapshot,
    ) : PaymentResult

    /** The user closed/cancelled the payment frame before paying; nothing was bought. */
    data object Cancelled : PaymentResult

    /** The purchase attempt failed; the platform state stays recoverable via pending queries. */
    data class Failed(
        val detail: String?,
    ) : PaymentResult

    /** Payments are unavailable in this environment/session; nothing was attempted. */
    data object Unavailable : PaymentResult
}
