package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop

/** The single application-owned paid product; the gameplay reward never depends on Yandex data. */
internal enum class WebPaidProduct(
    val yandexProductId: String,
    val gemReward: Int,
) {
    GEMS_SMALL("gems_small", 100),
}

internal fun paidProductFor(yandexProductId: String): WebPaidProduct? =
    WebPaidProduct.entries.firstOrNull { it.yandexProductId == yandexProductId }

/**
 * Versioned Player-scoped Payments persistence: the durable ledger of fulfilled purchase tokens.
 * The token is the exactly-once identity; entries are monotonic identities, so multi-device
 * restore uses UNION semantics (never last-write-wins) and can never forget a fulfilled token.
 * Gems are NOT stored here — Economy stays the only source of truth.
 */
internal data class WebPaymentsSnapshot(
    val version: Int = CURRENT_VERSION,
    val fulfilledTokens: Map<String, String> = emptyMap(),
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported Web payments schema $version." }
        require(fulfilledTokens.size <= MAX_LEDGER_ENTRIES) { "Web payments ledger is over budget." }
        require(fulfilledTokens.keys.all(String::isNotBlank)) { "Fulfilled purchase tokens must be non-blank." }
    }

    fun isFulfilled(purchaseToken: String): Boolean = fulfilledTokens.containsKey(purchaseToken)

    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Deliberately generous: bounding the ledger could forget an old still-recoverable
         * purchase and pay it twice, so the ledger grows with real purchases instead.
         */
        const val MAX_LEDGER_ENTRIES = 1_000

        val EMPTY = WebPaymentsSnapshot()
    }
}

/** Deterministic compact binary format for the fulfilled-token ledger. */
internal object WebPaymentsCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte())
    private const val MAX_ID_LENGTH = 0xff

    fun encode(snapshot: WebPaymentsSnapshot): ByteArray {
        val entries =
            snapshot.fulfilledTokens.entries
                .sortedWith(compareBy({ it.key }, { it.value }))
        require(entries.all { (token, productId) -> token.length in 1..MAX_ID_LENGTH && productId.length in 1..MAX_ID_LENGTH })

        var size = 4 + 1 + 4
        entries.forEach { (token, productId) -> size += 2 + token.encodeToByteArray().size + 1 + productId.encodeToByteArray().size }

        val result = ByteArray(size)
        magic.copyInto(result)
        result[4] = snapshot.version.toByte()
        writeInt(result, 5, entries.size)
        var offset = 9
        entries.forEach { (token, productId) ->
            val tokenBytes = token.encodeToByteArray()
            val productBytes = productId.encodeToByteArray()
            result[offset] = ((tokenBytes.size ushr 8) and 0xff).toByte()
            result[offset + 1] = (tokenBytes.size and 0xff).toByte()
            tokenBytes.copyInto(result, offset + 2)
            offset += 2 + tokenBytes.size
            result[offset] = productBytes.size.toByte()
            productBytes.copyInto(result, offset + 1)
            offset += 1 + productBytes.size
        }
        return result
    }

    fun decode(payload: ByteArray): WebPaymentsSnapshot? =
        runCatching {
            require(payload.size >= 9)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version == WebPaymentsSnapshot.CURRENT_VERSION)
            val count = readInt(payload, 5)
            require(count in 0..WebPaymentsSnapshot.MAX_LEDGER_ENTRIES)
            var offset = 9
            val tokens = LinkedHashMap<String, String>(count)
            repeat(count) {
                require(offset + 2 <= payload.size)
                val tokenLength = ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)
                require(tokenLength in 1..MAX_ID_LENGTH && offset + 2 + tokenLength + 1 <= payload.size)
                val token = payload.copyOfRange(offset + 2, offset + 2 + tokenLength).decodeToString()
                offset += 2 + tokenLength
                val productLength = payload[offset].toInt() and 0xff
                require(productLength in 1..MAX_ID_LENGTH && offset + 1 + productLength <= payload.size)
                val productId = payload.copyOfRange(offset + 1, offset + 1 + productLength).decodeToString()
                offset += 1 + productLength
                require(tokens.put(token, productId) == null) { "Duplicate fulfilled purchase token." }
            }
            WebPaymentsSnapshot(fulfilledTokens = tokens)
        }.getOrNull()

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int = ((source[offset].toInt() and 0xff) shl 24) or
        ((source[offset + 1].toInt() and 0xff) shl 16) or
        ((source[offset + 2].toInt() and 0xff) shl 8) or
        (source[offset + 3].toInt() and 0xff)

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }
}

/** Player-scoped durable storage for the fulfilled-token ledger. */
internal interface WebPaymentsStore {
    fun load(): WebPaymentsSnapshot

    fun save(snapshot: WebPaymentsSnapshot)
}

internal class WebPaymentsLocalStore(
    scope: WebCatalogProgressScope,
) : WebPaymentsStore {
    internal val storageKey = "$STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebPaymentsSnapshot {
        val encoded = paymentsLocalStorageGet(storageKey) ?: return WebPaymentsSnapshot.EMPTY
        val payload = WebBase64.decode(encoded) ?: return WebPaymentsSnapshot.EMPTY
        return WebPaymentsCodec.decode(payload) ?: WebPaymentsSnapshot.EMPTY
    }

    override fun save(snapshot: WebPaymentsSnapshot) {
        paymentsLocalStorageSet(storageKey, WebBase64.encode(WebPaymentsCodec.encode(snapshot)))
    }

    private companion object {
        const val STORAGE_KEY_PREFIX = "logica_payments_v1"
    }
}

private fun paymentsLocalStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun paymentsLocalStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

internal fun interface WebPaymentsRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebPlayerPaymentsRepository
}

/**
 * Player-scoped fulfilled-token ledger. Union-only mutations keep fulfillment identities
 * monotonic across devices; every mutation is durable-first and never publishes on failure.
 */
internal class WebPlayerPaymentsRepository(
    val scope: WebCatalogProgressScope,
    private val store: WebPaymentsStore,
) {
    private val mutableSnapshot = MutableStateFlow(WebPaymentsSnapshot.EMPTY)
    val snapshot: StateFlow<WebPaymentsSnapshot> = mutableSnapshot.asStateFlow()

    /** Invoked after every successful durable local mutation; never after a cloud restore. */
    var onDurableChange: (() -> Unit)? = null

    fun isFulfilled(purchaseToken: String): Boolean = mutableSnapshot.value.isFulfilled(purchaseToken)

    fun loadLocal() {
        mutableSnapshot.value = store.load()
    }

    /**
     * Unions the given fulfilled identities into the ledger (idempotent for known tokens).
     * Durable-first: the merged snapshot persists locally before it becomes observable.
     */
    fun recordFulfillments(entries: Map<String, String>): WebExternalRestoreResult =
        applyMerged { current -> WebPaymentsSnapshot(fulfilledTokens = current.fulfilledTokens + entries) }

    /** Unified cloud restore: union merge preserves both devices' fulfillment knowledge. */
    fun mergeCloud(cloud: WebPaymentsSnapshot): WebExternalRestoreResult =
        applyMerged { current -> WebPaymentsSnapshot(fulfilledTokens = current.fulfilledTokens + cloud.fulfilledTokens) }

    /** Applies an absolute target snapshot durably-first (used by fulfillment recovery). */
    fun applyExternal(target: WebPaymentsSnapshot): WebExternalRestoreResult =
        runCatching {
            if (target == mutableSnapshot.value) return@runCatching WebExternalRestoreResult.NoChange as WebExternalRestoreResult
            runCatching { store.save(target) }.getOrElse {
                return@runCatching WebExternalRestoreResult.PersistenceFailed(it) as WebExternalRestoreResult
            }
            mutableSnapshot.value = target
            WebExternalRestoreResult.Applied as WebExternalRestoreResult
        }.getOrDefault(WebExternalRestoreResult.Rejected)

    private fun applyMerged(merge: (WebPaymentsSnapshot) -> WebPaymentsSnapshot): WebExternalRestoreResult =
        runCatching {
            val merged = merge(mutableSnapshot.value)
            if (merged == mutableSnapshot.value) {
                return@runCatching WebExternalRestoreResult.NoChange as WebExternalRestoreResult
            }
            runCatching { store.save(merged) }.getOrElse {
                return@runCatching WebExternalRestoreResult.PersistenceFailed(it) as WebExternalRestoreResult
            }
            mutableSnapshot.value = merged
            onDurableChange?.invoke()
            WebExternalRestoreResult.Applied as WebExternalRestoreResult
        }.getOrDefault(WebExternalRestoreResult.Rejected)
}

/**
 * One recoverable paid-fulfillment transaction: granting the configured gem reward and marking
 * the purchase token fulfilled must happen together or be replayable together. The journal
 * stores absolute target snapshots so recovery is deterministic and idempotent.
 */
internal data class WebPendingPaymentFulfillment(
    val version: Int = CURRENT_VERSION,
    val id: String,
    val purchaseToken: String,
    val productId: String,
    val targetEconomy: WebEconomySnapshot,
    val targetPayments: WebPaymentsSnapshot,
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported Web payment fulfillment $version." }
        require(id.isNotEmpty()) { "A payment fulfillment needs a stable id." }
        require(purchaseToken.isNotBlank()) { "A payment fulfillment needs its purchase token." }
        require(productId.isNotBlank()) { "A payment fulfillment needs its product id." }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** Player-scoped durable journal of one pending paid fulfillment. */
internal interface WebPaymentsJournalStore {
    fun load(): WebPendingPaymentFulfillment?

    fun save(fulfillment: WebPendingPaymentFulfillment)

    fun clear()
}

internal class BrowserWebPaymentsJournalStore(
    scope: WebCatalogProgressScope,
) : WebPaymentsJournalStore {
    private val storageKey = "$STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebPendingPaymentFulfillment? {
        val encoded = paymentsLocalStorageGet(storageKey) ?: return null
        val payload = WebBase64.decode(encoded) ?: return null
        return WebPendingPaymentFulfillmentCodec.decode(payload)
    }

    override fun save(fulfillment: WebPendingPaymentFulfillment) {
        paymentsLocalStorageSet(storageKey, WebBase64.encode(WebPendingPaymentFulfillmentCodec.encode(fulfillment)))
    }

    override fun clear() {
        paymentsLocalStorageSet(storageKey, "")
    }

    private companion object {
        const val STORAGE_KEY_PREFIX = "logica_payments_journal_v1"
    }
}

/** Deterministic compact binary format for the pending paid-fulfillment journal. */
internal object WebPendingPaymentFulfillmentCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'P'.code.toByte(), 'J'.code.toByte())
    private const val MAX_ID_LENGTH = 96
    private const val MAX_TOKEN_LENGTH = 384

    fun encode(fulfillment: WebPendingPaymentFulfillment): ByteArray {
        val idBytes = fulfillment.id.encodeToByteArray()
        val tokenBytes = fulfillment.purchaseToken.encodeToByteArray()
        val productBytes = fulfillment.productId.encodeToByteArray()
        require(idBytes.size in 1..MAX_ID_LENGTH)
        require(tokenBytes.size in 1..MAX_TOKEN_LENGTH)
        require(productBytes.size in 1..MAX_ID_LENGTH)
        val economyPayload = WebEconomyCodec.encode(fulfillment.targetEconomy)
        val paymentsPayload = WebPaymentsCodec.encode(fulfillment.targetPayments)

        var cursor = 4 + 1 + 1 + idBytes.size + 2 + tokenBytes.size + 1 + productBytes.size
        val result = ByteArray(cursor + 4 + economyPayload.size + 4 + paymentsPayload.size)
        magic.copyInto(result)
        result[4] = fulfillment.version.toByte()
        result[5] = idBytes.size.toByte()
        idBytes.copyInto(result, 6)
        cursor = 6 + idBytes.size
        writeShort(result, cursor, tokenBytes.size)
        tokenBytes.copyInto(result, cursor + 2)
        cursor += 2 + tokenBytes.size
        result[cursor] = productBytes.size.toByte()
        productBytes.copyInto(result, cursor + 1)
        cursor += 1 + productBytes.size
        writeInt(result, cursor, economyPayload.size)
        economyPayload.copyInto(result, cursor + 4)
        cursor += 4 + economyPayload.size
        writeInt(result, cursor, paymentsPayload.size)
        paymentsPayload.copyInto(result, cursor + 4)
        return result
    }

    fun decode(payload: ByteArray): WebPendingPaymentFulfillment? =
        runCatching {
            require(payload.size >= 8)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version == WebPendingPaymentFulfillment.CURRENT_VERSION)
            val idLength = payload[5].toInt() and 0xff
            require(idLength in 1..MAX_ID_LENGTH && 6 + idLength <= payload.size)
            val id = payload.copyOfRange(6, 6 + idLength).decodeToString()
            var offset = 6 + idLength

            require(offset + 2 <= payload.size)
            val tokenLength =
                ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)
            offset += 2
            require(tokenLength in 1..MAX_TOKEN_LENGTH && offset + tokenLength <= payload.size)
            val purchaseToken = payload.copyOfRange(offset, offset + tokenLength).decodeToString()
            offset += tokenLength
            require(offset + 1 <= payload.size)
            val productLength = payload[offset].toInt() and 0xff
            require(productLength in 1..MAX_ID_LENGTH && offset + 1 + productLength <= payload.size)
            val productId = payload.copyOfRange(offset + 1, offset + 1 + productLength).decodeToString()
            offset += 1 + productLength

            fun readChild(): ByteArray {
                require(offset + 4 <= payload.size)
                val length = readInt(payload, offset)
                offset += 4
                require(length >= 0 && offset + length <= payload.size) { "Corrupt payment fulfillment child." }
                return payload.copyOfRange(offset, offset + length).also { section -> offset += section.size }
            }

            WebPendingPaymentFulfillment(
                version = version,
                id = id,
                purchaseToken = purchaseToken,
                productId = productId,
                targetEconomy =
                    WebEconomyCodec.decode(readChild())
                        ?: throw IllegalArgumentException("Corrupt payment fulfillment economy target."),
                targetPayments =
                    WebPaymentsCodec.decode(readChild())
                        ?: throw IllegalArgumentException("Corrupt payment fulfillment payments target."),
            )
        }.getOrNull()

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int = ((source[offset].toInt() and 0xff) shl 24) or
        ((source[offset + 1].toInt() and 0xff) shl 16) or
        ((source[offset + 2].toInt() and 0xff) shl 8) or
        (source[offset + 3].toInt() and 0xff)

    private fun writeShort(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = ((value ushr 8) and 0xff).toByte()
        destination[offset + 1] = (value and 0xff).toByte()
    }

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }
}


/**
 * Payments execution boundary over the Yandex bridge. The Store UI and fulfillment logic never
 * touch raw SDK objects; unsupported environments degrade through null/Unavailable results.
 */
internal interface WebPaymentsProvider {
    suspend fun catalog(): List<PaymentProductSnapshot>?

    suspend fun purchase(productId: String): PaymentResult

    suspend fun pendingPurchases(): List<PaymentPurchaseSnapshot>?

    suspend fun consume(purchaseToken: String): Boolean
}

internal class YandexPaymentsProvider(
    private val bridge: YandexGamesBridge,
) : WebPaymentsProvider {
    override suspend fun catalog(): List<PaymentProductSnapshot>? = bridge.paymentsCatalog()

    override suspend fun purchase(productId: String): PaymentResult = bridge.purchaseProduct(productId)

    override suspend fun pendingPurchases(): List<PaymentPurchaseSnapshot>? = bridge.pendingPurchases()

    override suspend fun consume(purchaseToken: String): Boolean = bridge.consumePurchaseToken(purchaseToken)
}

/** Compact paid-purchase presentation state for the Store's real-money section. */
internal enum class WebPaidPurchaseState {
    Idle,
    Purchasing,
    Fulfilling,
    Saving,
    Success,
    Cancelled,
    Unavailable,
    Error,

    /** Locally durable but the canonical cloud flush has not succeeded yet (recoverable). */
    CloudPending,
}

/** Result of processing one platform purchase through the fulfillment pipeline. */
internal enum class WebPaymentOutcome {
    /** Reward granted exactly once and the token recorded as fulfilled. */
    Fulfilled,

    /** The token was already fulfilled: zero additional gems, only recovery work remains. */
    AlreadyFulfilled,

    /** The build cannot fulfill this product id; the purchase is never consumed. */
    UnknownProduct,

    /** Local Economy/Payments writes failed; the journal stays pending for recovery. */
    PersistenceFailed,

    /** Fulfillment is durable but cloud flush/consume did not succeed yet (retry later). */
    PendingRetry,

    /** Local fulfillment/persistence failed for this attempt (recoverable via journal). */
    Error,

    /** Interactive-only: the user closed the payment frame before paying. */
    Cancelled,

    /** Interactive-only: payments were unavailable for this attempt. */
    Unavailable,
}

internal sealed interface WebPaidCatalogState {
    data object Loading : WebPaidCatalogState

    data object Unavailable : WebPaidCatalogState

    data class Ready(
        val entries: List<WebPaidCatalogEntry>,
    ) : WebPaidCatalogState
}

internal data class WebPaidCatalogEntry(
    val product: WebPaidProduct,
    val details: PaymentProductSnapshot,
)

/**
 * The paid-purchase pipeline: Store UI -> controller -> payments provider -> fulfillment ->
 * Economy + Payments persistence -> unified Cloud Save -> consume.
 *
 * Safety model (45.15): every interactive purchase captures a runtime session id and the
 * current Player context; only that session may update UI state, and the context is
 * re-validated before granting. Durable exactly-once identity is the Yandex purchaseToken,
 * tracked in the Player-scoped fulfilled-token ledger; grant + ledger form one recoverable
 * journal transaction. Consumption happens only after the immediate canonical unified cloud
 * flush succeeds, so an un-consumed purchase always remains recoverable via getPurchases().
 */
internal class WebPaymentsCoordinator(
    private val provider: WebPaymentsProvider,
    private val economyRepository: () -> WebPlayerEconomyRepository?,
    private val paymentsRepository: () -> WebPlayerPaymentsRepository?,
    private val journalStore: () -> WebPaymentsJournalStore?,
    private val revisions: () -> WebPlayerStateRevisions?,
    private val unifiedSaveAccess: () -> WebUnifiedSaveAccess?,
    private val currentPlayerContext: () -> WebPlayerContextToken?,
    private val scope: CoroutineScope,
) {
    private val mutablePurchaseState = MutableStateFlow(WebPaidPurchaseState.Idle)
    val purchaseState: StateFlow<WebPaidPurchaseState> = mutablePurchaseState.asStateFlow()

    private val mutableCatalog = MutableStateFlow<WebPaidCatalogState>(WebPaidCatalogState.Loading)
    val catalogState: StateFlow<WebPaidCatalogState> = mutableCatalog.asStateFlow()

    /** Runtime diagnostics for platform purchases this build cannot fulfill (never consumed). */
    private val mutableUnknownProducts = MutableStateFlow<List<PaymentPurchaseSnapshot>>(emptyList())
    val unknownProducts: StateFlow<List<PaymentPurchaseSnapshot>> = mutableUnknownProducts.asStateFlow()

    private var nextPaymentSessionId = 0L
    private var activePaymentSession: Long? = null
    private var fallbackRevisions: WebPlayerStateRevisions? = null
    private var nextJournalSequence = 0L

    /** Refreshes the Yandex catalog snapshot for the Store's real-money section. */
    fun refreshCatalog() {
        scope.launch {
            mutableCatalog.value = WebPaidCatalogState.Loading
            val catalog = provider.catalog()
            mutableCatalog.value =
                if (catalog == null) {
                    WebPaidCatalogState.Unavailable
                } else {
                    val entries = catalog.mapNotNull { details ->
                        paidProductFor(details.productId)?.let { WebPaidCatalogEntry(it, details) }
                    }
                    WebPaidCatalogState.Ready(entries)
                }
        }
    }

    /** User-initiated purchase of the single supported paid product. */
    fun purchaseGemsSmall() {
        if (activePaymentSession != null) return // one payment session at a time
        val session = ++nextPaymentSessionId
        activePaymentSession = session
        val capturedContext = currentPlayerContext()
        mutablePurchaseState.value = WebPaidPurchaseState.Purchasing
        scope.launch {
            val result = provider.purchase(WebPaidProduct.GEMS_SMALL.yandexProductId)
            onInteractiveResult(session, capturedContext, result)
        }
    }

    private suspend fun onInteractiveResult(
        session: Long,
        capturedContext: WebPlayerContextToken?,
        result: PaymentResult,
    ) {
        if (activePaymentSession != session) return // stale payment session cannot touch UI
        val outcome = when (result) {
            is PaymentResult.Completed -> {
                if (currentPlayerContext() != capturedContext) {
                    // Account changed during the frame: never grant/consume across Players;
                    // the purchase stays recoverable through its owning Player reconcile.
                    activePaymentSession = null
                    mutablePurchaseState.value = WebPaidPurchaseState.Idle
                    return
                }
                mutablePurchaseState.value = WebPaidPurchaseState.Fulfilling
                completeFulfillment(result.purchase)
            }
            PaymentResult.Cancelled -> WebPaymentOutcome.Cancelled
            PaymentResult.Unavailable -> WebPaymentOutcome.Unavailable
            is PaymentResult.Failed -> WebPaymentOutcome.Error
        }
        activePaymentSession = null
        mutablePurchaseState.value = when (outcome) {
            WebPaymentOutcome.Fulfilled, WebPaymentOutcome.AlreadyFulfilled -> WebPaidPurchaseState.Success
            WebPaymentOutcome.PendingRetry -> WebPaidPurchaseState.CloudPending
            WebPaymentOutcome.Cancelled -> WebPaidPurchaseState.Cancelled
            WebPaymentOutcome.Unavailable -> WebPaidPurchaseState.Unavailable
            else -> WebPaidPurchaseState.Error
        }
    }

    /**
     * Full pipeline for one platform purchase: local fulfill (grant+ledger as one recoverable
     * journal transaction) -> immediate canonical unified flush -> consume. Consumption happens
     * only after the flush succeeds; failures converge through later getPurchases() reconciles.
     */
    private suspend fun completeFulfillment(purchase: PaymentPurchaseSnapshot): WebPaymentOutcome {
        val localOutcome = fulfillPurchase(purchase)
        if (localOutcome == WebPaymentOutcome.UnknownProduct || localOutcome == WebPaymentOutcome.PersistenceFailed) {
            return localOutcome // nothing to flush; journal/reconciliation owns recovery
        }
        val flushed = unifiedSaveAccess()?.flushNow() == true
        if (!flushed) return WebPaymentOutcome.PendingRetry // keep reward + ledger, do NOT consume
        if (provider.consume(purchase.purchaseToken)) return localOutcome
        return WebPaymentOutcome.PendingRetry // retried when getPurchases returns this token again
    }

    /** Grants + fulfills one purchase exactly once, keyed by its opaque token. */
    fun fulfillPurchase(purchase: PaymentPurchaseSnapshot): WebPaymentOutcome {
        val economy = economyRepository() ?: return WebPaymentOutcome.PersistenceFailed
        val payments = paymentsRepository() ?: return WebPaymentOutcome.PersistenceFailed
        val journal = journalStore() ?: return WebPaymentOutcome.PersistenceFailed
        val product = paidProductFor(purchase.productId)
        if (product == null) {
            recordUnknown(purchase)
            return WebPaymentOutcome.UnknownProduct // never granted, never consumed
        }
        // Primary duplicate-payment defense: a fulfilled token never pays again.
        if (payments.isFulfilled(purchase.purchaseToken)) return WebPaymentOutcome.AlreadyFulfilled

        val revision = revisions().next()
        val currentEconomy = economy.currentSnapshot
        val targetEconomy =
            currentEconomy.copy(gems = currentEconomy.gems + product.gemReward, revision = revision)
        val currentPayments = payments.snapshot.value
        val targetPayments =
            currentPayments.copy(
                fulfilledTokens = currentPayments.fulfilledTokens + (purchase.purchaseToken to purchase.productId),
            )

        // Journal durability precedes any domain mutation.
        nextJournalSequence += 1
        val fulfillment =
            WebPendingPaymentFulfillment(
                id = "pay-$nextJournalSequence",
                purchaseToken = purchase.purchaseToken,
                productId = purchase.productId,
                targetEconomy = targetEconomy,
                targetPayments = targetPayments,
            )
        if (runCatching { journal.save(fulfillment) }.isFailure) return WebPaymentOutcome.PersistenceFailed

        // Atomic application-level pair: Economy first, Payments second; a failed second side
        // rolls Economy back so only whole consistent pairs are ever observable/published.
        val previousEconomy = economy.currentSnapshot
        when (economy.applyExternal(targetEconomy)) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> Unit
            else -> return WebPaymentOutcome.PersistenceFailed // journal stays pending
        }
        when (payments.applyExternal(targetPayments)) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> Unit
            else -> {
                economy.applyExternal(previousEconomy) // keep the previous durable pair
                return WebPaymentOutcome.PersistenceFailed // journal stays pending
            }
        }
        runCatching { journal.clear() }
        economy.notifyDurableChange() // normal unified-save dirty path (coalesced)
        return WebPaymentOutcome.Fulfilled
    }

    private fun revisions(): WebPlayerStateRevisions {
        val provided = revisions?.invoke()
        if (provided != null) return provided
        return fallbackRevisions ?: WebPlayerStateRevisions().also { fallbackRevisions = it }
    }

    private fun recordUnknown(purchase: PaymentPurchaseSnapshot) {
        mutableUnknownProducts.value =
            (mutableUnknownProducts.value + purchase).distinctBy { it.purchaseToken }
    }

    /**
     * Idempotent recovery of an interrupted paid fulfillment: establishes the exact recorded
     * Economy + Payments targets, then clears the journal. Repeated recovery observes
     * NoChange on both sides and can never add gems or duplicate the token twice.
     */
    fun recoverPendingFulfillment(): Boolean {
        val journal = journalStore() ?: return false
        val pending = runCatching { journal.load() }.getOrNull() ?: return false
        val economy = economyRepository() ?: return false
        val payments = paymentsRepository() ?: return false
        val economyApplied = economy.applyExternal(pending.targetEconomy)
        if (economyApplied != WebExternalRestoreResult.Applied && economyApplied != WebExternalRestoreResult.NoChange) {
            return false // journal stays pending; retried on the next bind
        }
        val paymentsApplied = payments.applyExternal(pending.targetPayments)
        if (paymentsApplied != WebExternalRestoreResult.Applied && paymentsApplied != WebExternalRestoreResult.NoChange) {
            return false // Economy target is idempotent; the next bind finishes Payments
        }
        runCatching { journal.clear() }
        return true
    }

    /**
     * Mandatory pending-purchase reconciliation, run after Player bind/unified establishment:
     * every unconsumed SDK purchase is fulfilled exactly once (known products), flushed to the
     * canonical cloud save, and only then consumed; unknown products are never consumed.
     */
    suspend fun reconcilePendingPurchases() {
        recoverPendingFulfillment()
        val contextAtStart = currentPlayerContext() ?: return
        val purchases = provider.pendingPurchases() ?: return // unsupported/failed: silent no-op
        for (purchase in purchases) {
            if (currentPlayerContext() != contextAtStart) return // stale context: stop safely
            when {
                paidProductFor(purchase.productId) == null -> recordUnknown(purchase)
                else -> completeFulfillment(purchase) // grant-if-needed + flush + consume/retry
            }
        }
    }
}
