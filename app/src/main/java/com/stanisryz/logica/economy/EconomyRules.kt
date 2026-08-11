package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.model.Difficulty
import java.time.Duration

/**
 * The single source of truth for the offline player economy. Gems, lives, regeneration, and the
 * gem-to-life exchange rate are configured only here; repositories, ViewModels, and Compose read
 * these values instead of restating them.
 */
internal object EconomyRules {
    const val STARTING_GEMS = 0

    const val STARTING_LIVES = 5

    const val MAX_LIVES = 5

    /**
     * What one durable SOLVED attempt is worth. The reward depends only on the difficulty that was
     * completed: it is the same for every puzzle type and the same in Catalog and in Daily.
     */
    fun solvedGemReward(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 4
        }

    /** One durable FAILED attempt costs exactly this many lives, bounded at zero, at any difficulty. */
    const val FAILED_LIFE_PENALTY = 1

    const val LIFE_REFILL_GEM_COST = 10

    /** One missing life comes back after this much elapsed real time. */
    val LIFE_REGENERATION_INTERVAL: Duration = Duration.ofMinutes(15)

    val LIFE_REGENERATION_INTERVAL_MILLIS: Long = LIFE_REGENERATION_INTERVAL.toMillis()
}
