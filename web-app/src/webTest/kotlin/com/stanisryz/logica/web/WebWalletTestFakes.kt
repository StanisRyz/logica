package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory wallet storage for stage 2.1 tests; [failSaves] makes every write throw. */
internal class MemoryEconomyStore(
    var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT,
) : WebEconomyStore {
    var failSaves = false

    override fun load(): WebEconomySnapshot = snapshot

    override fun save(snapshot: WebEconomySnapshot) {
        check(!failSaves) { "Local storage refused the write." }
        this.snapshot = snapshot
    }
}

internal class MemoryItemStore(
    var snapshot: WebStoreSnapshot = WebStoreSnapshot.DEFAULT,
) : WebStoreStore {
    var failSaves = false

    override fun load(): WebStoreSnapshot = snapshot

    override fun save(snapshot: WebStoreSnapshot) {
        check(!failSaves) { "Local storage refused the write." }
        this.snapshot = snapshot
    }
}

internal class MemoryPaymentsStore(
    var snapshot: WebPaymentsSnapshot = WebPaymentsSnapshot.EMPTY,
) : WebPaymentsStore {
    var failSaves = false

    override fun load(): WebPaymentsSnapshot = snapshot

    override fun save(snapshot: WebPaymentsSnapshot) {
        check(!failSaves) { "Local storage refused the write." }
        this.snapshot = snapshot
    }
}

/** A purchase journal whose [clear] can be made to fail silently, like a full localStorage. */
internal class MemoryTransactionJournal : WebPurchaseTransactionStore {
    var stored: WebPurchaseTransaction? = null
    var failClear = false

    override fun load(): WebPurchaseTransaction? = stored

    override fun save(transaction: WebPurchaseTransaction) {
        stored = transaction
    }

    override fun clear() {
        check(!failClear) { "Local storage refused the write." }
        stored = null
    }
}

internal class MemoryFulfillmentJournal : WebPaymentsJournalStore {
    var stored: WebPendingPaymentFulfillment? = null
    var failClear = false

    override fun load(): WebPendingPaymentFulfillment? = stored

    override fun save(fulfillment: WebPendingPaymentFulfillment) {
        stored = fulfillment
    }

    override fun clear() {
        check(!failClear) { "Local storage refused the write." }
        stored = null
    }
}

/** Payments provider for stage 2.1 tests: [pending] null means a failed `getPurchases()`. */
internal class ScriptedPaymentsProvider : WebPaymentsProvider {
    var pending: List<PaymentPurchaseSnapshot>? = emptyList()
    var purchaseResult: PaymentResult = PaymentResult.Unavailable
    val consumedTokens = mutableListOf<String>()

    override suspend fun catalog(): List<PaymentProductSnapshot>? = null

    override suspend fun purchase(productId: String): PaymentResult = purchaseResult

    override suspend fun pendingPurchases(): List<PaymentPurchaseSnapshot>? = pending

    override suspend fun consume(purchaseToken: String): Boolean {
        consumedTokens += purchaseToken
        return true
    }
}

/** Unified save whose [flushNow] answers [flushSucceeds] and counts calls. */
internal class ScriptedUnifiedSave : WebUnifiedSaveAccess {
    override val unifiedSaveActive = true
    override val saveStatus = MutableStateFlow(WebUnifiedSaveStatus.IDLE)
    var flushSucceeds = true
    var flushCalls = 0

    override fun markDirty() = Unit

    override suspend fun flushNow(): Boolean {
        flushCalls += 1
        return flushSucceeds
    }

    override fun invalidateContext() = Unit
}
