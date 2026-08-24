package com.stanisryz.logica.platform

/**
 * Platform-neutral monetization models shared by every ad provider implementation. No SDK or
 * platform types may enter this file: providers translate these contracts into their own calls,
 * and rewards always flow back through the Economy and Store/Inventory systems.
 */
enum class AdKind {
    REWARDED,
    INTERSTITIAL,
}

/** What a completed advertisement grants. Amounts stay small, positive, and inventory-keyed. */
data class AdRewardDefinition(
    val rewardType: StoreRewardType,
    val amount: Int,
) {
    init {
        require(amount > 0) { "An advertisement reward must be positive." }
    }
}

/** Minimal ad request identity for future placement/targeting support. */
data class AdRequest(
    val kind: AdKind,
    val placementId: String,
)

/** The outcome of one provider-level advertisement attempt (never a wallet change by itself). */
sealed interface AdShowResult {
    /** The ad was shown and its completion condition was met (reward earned / fully viewed). */
    data object Completed : AdShowResult

    /** The ad was shown but closed before completion (no reward). */
    data object Dismissed : AdShowResult

    data class Failed(
        val detail: String?,
    ) : AdShowResult

    data object Unavailable : AdShowResult
}

/** Lightweight internal monetization analytics events; no external SDK is attached yet. */
enum class MonetizationAnalyticsEvent {
    AD_STARTED,
    AD_COMPLETED,
    AD_FAILED,
    REWARD_GRANTED,
}
