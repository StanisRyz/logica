package com.stanisryz.logica.store

import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.core.tasks.OnFailureListener
import ru.rustore.sdk.core.tasks.OnSuccessListener
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.AcknowledgementState
import ru.rustore.sdk.pay.model.PreferredPurchaseType
import ru.rustore.sdk.pay.model.ProductId
import ru.rustore.sdk.pay.model.ProductPurchase
import ru.rustore.sdk.pay.model.ProductPurchaseParams
import ru.rustore.sdk.pay.model.ProductPurchaseStatus
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.PurchaseId
import ru.rustore.sdk.pay.model.RuStorePaymentException
import ru.rustore.sdk.pay.model.SdkTheme
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One gem pack as RuStore prices it. The label is the store's own formatted price, never ours. */
internal data class StoreProduct(
    val productId: String,
    val priceLabel: String,
)

/** A purchase that RuStore has confirmed and that has not been finalized yet. */
internal data class StorePurchase(
    val purchaseId: String,
    val productId: String,
)

/** How one payment attempt ended. Only [Confirmed] may ever reach the economy. */
internal sealed interface StorePurchaseResult {
    /** RuStore confirmed the payment; [purchase] is ready to be credited and then finalized. */
    data class Confirmed(
        val purchase: StorePurchase,
    ) : StorePurchaseResult

    /** The payment exists but is not settled yet; reconciliation finishes it later. */
    data object Pending : StorePurchaseResult

    /** The player backed out. A normal result, not a failure. */
    data object Cancelled : StorePurchaseResult

    data class Failed(
        val cause: Throwable,
    ) : StorePurchaseResult
}

/**
 * The whole RuStore surface the application uses, reduced to four operations. Everything above this
 * interface works with plain identifiers, which is what keeps billing types out of the economy and
 * lets the purchase logic be tested without the SDK.
 */
internal interface RuStorePayGateway {
    /** Prices for the requested products. A product RuStore does not return is unavailable. */
    suspend fun products(productIds: List<String>): List<StoreProduct>

    /** Opens RuStore's payment flow and waits for it to end. */
    suspend fun purchase(productId: String): StorePurchaseResult

    /** Confirmed consumable purchases RuStore still considers undelivered. */
    suspend fun unfinalizedPurchases(): List<StorePurchase>

    /** Tells RuStore the goods were delivered, which closes the purchase for good. */
    suspend fun finalize(purchaseId: String)
}

/**
 * The real gateway.
 *
 * Gem packs are consumables bought one at a time through the one-step flow, so the money is captured
 * by RuStore and the purchase arrives as `CONFIRMED` with its acknowledgement still `PENDING`. That
 * pending acknowledgement is exactly the "not delivered yet" marker reconciliation needs: the app
 * credits the gems first and only then acknowledges, and a purchase that never got acknowledged
 * comes back from [unfinalizedPurchases] on the next attempt.
 *
 * The SDK is initialized by its own ContentProvider from the manifest, so there is nothing to start
 * here; if that never happened, `RuStorePayClient.instance` throws and the store reports itself
 * unavailable without touching anything else in the application.
 */
internal class RuStoreGemPayGateway(
    private val sdkTheme: () -> SdkTheme,
) : RuStorePayGateway {
    private val purchaseInteractor get() = RuStorePayClient.instance.getPurchaseInteractor()

    override suspend fun products(productIds: List<String>): List<StoreProduct> =
        RuStorePayClient.instance
            .getProductInteractor()
            .getProducts(productIds.map { ProductId(it) })
            .await()
            .filter { it.type == ProductType.CONSUMABLE_PRODUCT }
            .map { StoreProduct(productId = it.productId.value, priceLabel = it.amountLabel.value) }

    override suspend fun purchase(productId: String): StorePurchaseResult {
        val result =
            try {
                purchaseInteractor
                    .purchase(
                        params = ProductPurchaseParams(productId = ProductId(productId)),
                        preferredPurchaseType = PreferredPurchaseType.ONE_STEP,
                        sdkTheme = sdkTheme(),
                        purchaseEventListener = null,
                    ).await()
            } catch (cancelled: RuStorePaymentException.ProductPurchaseCancelled) {
                return StorePurchaseResult.Cancelled
            } catch (failure: Throwable) {
                return StorePurchaseResult.Failed(failure)
            }
        // A finished payment flow is not the same as a settled purchase, so the status is read back
        // from RuStore rather than assumed from the flow having returned at all.
        val purchase =
            runCatching { purchaseInteractor.getPurchase(result.purchaseId).await() }
                .getOrElse { return StorePurchaseResult.Failed(it) }
        val productPurchase = purchase as? ProductPurchase ?: return StorePurchaseResult.Pending
        return when (productPurchase.status) {
            ProductPurchaseStatus.CONFIRMED ->
                StorePurchaseResult.Confirmed(
                    StorePurchase(
                        purchaseId = productPurchase.purchaseId.value,
                        productId = productPurchase.productId.value,
                    ),
                )
            ProductPurchaseStatus.CANCELLED,
            ProductPurchaseStatus.REJECTED,
            ProductPurchaseStatus.EXPIRED,
            ProductPurchaseStatus.REVERSED,
            -> StorePurchaseResult.Cancelled
            else -> StorePurchaseResult.Pending
        }
    }

    override suspend fun unfinalizedPurchases(): List<StorePurchase> =
        purchaseInteractor
            .getPurchases(
                productType = ProductType.CONSUMABLE_PRODUCT,
                purchaseStatus = ProductPurchaseStatus.CONFIRMED,
                acknowledgementState = AcknowledgementState.PENDING,
            ).await()
            .filterIsInstance<ProductPurchase>()
            .map { StorePurchase(purchaseId = it.purchaseId.value, productId = it.productId.value) }

    override suspend fun finalize(purchaseId: String) {
        purchaseInteractor
            .updateAcknowledgementState(
                purchaseId = PurchaseId(purchaseId),
                state = AcknowledgementState.ACKNOWLEDGED,
                developerPayload = null,
            ).await()
    }
}

/** `Task.await()` blocks its thread, so the listener pair is bridged instead. */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener(
            object : OnSuccessListener<T> {
                override fun onSuccess(result: T) {
                    if (continuation.isActive) continuation.resume(result)
                }
            },
        )
        addOnFailureListener(
            object : OnFailureListener {
                override fun onFailure(throwable: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(throwable)
                }
            },
        )
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }
