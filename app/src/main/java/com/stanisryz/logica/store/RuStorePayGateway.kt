package com.stanisryz.logica.store

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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
import ru.rustore.sdk.pay.model.PurchaseAvailabilityResult
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
 * The gateway this build should use.
 *
 * Missing configuration is decided here, from the build, rather than discovered inside the SDK after
 * a call has already failed: a checkout without `logica.rustoreConsoleAppId` gets a gateway that
 * never touches `RuStorePayClient` at all.
 */
internal fun createRuStorePayGateway(
    consoleApplicationId: String,
    sdkTheme: () -> SdkTheme,
): RuStorePayGateway = if (consoleApplicationId.isBlank()) UnconfiguredRuStorePayGateway else RuStoreGemPayGateway(sdkTheme)

/**
 * The gateway for a build with no RuStore configuration. It offers nothing, which the store reads as
 * unavailable, and it makes no SDK call: there is no payment this build could complete.
 */
internal object UnconfiguredRuStorePayGateway : RuStorePayGateway {
    override suspend fun products(productIds: List<String>): List<StoreProduct> = emptyList()

    override suspend fun purchase(productId: String): StorePurchaseResult =
        StorePurchaseResult.Failed(StoreUnavailableException("This build has no RuStore console application ID."))

    override suspend fun unfinalizedPurchases(): List<StorePurchase> = emptyList()

    override suspend fun finalize(purchaseId: String) = Unit
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
 * here, and nothing is asked of it until the player opens the store. Every call is wrapped: a failure
 * and a request that never answers both end as [StoreUnavailableException], so the store can report
 * itself unavailable instead of crashing or waiting forever.
 */
internal class RuStoreGemPayGateway(
    private val sdkTheme: () -> SdkTheme,
) : RuStorePayGateway {
    private val purchaseInteractor get() = RuStorePayClient.instance.getPurchaseInteractor()

    override suspend fun products(productIds: List<String>): List<StoreProduct> =
        guarded("product") {
            requirePurchasesAvailable()
            RuStorePayClient.instance
                .getProductInteractor()
                .getProducts(productIds.map { ProductId(it) })
                .await()
                .filter { it.type == ProductType.CONSUMABLE_PRODUCT }
                .map { StoreProduct(productId = it.productId.value, priceLabel = it.amountLabel.value) }
        }

    override suspend fun purchase(productId: String): StorePurchaseResult {
        // The payment sheet is the player's own flow, so it is the one call with no time limit on it;
        // every way it can end is already a result rather than an exception.
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                return StorePurchaseResult.Failed(failure)
            }
        // A finished payment flow is not the same as a settled purchase, so the status is read back
        // from RuStore rather than assumed from the flow having returned at all.
        val purchase =
            try {
                guarded("purchase status") { purchaseInteractor.getPurchase(result.purchaseId).await() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                return StorePurchaseResult.Failed(failure)
            }
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
        guarded("unfinalized purchases") {
            purchaseInteractor
                .getPurchases(
                    productType = ProductType.CONSUMABLE_PRODUCT,
                    purchaseStatus = ProductPurchaseStatus.CONFIRMED,
                    acknowledgementState = AcknowledgementState.PENDING,
                ).await()
                .filterIsInstance<ProductPurchase>()
                .map { StorePurchase(purchaseId = it.purchaseId.value, productId = it.productId.value) }
        }

    override suspend fun finalize(purchaseId: String) {
        guarded("acknowledgement") {
            purchaseInteractor
                .updateAcknowledgementState(
                    purchaseId = PurchaseId(purchaseId),
                    state = AcknowledgementState.ACKNOWLEDGED,
                    developerPayload = null,
                ).await()
        }
    }

    /**
     * RuStore's own answer to whether this device can pay at all. Asking before the products request
     * is what turns a device without RuStore, without a signed-in user, or with a Pay SDK that failed
     * to initialize into a plainly unavailable store rather than a failing product load.
     */
    private suspend fun requirePurchasesAvailable() {
        val availability = purchaseInteractor.getPurchaseAvailability().await()
        if (availability !is PurchaseAvailabilityResult.Available) {
            throw StoreUnavailableException(
                "RuStore reports purchases as unavailable.",
                (availability as? PurchaseAvailabilityResult.Unavailable)?.cause,
            )
        }
    }

    /**
     * Every SDK touch that is not the payment sheet itself goes through here: anything thrown becomes
     * a [StoreUnavailableException], and a call that never answers is given up on rather than left to
     * hold the store in Loading forever. Coroutine cancellation is passed through untouched so a
     * closed store still stops its own work.
     */
    private suspend fun <T> guarded(
        operation: String,
        block: suspend () -> T,
    ): T =
        try {
            withTimeout(SDK_CALL_TIMEOUT_MILLIS) { block() }
        } catch (timeout: TimeoutCancellationException) {
            throw StoreUnavailableException("RuStore did not answer the $operation request in time.", timeout)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            throw StoreUnavailableException("RuStore could not answer the $operation request.", failure)
        }
}

/** How long a RuStore call the player is not watching may take before the store gives up on it. */
private const val SDK_CALL_TIMEOUT_MILLIS = 15_000L

/** The payment sheet follows the device's night mode, which is the only theme signal available. */
internal fun Context.ruStoreSdkTheme(): SdkTheme {
    val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (nightMode == Configuration.UI_MODE_NIGHT_YES) SdkTheme.DARK else SdkTheme.LIGHT
}

/**
 * Hands a finished payment's deeplink back to the Pay SDK, which is the only way it learns that the
 * player returned from an external payment application. A hand-off that fails changes nothing: the
 * purchase is still picked up by reconciliation the next time the store is opened.
 */
internal fun Context.proceedRuStorePayIntent(intent: Intent) {
    runCatching { RuStorePayClient.instance.getIntentInteractor().proceedIntent(intent, ruStoreSdkTheme()) }
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
