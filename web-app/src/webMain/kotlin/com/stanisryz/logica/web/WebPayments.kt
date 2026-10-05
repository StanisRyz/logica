package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The application-owned paid products, the same as on Android; the reward never depends on Yandex
 * data. The starter pack also restocks hints and refills lives and is offered until bought once;
 * «no ads» is [permanent]: never consumed, so every bind sees it again in `getPurchases()`.
 */
internal enum class WebPaidProduct(
    val yandexProductId: String,
    val gemReward: Int,
    val hintReward: Int = 0,
    val refillsLives: Boolean = false,
    val permanent: Boolean = false,
) {
    GEMS_50("gems_50", EconomyPolicy.GEM_PACK_SMALL),
    GEMS_150("gems_150", EconomyPolicy.GEM_PACK_MEDIUM),
    GEMS_500("gems_500", EconomyPolicy.GEM_PACK_LARGE),
    STARTER_PACK("starter_pack", EconomyPolicy.STARTER_PACK_GEMS, hintReward = EconomyPolicy.STARTER_PACK_HINTS, refillsLives = true),
    NO_ADS("no_ads", 0, permanent = true),
    ;

    companion object {
        /** The ordinary gem packs, in the Store's order. */
        val GEM_PACKS: List<WebPaidProduct> = listOf(GEMS_50, GEMS_150, GEMS_500)
    }
}

/** Delays of the bounded retries of a failed canonical flush before a paid token is consumed. */
private val FLUSH_RETRY_DELAYS_MS = longArrayOf(2_000L, 8_000L)

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

    /** Whether any fulfilled purchase was [product]: the starter pack's once-only offer, «no ads». */
    fun owns(product: WebPaidProduct): Boolean = fulfilledTokens.containsValue(product.yandexProductId)

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
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
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
    /** The Store inventory after the purchase, for a product that restocks hints; else null. */
    val targetStore: WebStoreSnapshot? = null,
) {
    init {
        require(version in 1..CURRENT_VERSION) { "Unsupported Web payment fulfillment $version." }
        require(id.isNotEmpty()) { "A payment fulfillment needs a stable id." }
        require(purchaseToken.isNotBlank()) { "A payment fulfillment needs its purchase token." }
        require(productId.isNotBlank()) { "A payment fulfillment needs its product id." }
    }

    companion object {
        const val CURRENT_VERSION = 2
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
        // Version 2 always carries a Store child; an empty one means the purchase leaves the Store alone.
        val storePayload = fulfillment.targetStore?.let(WebStoreCodec::encode) ?: ByteArray(0)

        var cursor = 4 + 1 + 1 + idBytes.size + 2 + tokenBytes.size + 1 + productBytes.size
        val result = ByteArray(cursor + 4 + economyPayload.size + 4 + paymentsPayload.size + 4 + storePayload.size)
        magic.copyInto(result)
        result[4] = WebPendingPaymentFulfillment.CURRENT_VERSION.toByte()
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
        cursor += 4 + paymentsPayload.size
        writeInt(result, cursor, storePayload.size)
        storePayload.copyInto(result, cursor + 4)
        return result
    }

    fun decode(payload: ByteArray): WebPendingPaymentFulfillment? =
        runCatching {
            require(payload.size >= 8)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version in 1..WebPendingPaymentFulfillment.CURRENT_VERSION)
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
                targetStore =
                    if (version >= 2) {
                        readChild().takeIf { it.isNotEmpty() }?.let {
                            WebStoreCodec.decode(it) ?: throw IllegalArgumentException("Corrupt payment fulfillment store target.")
                        }
                    } else {
                        null
                    },
            )
        }.getOrNull()

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
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

/**
 * What a successful `getPurchases()` answered in one bound Player context: the products the platform
 * itself confirms. Kept in memory only, per context; another context never reads it.
 */
internal data class WebConfirmedPurchases(
    val context: WebPlayerContextToken,
    val productIds: Set<String>,
)

/**
 * The one ownership rule for a permanent product such as «no ads»: once `getPurchases()` answered
 * successfully in the current Player context, only what it (or a purchase confirmed since) lists
 * counts, so a tampered local ledger entry cannot remove ads; until then — a failed or unsupported
 * query, standalone development — the fulfilled-token ledger decides as before. The ledger itself
 * is never pruned.
 */
internal fun ownsPaidProduct(
    product: WebPaidProduct,
    ledger: WebPaymentsSnapshot?,
    confirmed: WebConfirmedPurchases?,
    currentContext: WebPlayerContextToken?,
): Boolean {
    val live = confirmed?.takeIf { currentContext != null && it.context == currentContext }
    return if (live != null) product.yandexProductId in live.productIds else ledger?.owns(product) == true
}

/** Compact paid-purchase presentation state for the Store's real-money section. */
internal enum class WebPaidPurchaseState {
    Idle,
    Purchasing,
    Fulfilling,
    Success,
    Cancelled,
    Unavailable,
    Error,
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

    /** Fulfillment is durable but the consume call did not succeed yet (retried by the next reconcile). */
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
 * journal transaction. A consumable is consumed after the canonical unified cloud flush and its
 * bounded retries, whether or not the flush succeeded, so no token can ever pay twice.
 */
internal class WebPaymentsCoordinator(
    private val provider: WebPaymentsProvider,
    private val economyRepository: () -> WebPlayerEconomyRepository?,
    private val storeRepository: () -> WebPlayerStoreRepository?,
    private val paymentsRepository: () -> WebPlayerPaymentsRepository?,
    private val journalStore: () -> WebPaymentsJournalStore?,
    private val revisions: () -> WebPlayerStateRevisions?,
    private val unifiedSaveAccess: () -> WebUnifiedSaveAccess?,
    private val currentPlayerContext: () -> WebPlayerContextToken?,
    private val scope: CoroutineScope,
) {
    private val mutablePurchaseState = MutableStateFlow(WebPaidPurchaseState.Idle)
    val purchaseState: StateFlow<WebPaidPurchaseState> = mutablePurchaseState.asStateFlow()

    /** The pack the latest interactive purchase was for, so only its row reports the state. */
    private val mutablePurchasingProduct = MutableStateFlow<WebPaidProduct?>(null)
    val purchasingProduct: StateFlow<WebPaidProduct?> = mutablePurchasingProduct.asStateFlow()

    private val mutableCatalog = MutableStateFlow<WebPaidCatalogState>(WebPaidCatalogState.Loading)
    val catalogState: StateFlow<WebPaidCatalogState> = mutableCatalog.asStateFlow()

    /** Runtime diagnostics for platform purchases this build cannot fulfill (never consumed). */
    private val mutableUnknownProducts = MutableStateFlow<List<PaymentPurchaseSnapshot>>(emptyList())
    val unknownProducts: StateFlow<List<PaymentPurchaseSnapshot>> = mutableUnknownProducts.asStateFlow()

    private val mutableConfirmedPurchases = MutableStateFlow<WebConfirmedPurchases?>(null)

    /** The latest successful `getPurchases()` answer, for [ownsPaidProduct]; memory only. */
    val confirmedPurchases: StateFlow<WebConfirmedPurchases?> = mutableConfirmedPurchases.asStateFlow()

    /** Whether the bound Player owns [product] by [ownsPaidProduct], for ads and the Store alike. */
    fun owns(product: WebPaidProduct): Boolean =
        ownsPaidProduct(product, paymentsRepository()?.snapshot?.value, mutableConfirmedPurchases.value, currentPlayerContext())

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
                    val entries =
                        catalog
                            .mapNotNull { details ->
                                paidProductFor(details.productId)?.let { WebPaidCatalogEntry(it, details) }
                            }.sortedBy { it.product.ordinal }
                    WebPaidCatalogState.Ready(entries)
                }
        }
    }

    /** User-initiated purchase of one paid pack. */
    fun purchase(product: WebPaidProduct) {
        if (activePaymentSession != null) return // one payment session at a time
        val session = ++nextPaymentSessionId
        activePaymentSession = session
        val capturedContext = currentPlayerContext()
        mutablePurchasingProduct.value = product
        mutablePurchaseState.value = WebPaidPurchaseState.Purchasing
        scope.launch {
            val result = provider.purchase(product.yandexProductId)
            onInteractiveResult(session, capturedContext, result)
        }
    }

    private suspend fun onInteractiveResult(
        session: Long,
        capturedContext: WebPlayerContextToken?,
        result: PaymentResult,
    ) {
        if (activePaymentSession != session) return // stale payment session cannot touch UI
        when (result) {
            is PaymentResult.Completed -> {
                if (currentPlayerContext() != capturedContext) {
                    // Account changed during the frame: never grant/consume across Players;
                    // the purchase stays recoverable through its owning Player reconcile.
                    activePaymentSession = null
                    mutablePurchaseState.value = WebPaidPurchaseState.Idle
                    return
                }
                mutablePurchaseState.value = WebPaidPurchaseState.Fulfilling
                // The Store reports the result as soon as the reward is locally durable; the cloud
                // flush retries and the consume call then finish without holding the Store busy.
                completeFulfillment(result.purchase) { localOutcome -> finishInteractive(localOutcome) }
            }
            PaymentResult.Cancelled -> finishInteractive(WebPaymentOutcome.Cancelled)
            PaymentResult.Unavailable -> finishInteractive(WebPaymentOutcome.Unavailable)
            is PaymentResult.Failed -> finishInteractive(WebPaymentOutcome.Error)
        }
    }

    private fun finishInteractive(outcome: WebPaymentOutcome) {
        activePaymentSession = null
        mutablePurchaseState.value =
            when (outcome) {
                WebPaymentOutcome.Fulfilled, WebPaymentOutcome.AlreadyFulfilled -> WebPaidPurchaseState.Success
                WebPaymentOutcome.Cancelled -> WebPaidPurchaseState.Cancelled
                WebPaymentOutcome.Unavailable -> WebPaidPurchaseState.Unavailable
                else -> WebPaidPurchaseState.Error
            }
    }

    /**
     * Full pipeline for one platform purchase: local fulfill (grant+ledger as one recoverable
     * journal transaction) -> canonical unified flush, retried ~2s and ~8s if it fails -> consume
     * regardless. Consuming even without a successful flush means a cleared local ledger can
     * never pay the same token twice; the accepted cost is a purchase lost if the player clears
     * site data before any cloud write lands. Permanent products are never consumed.
     */
    private suspend fun completeFulfillment(
        purchase: PaymentPurchaseSnapshot,
        onLocalOutcome: (WebPaymentOutcome) -> Unit = {},
    ): WebPaymentOutcome {
        val localOutcome = fulfillPurchase(purchase)
        if (localOutcome == WebPaymentOutcome.Fulfilled || localOutcome == WebPaymentOutcome.AlreadyFulfilled) {
            confirmPurchased(purchase.productId) // the platform itself just confirmed it
        }
        onLocalOutcome(localOutcome)
        if (localOutcome == WebPaymentOutcome.UnknownProduct || localOutcome == WebPaymentOutcome.PersistenceFailed) {
            return localOutcome // nothing to flush; journal/reconciliation owns recovery
        }
        // A permanent product is never consumed: it stays in getPurchases() as the proof it is owned,
        // so a purchase the ledger already knows needs no flush either.
        val permanent = paidProductFor(purchase.productId)?.permanent == true
        if (permanent && localOutcome == WebPaymentOutcome.AlreadyFulfilled) return localOutcome
        val context = currentPlayerContext()
        flushWithBoundedRetry()
        if (permanent) return localOutcome
        // Never consume one Player's token from another Player's context; its own next bind does.
        if (currentPlayerContext() != context) return WebPaymentOutcome.PendingRetry
        if (provider.consume(purchase.purchaseToken)) return localOutcome
        return WebPaymentOutcome.PendingRetry // retried when getPurchases returns this token again
    }

    /** One immediate canonical flush, then at most two delayed retries; best effort, never blocking. */
    private suspend fun flushWithBoundedRetry() {
        if (unifiedSaveAccess()?.flushNow() == true) return
        for (delayMs in FLUSH_RETRY_DELAYS_MS) {
            delay(delayMs)
            if (unifiedSaveAccess()?.flushNow() == true) return
        }
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

        val store = if (product.hintReward > 0) storeRepository() ?: return WebPaymentOutcome.PersistenceFailed else null
        val revision = revisions().next()
        val currentEconomy = economy.currentSnapshot
        val targetEconomy =
            currentEconomy.copy(
                gems = saturatedGems(currentEconomy.gems, product.gemReward),
                lives = if (product.refillsLives) EconomyPolicy.MAXIMUM_LIVES else currentEconomy.lives,
                nextLifeRestoreAtEpochMs = if (product.refillsLives) null else currentEconomy.nextLifeRestoreAtEpochMs,
                revision = revision,
            )
        val previousStore = store?.snapshot?.value
        val targetStore =
            previousStore?.let { current ->
                val hints = ((current.inventory[STORE_INVENTORY_HINTS] ?: 0) + product.hintReward).coerceAtMost(MAX_STORE_INVENTORY)
                current.copy(inventory = current.inventory + (STORE_INVENTORY_HINTS to hints), revision = revision)
            }
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
                targetStore = targetStore,
            )
        if (runCatching { journal.save(fulfillment) }.isFailure) return WebPaymentOutcome.PersistenceFailed

        // Atomic application-level group: Economy (with the Store as one pair when hints are part of
        // the product) first, Payments second; a failed second side rolls the first back so only
        // whole consistent states are ever observable/published.
        val previousEconomy = economy.currentSnapshot
        val firstApplied =
            if (store != null && targetStore != null) {
                WebEconomyStorePairApply.apply(economy, store, targetEconomy, targetStore)
            } else {
                economy.applyExternal(targetEconomy).let {
                    it == WebExternalRestoreResult.Applied || it == WebExternalRestoreResult.NoChange
                }
            }
        if (!firstApplied) return WebPaymentOutcome.PersistenceFailed // journal stays pending
        when (payments.applyExternal(targetPayments)) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> Unit
            else -> {
                economy.applyExternal(previousEconomy) // keep the previous durable state
                if (store != null && previousStore != null) store.applyExternal(previousStore)
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

    /** Adds a purchase the platform confirmed in this context to the latest `getPurchases()` answer. */
    private fun confirmPurchased(productId: String) {
        val current = mutableConfirmedPurchases.value ?: return
        if (current.context != currentPlayerContext()) return
        mutableConfirmedPurchases.value = current.copy(productIds = current.productIds + productId)
    }

    private fun recordUnknown(purchase: PaymentPurchaseSnapshot) {
        mutableUnknownProducts.value =
            (mutableUnknownProducts.value + purchase).distinctBy { it.purchaseToken }
    }

    /**
     * Recovery of an interrupted paid fulfillment. The purchase is paid for, so it is never
     * dropped, but its absolute targets are applied only while nothing has changed since:
     * - the token is already in the ledger: the reward landed, only the journal goes;
     * - every wallet side is untouched since the journal (`cur < R`) or already at its target: the
     *   journaled targets are applied (a side at its target is a no-op), then the ledger entry is
     *   added by union, so a reward already in place is never given twice;
     * - otherwise the journal is stale: the product's reward is granted again as a delta on top of
     *   the current state with a fresh revision ([fulfillPurchase], itself journaled), exactly once
     *   per token because the ledger records it.
     */
    fun recoverPendingFulfillment(): Boolean {
        val journal = journalStore() ?: return false
        val pending = runCatching { journal.load() }.getOrNull() ?: return false
        val economy = economyRepository() ?: return false
        val payments = paymentsRepository() ?: return false
        val store = storeRepository()
        // Keep this context's timeline ahead of the journal, whatever happens to it below.
        revisions().raiseTo(pending.targetEconomy.revision)
        if (payments.isFulfilled(pending.purchaseToken)) {
            runCatching { journal.clear() }
            return true
        }
        val targetStore = pending.targetStore
        val journalRevision = pending.targetEconomy.revision
        val economyCurrent = economy.currentSnapshot
        val storeCurrent = store?.snapshot?.value
        val applicable =
            journalSideApplicable(economyCurrent.revision, economyCurrent == pending.targetEconomy, journalRevision) &&
                (
                    targetStore == null ||
                        (storeCurrent != null && journalSideApplicable(storeCurrent.revision, storeCurrent == targetStore, journalRevision))
                )
        if (!applicable) {
            // Stale: a fresh, journaled delta grant replaces this journal (exactly once per token).
            val outcome = fulfillPurchase(PaymentPurchaseSnapshot(pending.purchaseToken, pending.productId))
            return outcome == WebPaymentOutcome.Fulfilled || outcome == WebPaymentOutcome.AlreadyFulfilled
        }
        val firstApplied =
            if (targetStore != null) {
                WebEconomyStorePairApply.apply(economy, store ?: return false, pending.targetEconomy, targetStore)
            } else {
                economy.applyExternal(pending.targetEconomy).let {
                    it == WebExternalRestoreResult.Applied || it == WebExternalRestoreResult.NoChange
                }
            }
        if (!firstApplied) return false // journal stays pending; retried on the next bind
        if (!recordFulfilled(payments, pending)) return false // the wallet target is in place; the ledger follows next time
        economy.notifyDurableChange()
        runCatching { journal.clear() }
        return true
    }

    /** Adds the journal's token to the ledger by union, never dropping a token recorded since. */
    private fun recordFulfilled(
        payments: WebPlayerPaymentsRepository,
        pending: WebPendingPaymentFulfillment,
    ): Boolean =
        when (payments.mergeCloud(WebPaymentsSnapshot(fulfilledTokens = mapOf(pending.purchaseToken to pending.productId)))) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> true
            else -> false
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
        if (currentPlayerContext() == contextAtStart) {
            mutableConfirmedPurchases.value = WebConfirmedPurchases(contextAtStart, purchases.map { it.productId }.toSet())
        }
        for (purchase in purchases) {
            if (currentPlayerContext() != contextAtStart) return // stale context: stop safely
            when {
                paidProductFor(purchase.productId) == null -> recordUnknown(purchase)
                else -> completeFulfillment(purchase) // grant-if-needed + flush + consume/retry
            }
        }
    }
}
