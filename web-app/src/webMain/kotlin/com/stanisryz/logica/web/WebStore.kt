@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PurchaseRecord
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.StoreItem
import com.stanisryz.logica.platform.StoreReward
import com.stanisryz.logica.platform.StoreRewardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Inventory key of the only supported consumable in this foundation stage. */
internal const val STORE_INVENTORY_HINTS = "hints"

/** History stays a compact bounded audit trail; older entries fall off the front. */
internal const val STORE_HISTORY_LIMIT = 50

/**
 * Versioned Player-scoped Store save model: per-item inventory quantities plus the bounded
 * purchase history. Simple by design; future items extend the inventory keys, not the schema.
 */
internal data class WebStoreSnapshot(
    val version: Int = CURRENT_VERSION,
    val inventory: Map<String, Int> = emptyMap(),
    val history: List<PurchaseRecord> = emptyList(),
    /**
     * Monotonic mutation revision from the Player-scoped [WebPlayerStateRevisions] timeline.
     * V1 payloads load as revision `0`; the field lets unified cloud restore compare whole
     * store snapshots instead of blindly overwriting newer local inventory/history.
     */
    val revision: Long = 0L,
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported Web Store schema $version." }
        require(inventory.values.all { it > 0 }) { "Stored inventory quantities must be positive." }
        require(history.size <= STORE_HISTORY_LIMIT) { "Stored purchase history is over budget." }
        require(revision >= 0L) { "Web Store revisions are monotonic and never negative." }
    }

    fun quantityOf(inventoryItemId: String): Int = inventory[inventoryItemId] ?: 0

    companion object {
        const val CURRENT_VERSION = 2

        val DEFAULT = WebStoreSnapshot()
    }
}

/** Deterministic compact binary format with an explicit schema version byte. */
internal object WebStoreCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte())
    private const val MAX_KEY_LENGTH = 64

    fun encode(snapshot: WebStoreSnapshot): ByteArray {
        val inventory = snapshot.inventory.entries.sortedBy { it.key }
        require(inventory.all { it.key.length <= MAX_KEY_LENGTH && it.value in 1..0xff })
        val history = snapshot.history.take(STORE_HISTORY_LIMIT)
        require(history.all { it.itemId.length <= MAX_KEY_LENGTH })

        var size = 4 + 1 + 8 + 1
        inventory.forEach { size += 1 + it.key.encodeToByteArray().size + 1 }
        size += 2
        history.forEach { size += 1 + it.itemId.encodeToByteArray().size + 13 }

        val result = ByteArray(size)
        magic.copyInto(result)
        result[4] = snapshot.version.toByte()
        writeLong(result, 5, snapshot.revision)
        result[13] = inventory.size.toByte()
        var offset = 14
        inventory.forEach { (key, quantity) ->
            val bytes = key.encodeToByteArray()
            result[offset] = bytes.size.toByte()
            bytes.copyInto(result, offset + 1)
            offset += 1 + bytes.size
            result[offset] = quantity.toByte()
            offset += 1
        }
        result[offset] = ((history.size ushr 8) and 0xff).toByte()
        result[offset + 1] = (history.size and 0xff).toByte()
        offset += 2
        history.forEach { record ->
            offset = writeRecord(result, offset, record)
        }
        return result
    }

    private fun writeRecord(
        destination: ByteArray,
        startOffset: Int,
        record: PurchaseRecord,
    ): Int {
        val idBytes = record.itemId.encodeToByteArray()
        destination[startOffset] = idBytes.size.toByte()
        idBytes.copyInto(destination, startOffset + 1)
        var offset = startOffset + 1 + idBytes.size
        writeInt(destination, offset, record.priceGems)
        offset += 4
        writeLong(destination, offset, record.timestampEpochMs)
        offset += 8
        destination[offset] = record.status.ordinal.toByte()
        return offset + 1
    }

    fun decode(payload: ByteArray): WebStoreSnapshot? =
        runCatching {
            require(payload.size >= 8)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version in 1..WebStoreSnapshot.CURRENT_VERSION)

            // V2 prefixes the inventory count with the mutation revision; V1 loads as revision 0.
            var revision = 0L
            var offset = 5
            if (version >= 2) {
                for (index in 0 until 8) {
                    revision = (revision shl 8) or (payload[offset + index].toLong() and 0xff)
                }
                offset += 8
            }
            val inventoryCount = payload[offset].toInt() and 0xff
            offset += 1
            val inventory = linkedMapOf<String, Int>()
            repeat(inventoryCount) {
                require(offset + 2 <= payload.size)
                val keyLength = payload[offset].toInt() and 0xff
                require(keyLength in 1..MAX_KEY_LENGTH)
                val key = payload.copyOfRange(offset + 1, offset + 1 + keyLength).decodeToString()
                offset += 1 + keyLength
                val quantity = payload[offset].toInt() and 0xff
                require(quantity > 0)
                require(inventory.put(key, quantity) == null) { "Duplicate stored inventory item." }
                offset += 1
            }

            require(offset + 2 <= payload.size)
            val historyCount = ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)
            require(historyCount <= STORE_HISTORY_LIMIT)
            offset += 2
            val history = ArrayList<PurchaseRecord>(historyCount)
            repeat(historyCount) {
                require(offset + 2 <= payload.size)
                val idLength = payload[offset].toInt() and 0xff
                require(idLength in 1..MAX_KEY_LENGTH)
                require(offset + 1 + idLength + 13 <= payload.size)
                val itemId = payload.copyOfRange(offset + 1, offset + 1 + idLength).decodeToString()
                offset += 1 + idLength
                val price = readInt(payload, offset)
                offset += 4
                var timestamp = 0L
                for (index in 0 until 8) {
                    timestamp = (timestamp shl 8) or (payload[offset + index].toLong() and 0xff)
                }
                offset += 8
                val status = PurchaseStatus.entries.getOrNull(payload[offset].toInt() and 0xff) ?: PurchaseStatus.FAILED
                offset += 1
                history += PurchaseRecord(itemId, price, timestamp, status)
            }
            WebStoreSnapshot(inventory = inventory, history = history, revision = revision)
        }.getOrNull()

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

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
            ((source[offset + 1].toInt() and 0xff) shl 16) or
            ((source[offset + 2].toInt() and 0xff) shl 8) or
            (source[offset + 3].toInt() and 0xff)

    private fun writeLong(
        destination: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (index in 0 until 8) {
            destination[offset + index] = (value ushr ((7 - index) * 8)).toByte()
        }
    }
}

internal interface WebStoreStore {
    fun load(): WebStoreSnapshot

    fun save(snapshot: WebStoreSnapshot)
}

/** Store uses its own Player-scoped local key and never shares another domain's payload. */
internal class WebStoreLocalStore(
    scope: WebCatalogProgressScope,
) : WebStoreStore {
    internal val storageKey = "$LOCAL_STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebStoreSnapshot {
        val encoded = storeLocalStorageGet(storageKey) ?: return WebStoreSnapshot.DEFAULT
        val payload = WebBase64.decode(encoded) ?: return WebStoreSnapshot.DEFAULT
        return WebStoreCodec.decode(payload) ?: WebStoreSnapshot.DEFAULT
    }

    override fun save(snapshot: WebStoreSnapshot) {
        storeLocalStorageSet(storageKey, WebBase64.encode(WebStoreCodec.encode(snapshot)))
    }

    private companion object {
        const val LOCAL_STORAGE_KEY_PREFIX = "logica_store_v1"
    }
}

private fun storeLocalStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun storeLocalStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

internal fun interface WebStoreRepositoryFactory {
    fun create(scope: WebCatalogProgressScope): WebPlayerStoreRepository
}

/**
 * Player-scoped inventory and purchase history. Mutations persist locally before publication;
 * the wallet itself lives in the economy repository — never here.
 */
internal class WebPlayerStoreRepository(
    val scope: WebCatalogProgressScope,
    private val store: WebStoreStore,
    private val revisions: WebPlayerStateRevisions = WebPlayerStateRevisions(),
) {
    private val mutableSnapshot = MutableStateFlow(WebStoreSnapshot.DEFAULT)
    val snapshot: StateFlow<WebStoreSnapshot> = mutableSnapshot.asStateFlow()

    /** Invoked after every successful durable local mutation; never after a cloud restore. */
    var onDurableChange: (() -> Unit)? = null

    fun loadLocal() {
        val loaded = store.load()
        revisions.raiseTo(loaded.revision)
        mutableSnapshot.value = loaded
    }

    /** Grants a positive quantity of one inventory item. */
    fun grantInventory(
        inventoryItemId: String,
        amount: Int,
    ): Boolean {
        if (amount <= 0 || inventoryItemId.isBlank()) return false
        return mutate { snapshot ->
            snapshot.copy(inventory = snapshot.inventory + (inventoryItemId to snapshot.quantityOf(inventoryItemId) + amount))
        }
    }

    /** Consumes one unit of an inventory item when available. */
    fun consumeInventory(inventoryItemId: String): Boolean {
        if (inventoryItemId.isBlank()) return false
        var consumed = false
        mutate { snapshot ->
            val current = snapshot.quantityOf(inventoryItemId)
            if (current > 0) {
                consumed = true
                val updated =
                    if (current == 1) {
                        snapshot.inventory - inventoryItemId
                    } else {
                        snapshot.inventory + (inventoryItemId to current - 1)
                    }
                snapshot.copy(inventory = updated)
            } else {
                snapshot
            }
        }
        return consumed
    }

    /** Prepends a bounded audit record; history overflow silently drops the oldest entries. */
    fun recordPurchase(record: PurchaseRecord) {
        mutate { snapshot ->
            snapshot.copy(history = (listOf(record) + snapshot.history).take(STORE_HISTORY_LIMIT))
        }
    }

    private inline fun mutate(update: (WebStoreSnapshot) -> WebStoreSnapshot): Boolean {
        // No-op mutations never consume a revision; every real durable change gets exactly one
        // fresh revision from the Player-scoped timeline, published only after local durability.
        val target = update(mutableSnapshot.value)
        if (target == mutableSnapshot.value) return false
        val updated = target.copy(revision = revisions.next())
        // Local durability precedes publication; a failed save leaves the store state untouched.
        runCatching {
            store.save(updated)
        }.onFailure { return false }
        mutableSnapshot.value = updated
        onDurableChange?.invoke()
        return true
    }

    /** Emits one explicit durable-change signal for coupled transaction paths. */
    fun notifyDurableChange() {
        onDurableChange?.invoke()
    }

    /**
     * Restores an externally supplied durable snapshot (unified cloud save or transaction
     * recovery). Durable-first: the snapshot is persisted to Player-scoped local storage and
     * only a successful write updates the observable snapshot and raises the revision timeline.
     * A failed persistence keeps the previous inventory/history authoritative and untouched.
     */
    fun applyExternal(snapshot: WebStoreSnapshot): WebExternalRestoreResult =
        runCatching {
            if (snapshot == mutableSnapshot.value) return@runCatching WebExternalRestoreResult.NoChange as WebExternalRestoreResult
            runCatching { store.save(snapshot) }.getOrElse {
                return@runCatching WebExternalRestoreResult.PersistenceFailed(it) as WebExternalRestoreResult
            }
            revisions.raiseTo(snapshot.revision)
            mutableSnapshot.value = snapshot
            WebExternalRestoreResult.Applied as WebExternalRestoreResult
        }.getOrDefault(WebExternalRestoreResult.Rejected)
}

/** Session-facing Store surface used by the Store UI and gameplay inventory consumption. */
internal interface WebStoreSessionAccess {
    val storeBinding: StateFlow<WebStoreBinding>
}

internal sealed interface WebStoreBinding {
    data object Loading : WebStoreBinding

    data class Ready(
        val token: WebPlayerContextToken,
        val repository: WebPlayerStoreRepository,
        val identity: PlayerIdentity?,
    ) : WebStoreBinding

    data class Unavailable(
        val detail: String,
    ) : WebStoreBinding
}

/**
 * The small static Store catalog, deliberately separated from UI. Prices are internal gems;
 * future items are added here without touching purchase processing or presentation.
 */
internal object WebStoreCatalog {
    const val ITEM_HINT_PACK = "hint_pack"
    const val ITEM_LIFE_RESTORE = "life_restore"

    val ITEMS: List<StoreItem> =
        listOf(
            StoreItem(
                id = ITEM_HINT_PACK,
                priceGems = 10,
                reward = StoreReward(StoreRewardType.HINTS, amount = 3),
            ),
            StoreItem(
                id = ITEM_LIFE_RESTORE,
                priceGems = 10,
                reward = StoreReward(StoreRewardType.LIFE_RESTORE, amount = 1),
            ),
        )

    fun itemById(itemId: String): StoreItem? = ITEMS.firstOrNull { it.id == itemId }
}

/**
 * The purchase pipeline. One successful internal gem purchase is ONE logical transaction: the
 * complete final Economy+Store pair is calculated up front, stamped with a single shared
 * transaction revision, journaled durably before either domain changes, applied as an atomic
 * local pair, then committed by clearing the journal. The economy repository stays the single
 * source of truth for gems; failed purchases never create a committed transaction.
 */
internal class WebStoreProcessor(
    private val economyRepository: () -> WebPlayerEconomyRepository?,
    private val storeRepository: () -> WebPlayerStoreRepository?,
    private val currentTimeMs: () -> Long,
    private val revisionsProvider: (() -> WebPlayerStateRevisions)? = null,
    private val transactionStoreProvider: (() -> WebPurchaseTransactionStore?)? = null,
) {
    private var fallbackRevisions: WebPlayerStateRevisions? = null
    private var nextTransactionSequence = 0L

    fun purchase(
        item: StoreItem?,
        playerId: String?,
    ): PurchaseResult {
        if (item == null) return PurchaseResult.Failure(PurchaseStatus.UNKNOWN_ITEM, null, 0, 0)
        val economy = economyRepository() ?: return PurchaseResult.Failure(PurchaseStatus.FAILED, item, item.priceGems, 0)
        val store = storeRepository() ?: return PurchaseResult.Failure(PurchaseStatus.FAILED, item, item.priceGems, 0)

        // Validation happens on the current durable state; failures never touch either domain.
        val currentEconomy = economy.currentSnapshot
        if (currentEconomy.gems < item.priceGems) {
            recordAttempt(store, item, PurchaseStatus.INSUFFICIENT_GEMS, playerId)
            return PurchaseResult.Failure(PurchaseStatus.INSUFFICIENT_GEMS, item, item.priceGems, currentEconomy.gems)
        }

        // Calculate the complete final pair once — including life-restore items, whose reward
        // conceptually changes the same Economy snapshot twice (gems down, lives up).
        var inventoryItemId: String? = null
        var granted = 0
        val targetLives =
            when (item.reward.type) {
                StoreRewardType.HINTS -> {
                    inventoryItemId = STORE_INVENTORY_HINTS
                    granted = item.reward.amount
                    currentEconomy.lives
                }
                StoreRewardType.LIFE_RESTORE -> {
                    val restored = minOf(item.reward.amount, EconomyPolicy.MAXIMUM_LIVES - currentEconomy.lives)
                    if (restored <= 0) {
                        recordAttempt(store, item, PurchaseStatus.FAILED, playerId)
                        return PurchaseResult.Failure(PurchaseStatus.FAILED, item, item.priceGems, currentEconomy.gems)
                    }
                    granted = restored
                    currentEconomy.lives + restored
                }
            }

        // One shared transaction revision for both sides of this single logical purchase.
        val revision = revisions().next()
        val currentStore = store.snapshot.value
        val targetInventory =
            inventoryItemId?.let { id ->
                currentStore.inventory + (id to currentStore.quantityOf(id) + item.reward.amount)
            } ?: currentStore.inventory
        val transaction =
            WebPurchaseTransaction(
                id = nextTransactionId(),
                revision = revision,
                itemId = item.id,
                priceGems = item.priceGems,
                targetEconomy =
                    currentEconomy.copy(
                        gems = currentEconomy.gems - item.priceGems,
                        lives = targetLives,
                        revision = revision,
                    ),
                targetStore =
                    currentStore.copy(
                        inventory = targetInventory,
                        history =
                            (
                                listOf(
                                    PurchaseRecord(item.id, item.priceGems, currentTimeMs(), PurchaseStatus.SUCCESS),
                                ) + currentStore.history
                            ).take(STORE_HISTORY_LIMIT),
                        revision = revision,
                    ),
            )

        val journal = transactionStore()
        // Journal durability precedes any domain mutation: an interruption after this point is
        // always deterministically recoverable from the stored absolute target pair.
        if (journal == null || runCatching { journal.save(transaction) }.isFailure) {
            recordAttempt(store, item, PurchaseStatus.FAILED, playerId)
            return PurchaseResult.Failure(PurchaseStatus.FAILED, item, item.priceGems, currentEconomy.gems)
        }

        // Atomic application-level pair apply; intermediate steps emit no durable-change signals.
        val applied =
            WebEconomyStorePairApply.apply(economy, store, transaction.targetEconomy, transaction.targetStore)
        if (!applied) {
            // Local persistence failed mid-pair: previous snapshots were restored and the
            // journal stays pending so the next Player bind finishes the transaction exactly.
            return PurchaseResult.Failure(PurchaseStatus.FAILED, item, item.priceGems, economy.currentSnapshot.gems)
        }

        runCatching { journal.clear() }
        // Exactly one meaningful unified-save signal for the whole committed pair.
        economy.notifyDurableChange()
        return PurchaseResult.Success(item, inventoryItemId, granted)
    }

    private fun revisions(): WebPlayerStateRevisions {
        val provided = revisionsProvider?.invoke()
        if (provided != null) return provided
        // Fallback for hosts/tests without a session-bound timeline: still one fresh revision
        // per mutation, just not shared with the Player context lifecycle.
        return fallbackRevisions ?: WebPlayerStateRevisions().also { fallbackRevisions = it }
    }

    private fun transactionStore(): WebPurchaseTransactionStore? = transactionStoreProvider?.invoke()

    private fun nextTransactionId(): String {
        nextTransactionSequence += 1
        return "tx-" + currentTimeMs().toString() + "-" + nextTransactionSequence.toString()
    }

    /** Failed attempts stay Store-only audit history mutations with a normal Store revision. */
    private fun recordAttempt(
        store: WebPlayerStoreRepository,
        item: StoreItem,
        status: PurchaseStatus,
        @Suppress("UNUSED_PARAMETER") playerId: String?,
    ) {
        store.recordPurchase(PurchaseRecord(item.id, item.priceGems, currentTimeMs(), status))
    }

    /** Convenience overload used by the Store UI with a catalog id. */
    fun purchaseById(
        itemId: String,
        playerId: String? = null,
    ): PurchaseResult = purchase(WebStoreCatalog.itemById(itemId), playerId)
}

/**
 * One recoverable coupled Economy+Store purchase transaction. The journal is authoritative for
 * recovery: it stores the complete final target pair so an interruption between local writes
 * can be finished deterministically without inferring intent from balances.
 */
internal data class WebPurchaseTransaction(
    val version: Int = CURRENT_VERSION,
    val id: String,
    val revision: Long,
    val kind: Int = KIND_GEM_PURCHASE,
    val itemId: String,
    val priceGems: Int,
    val targetEconomy: WebEconomySnapshot,
    val targetStore: WebStoreSnapshot,
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported Web purchase transaction $version." }
        require(id.isNotEmpty()) { "A purchase transaction needs a stable id." }
        require(revision > 0L) { "A purchase transaction revision is always allocated." }
        require(kind == KIND_GEM_PURCHASE) { "Unsupported purchase transaction kind $kind." }
        require(itemId.isNotEmpty()) { "A purchase transaction needs its Store item id." }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val KIND_GEM_PURCHASE = 1
    }
}

/** Player-scoped durable journal for one pending coupled purchase transaction. */
internal interface WebPurchaseTransactionStore {
    fun load(): WebPurchaseTransaction?

    fun save(transaction: WebPurchaseTransaction)

    fun clear()
}

/** Browser implementation over one Player-scoped localStorage key. */
internal class BrowserWebPurchaseTransactionStore(
    scope: WebCatalogProgressScope,
) : WebPurchaseTransactionStore {
    private val storageKey = "$STORAGE_KEY_PREFIX:${scope.keySuffix}"

    override fun load(): WebPurchaseTransaction? {
        val encoded = purchaseTxStorageGet(storageKey) ?: return null
        val payload = WebBase64.decode(encoded) ?: return null
        return WebPurchaseTransactionCodec.decode(payload)
    }

    override fun save(transaction: WebPurchaseTransaction) {
        purchaseTxStorageSet(storageKey, WebBase64.encode(WebPurchaseTransactionCodec.encode(transaction)))
    }

    override fun clear() {
        purchaseTxStorageSet(storageKey, "")
    }

    private companion object {
        const val STORAGE_KEY_PREFIX = "logica_purchase_tx_v1"
    }
}

private fun purchaseTxStorageGet(key: String): String? = js("globalThis.localStorage.getItem(key)")

private fun purchaseTxStorageSet(
    key: String,
    value: String,
) {
    js("globalThis.localStorage.setItem(key, value)")
}

/** Deterministic compact binary format for the pending-transaction journal. */
internal object WebPurchaseTransactionCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'T'.code.toByte(), 'X'.code.toByte())
    private const val MAX_ID_LENGTH = 96

    fun encode(transaction: WebPurchaseTransaction): ByteArray {
        val idBytes = transaction.id.encodeToByteArray()
        val itemBytes = transaction.itemId.encodeToByteArray()
        require(idBytes.size in 1..MAX_ID_LENGTH && itemBytes.size in 1..MAX_ID_LENGTH)
        val economyPayload = WebEconomyCodec.encode(transaction.targetEconomy)
        val storePayload = WebStoreCodec.encode(transaction.targetStore)

        var offset = 4 + 1 + 1 + idBytes.size + 8 + 1 + 1 + itemBytes.size + 4
        val result = ByteArray(offset + 4 + economyPayload.size + 4 + storePayload.size)
        magic.copyInto(result)
        result[4] = transaction.version.toByte()
        result[5] = idBytes.size.toByte()
        idBytes.copyInto(result, 6)
        var cursor = 6 + idBytes.size
        writeLong(result, cursor, transaction.revision)
        cursor += 8
        result[cursor] = transaction.kind.toByte()
        cursor += 1
        result[cursor] = itemBytes.size.toByte()
        itemBytes.copyInto(result, cursor + 1)
        cursor += 1 + itemBytes.size
        writeInt(result, cursor, transaction.priceGems)
        cursor += 4
        writeInt(result, cursor, economyPayload.size)
        economyPayload.copyInto(result, cursor + 4)
        cursor += 4 + economyPayload.size
        writeInt(result, cursor, storePayload.size)
        storePayload.copyInto(result, cursor + 4)
        return result
    }

    fun decode(payload: ByteArray): WebPurchaseTransaction? =
        runCatching {
            require(payload.size >= 10)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = payload[4].toInt() and 0xff
            require(version == WebPurchaseTransaction.CURRENT_VERSION)
            val idLength = payload[5].toInt() and 0xff
            require(idLength in 1..MAX_ID_LENGTH && 6 + idLength <= payload.size)
            val id = payload.copyOfRange(6, 6 + idLength).decodeToString()
            var offset = 6 + idLength
            require(offset + 8 <= payload.size)
            var revision = 0L
            repeat(8) { index ->
                revision = (revision shl 8) or (payload[offset + index].toLong() and 0xff)
            }
            offset += 8
            val kind = payload[offset].toInt() and 0xff
            offset += 1
            val itemLength = payload[offset].toInt() and 0xff
            require(itemLength in 1..MAX_ID_LENGTH && offset + 1 + itemLength + 4 <= payload.size)
            val itemId = payload.copyOfRange(offset + 1, offset + 1 + itemLength).decodeToString()
            offset += 1 + itemLength
            val priceGems = readInt(payload, offset)
            offset += 4

            fun readSection(): ByteArray {
                require(offset + 4 <= payload.size)
                val length = readInt(payload, offset)
                offset += 4
                require(length >= 0 && offset + length <= payload.size) { "Corrupt purchase transaction section." }
                return payload.copyOfRange(offset, offset + length).also { section -> offset += section.size }
            }

            WebPurchaseTransaction(
                version = version,
                id = id,
                revision = revision,
                kind = kind,
                itemId = itemId,
                priceGems = priceGems,
                targetEconomy =
                    WebEconomyCodec.decode(readSection())
                        ?: throw IllegalArgumentException("Corrupt purchase transaction economy target."),
                targetStore =
                    WebStoreCodec.decode(readSection())
                        ?: throw IllegalArgumentException("Corrupt purchase transaction store target."),
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

    private fun writeLong(
        destination: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (index in 0 until 8) {
            destination[offset + index] = (value ushr ((7 - index) * 8)).toByte()
        }
    }
}

/**
 * Application-level atomic Economy+Store pair apply over browser local storage: each side is
 * persisted durably before it becomes observable, and a failed second side rolls the first
 * side back to the previous durable snapshot, so only whole pairs are ever observable.
 */
internal object WebEconomyStorePairApply {
    fun apply(
        economy: WebPlayerEconomyRepository,
        store: WebPlayerStoreRepository,
        targetEconomy: WebEconomySnapshot,
        targetStore: WebStoreSnapshot,
    ): Boolean {
        val previousEconomy = economy.currentSnapshot
        when (economy.applyExternal(targetEconomy)) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> Unit
            else -> return false
        }
        when (store.applyExternal(targetStore)) {
            WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> return true
            else -> {
                // Keep the previous local pair authoritative; never leave a half-applied pair.
                economy.applyExternal(previousEconomy)
                return false
            }
        }
    }
}

/**
 * Idempotent finish of an interrupted purchase: re-establishes the journaled target Economy +
 * Store pair (durable-first, absolute snapshots), then clears the journal. Repeated recovery
 * observes NoChange on both sides and can never deduct gems or grant inventory twice.
 */
internal object WebPurchaseTransactionRecovery {
    fun recover(
        transaction: WebPurchaseTransaction,
        economy: WebPlayerEconomyRepository,
        store: WebPlayerStoreRepository,
        journal: WebPurchaseTransactionStore,
    ): Boolean {
        val recovered =
            WebEconomyStorePairApply.apply(economy, store, transaction.targetEconomy, transaction.targetStore)
        if (recovered) journal.clear()
        return recovered
    }
}
