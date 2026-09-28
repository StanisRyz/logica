package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleGemReward
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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
     * What one durable SOLVED attempt is worth: the shared [PuzzleGemReward] table by game and
     * difficulty, the same in Catalog and in Daily.
     */
    fun solvedGemReward(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): Int = PuzzleGemReward.forSolved(puzzleType, difficulty)

    /** One durable FAILED attempt costs exactly this many lives, bounded at zero, at any difficulty. */
    const val FAILED_LIFE_PENALTY = 1

    const val LIFE_REFILL_GEM_COST = 10

    /** What one watched Store rewarded ad is worth. */
    const val REWARDED_AD_GEMS = 1

    /** Hints are a consumable inventory item; a brand-new or migrated player starts with this many. */
    const val STARTING_HINTS = 3

    // Hint prices are provisional placeholders until the pricing pass; only the logic is final.
    const val HINT_SINGLE_GEM_COST = 4
    const val HINT_PACK_SIZE = 3
    const val HINT_PACK_GEM_COST = 10

    /** One missing life comes back after this much elapsed real time. */
    val LIFE_REGENERATION_INTERVAL: Duration = Duration.ofMinutes(30)

    val LIFE_REGENERATION_INTERVAL_MILLIS: Long = LIFE_REGENERATION_INTERVAL.toMillis()
}
