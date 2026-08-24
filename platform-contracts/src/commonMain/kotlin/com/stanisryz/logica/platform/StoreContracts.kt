package com.stanisryz.logica.platform

/** What a purchased Store item grants. New reward kinds extend this enum, not the call sites. */
enum class StoreRewardType {
    HINTS,
    LIFE_RESTORE,
}

/** A positive grant attached to a Store item. */
data class StoreReward(
    val type: StoreRewardType,
    val amount: Int,
) {
    init {
        require(amount > 0) { "A Store reward must be positive." }
    }
}

/** One purchasable Store entry. Prices are internal gems only — never money. */
data class StoreItem(
    val id: String,
    val priceGems: Int,
    val reward: StoreReward,
) {
    init {
        require(id.isNotBlank()) { "A Store item must have an id." }
        require(priceGems > 0) { "A Store item must have a positive gem price." }
    }
}

/** A user-initiated purchase attempt; the Player comes from the caller's scoped context. */
data class PurchaseRequest(
    val playerId: String?,
    val itemId: String,
)

enum class PurchaseStatus {
    SUCCESS,
    INSUFFICIENT_GEMS,
    UNKNOWN_ITEM,
    FAILED,
}

sealed interface PurchaseResult {
    data class Success(
        val item: StoreItem,
        val inventoryItemId: String?,
        val grantedAmount: Int,
    ) : PurchaseResult

    data class Failure(
        val status: PurchaseStatus,
        val item: StoreItem?,
        val requiredGems: Int,
        val availableGems: Int,
    ) : PurchaseResult
}

/**
 * Durable audit trail of one purchase attempt. Successful and failed attempts are both recorded;
 * timestamps are epoch milliseconds supplied by the owning host.
 */
data class PurchaseRecord(
    val itemId: String,
    val priceGems: Int,
    val timestampEpochMs: Long,
    val status: PurchaseStatus,
) {
    init {
        require(itemId.isNotBlank()) { "A purchase record must reference an item." }
        require(priceGems >= 0) { "A purchase record price must not be negative." }
        require(timestampEpochMs >= 0L) { "A purchase record timestamp must not be negative." }
    }

    val successful: Boolean
        get() = status == PurchaseStatus.SUCCESS
}
