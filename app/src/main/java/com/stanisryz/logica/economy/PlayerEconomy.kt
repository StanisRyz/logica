package com.stanisryz.logica.economy

import kotlin.math.min

/**
 * The player's wallet.
 *
 * [nextLifeAtEpochMillis] is the regeneration anchor: the instant the next missing life is due. It
 * exists exactly while a life is missing, so a full wallet never runs a timer and can never
 * accumulate hidden lives above the cap. Losing another life keeps the anchor that is already
 * running rather than restarting the interval.
 */
internal data class PlayerEconomy(
    val gems: Int = EconomyRules.STARTING_GEMS,
    val lives: Int = EconomyRules.STARTING_LIVES,
    val nextLifeAtEpochMillis: Long? = null,
) {
    init {
        require(gems >= 0) { "Gems must not be negative." }
        require(lives in 0..EconomyRules.MAX_LIVES) { "Lives must be within 0..${EconomyRules.MAX_LIVES}." }
        require((lives < EconomyRules.MAX_LIVES) == (nextLifeAtEpochMillis != null)) {
            "A regeneration anchor exists exactly while a life is missing."
        }
    }

    /** The global gameplay gate: a new, resumed, or retried attempt needs at least one life. */
    val isGameplayAllowed: Boolean get() = lives > 0

    val isFull: Boolean get() = lives >= EconomyRules.MAX_LIVES

    val canRefillLifeWithGems: Boolean get() = !isFull && gems >= EconomyRules.LIFE_REFILL_GEM_COST

    /** Remaining wait for the next regenerated life, or `null` while the wallet is full. */
    fun millisUntilNextLife(nowEpochMillis: Long): Long? =
        nextLifeAtEpochMillis?.let {
            (it - nowEpochMillis).coerceIn(0L, EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS)
        }

    /**
     * Restores every life whose whole interval has elapsed and keeps the remaining partial interval
     * running toward the next one. Elapsed time is never negative: a backwards system clock simply
     * restores nothing, and a wait longer than one interval is repaired so a wrong device clock
     * cannot freeze regeneration forever.
     */
    fun regenerated(nowEpochMillis: Long): PlayerEconomy {
        val interval = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
        val dueAt = nextLifeAtEpochMillis ?: return this
        if (nowEpochMillis < dueAt) {
            val latestSaneDueAt = nowEpochMillis + interval
            return if (dueAt > latestSaneDueAt) copy(nextLifeAtEpochMillis = latestSaneDueAt) else this
        }
        val completedIntervals = 1 + (nowEpochMillis - dueAt) / interval
        val restored = min(completedIntervals, (EconomyRules.MAX_LIVES - lives).toLong()).toInt()
        val regeneratedLives = lives + restored
        return if (regeneratedLives >= EconomyRules.MAX_LIVES) {
            copy(lives = EconomyRules.MAX_LIVES, nextLifeAtEpochMillis = null)
        } else {
            copy(lives = regeneratedLives, nextLifeAtEpochMillis = dueAt + restored * interval)
        }
    }

    /** One durable FAILED attempt. An already running countdown keeps its remaining time. */
    fun withLifeSpent(nowEpochMillis: Long): PlayerEconomy {
        if (lives <= 0) return this
        return copy(
            lives = (lives - EconomyRules.FAILED_LIFE_PENALTY).coerceAtLeast(0),
            nextLifeAtEpochMillis =
                nextLifeAtEpochMillis ?: (nowEpochMillis + EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS),
        )
    }

    /** A life from any non-regeneration source; the running countdown continues untouched. */
    fun withLifeRestored(): PlayerEconomy {
        if (isFull) return this
        val restored = lives + 1
        return if (restored >= EconomyRules.MAX_LIVES) {
            copy(lives = EconomyRules.MAX_LIVES, nextLifeAtEpochMillis = null)
        } else {
            copy(lives = restored)
        }
    }

    fun withGemsGranted(amount: Int): PlayerEconomy {
        require(amount >= 0) { "A gem reward must not be negative." }
        return copy(gems = gems + amount)
    }

    fun withGemsSpent(amount: Int): PlayerEconomy {
        require(amount in 0..gems) { "The wallet does not hold enough gems." }
        return copy(gems = gems - amount)
    }
}
