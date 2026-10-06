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

    /** Gems that buy back one missing life. */
    const val LIFE_REFILL_GEM_COST = 10

    // Hint prices are provisional placeholders until the pricing pass; only the logic is final.
    const val HINT_SINGLE_GEM_COST = 4
    const val HINT_PACK_SIZE = 3
    const val HINT_PACK_GEM_COST = 10

    /** What one watched rewarded ad pays: a gem in the Store, a life while one is missing. */
    const val REWARDED_AD_GEMS = 1
    const val REWARDED_AD_LIVES = 1

    /** The paid gem packs, smallest first; the reward never comes from store metadata. */
    const val GEM_PACK_SMALL = 50
    const val GEM_PACK_MEDIUM = 150
    const val GEM_PACK_LARGE = 500

    /** The one-time starter pack: these gems and hints, plus every missing life. */
    const val STARTER_PACK_GEMS = 100
    const val STARTER_PACK_HINTS = 5

    /** Saving a Daily streak broken yesterday costs these gems, or one rewarded ad instead. */
    const val STREAK_RESTORE_GEMS = 15

    /** Only a streak at least this long, ending the day before yesterday, can be saved. */
    const val STREAK_RESTORE_MIN_STREAK = 3

    /** Two saved days are at least this many days apart. */
    const val STREAK_RESTORE_COOLDOWN_DAYS = 7

    /** How many past days the Daily archive holds, yesterday included and today not. */
    const val DAILY_ARCHIVE_DAYS = 30

    /** Gems that open one archive day for good; one rewarded ad opens it too. */
    const val DAILY_ARCHIVE_UNLOCK_GEMS = 5

    /**
     * Gems of the Web weekly star tournament by final place, 1 to 20 (the 20 places a Yandex table
     * shows): 100, 50, 25, then 10 for places 4–10 and 5 for 11–20. A week with no stars wins nothing.
     */
    val WEEKLY_PRIZES: List<Int> = listOf(100, 50, 25) + List(7) { 10 } + List(10) { 5 }

    /** The weekly tournament prize of [place], or 0 outside the prize places. */
    fun weeklyPrize(place: Int): Int = WEEKLY_PRIZES.getOrNull(place - 1) ?: 0
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
