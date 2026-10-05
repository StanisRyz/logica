package com.stanisryz.logica.platform

/**
 * Platform-neutral wallet/lives state. [nextLifeRestoreAtEpochMs] is an epoch-millisecond
 * timestamp reserved for the future life-restore timer; hosts own all clock interpretation.
 */
data class EconomyState(
    val gems: Int,
    val lives: Int,
    val nextLifeRestoreAtEpochMs: Long? = null,
) {
    init {
        require(gems >= 0) { "Gems must never be negative." }
        require(lives in 0..EconomyPolicy.MAXIMUM_LIVES) { "Lives must stay within the supported range." }
    }
}

/** The single place economy constants live for every platform; no platform APIs may enter here. */
object EconomyPolicy {
    /** A new player's welcome gift, the same as on Android. */
    const val STARTING_GEMS = 10

    /** The largest gem balance a wallet keeps; additions saturate here instead of overflowing. */
    const val MAX_GEMS = 1_000_000
    const val STARTING_LIVES = 5
    const val MAXIMUM_LIVES = 5
    const val FAILED_ATTEMPT_LIFE_COST = 1

    /** Hints are a consumable inventory item; a brand-new player starts with this many. */
    const val STARTING_HINTS = 3

    /** One missing life comes back after this much elapsed real time. */
    const val LIFE_RESTORE_INTERVAL_MS = 30L * 60L * 1000L
}

enum class EconomyRewardType {
    GEMS,
    LIFE_RESTORE,
}

enum class EconomyConsumptionType {
    LIFE,
}

/** What happened to the wallet: gameplay outcomes plus the reward/consumption they produced. */
sealed interface EconomyEvent {
    data object GameCompleted : EconomyEvent

    data object GameFailed : EconomyEvent

    data class RewardGranted(
        val type: EconomyRewardType,
        val amount: Int,
    ) : EconomyEvent {
        init {
            require(amount > 0) { "A granted reward must be positive." }
        }
    }

    data class ResourceConsumed(
        val type: EconomyConsumptionType,
        val amount: Int,
    ) : EconomyEvent {
        init {
            require(amount > 0) { "A consumed resource amount must be positive." }
        }
    }
}
