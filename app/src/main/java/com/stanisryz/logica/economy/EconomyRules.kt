package com.stanisryz.logica.economy

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleGemReward
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.time.Duration

/**
 * The Android names for the shared economy numbers. Every number comes from [EconomyPolicy], the one
 * source both platforms read; repositories, ViewModels, and Compose use these instead of restating them.
 */
internal object EconomyRules {
    /** A new player's welcome gift: enough for a first hint pack, so the store makes sense from the start. */
    const val STARTING_GEMS = EconomyPolicy.STARTING_GEMS

    const val STARTING_LIVES = EconomyPolicy.STARTING_LIVES

    const val MAX_LIVES = EconomyPolicy.MAXIMUM_LIVES

    /**
     * What one durable SOLVED attempt is worth: the shared [PuzzleGemReward] rule — one gem when an
     * Expert level's best first reaches three stars — the same in Catalog and in Daily.
     */
    fun solvedGemReward(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        stars: Int?,
        previousBestStars: Int?,
    ): Int = PuzzleGemReward.forSolved(puzzleType, difficulty, stars, previousBestStars)

    /** One durable FAILED attempt costs exactly this many lives, bounded at zero, at any difficulty. */
    const val FAILED_LIFE_PENALTY = EconomyPolicy.FAILED_ATTEMPT_LIFE_COST

    const val LIFE_REFILL_GEM_COST = EconomyPolicy.LIFE_REFILL_GEM_COST

    /** Saving a Daily streak broken yesterday: the price in gems, the shortest streak, and the gap between saves. */
    const val STREAK_RESTORE_GEMS = EconomyPolicy.STREAK_RESTORE_GEMS
    const val STREAK_RESTORE_MIN_STREAK = EconomyPolicy.STREAK_RESTORE_MIN_STREAK
    const val STREAK_RESTORE_COOLDOWN_DAYS = EconomyPolicy.STREAK_RESTORE_COOLDOWN_DAYS
    const val DAILY_ARCHIVE_DAYS = EconomyPolicy.DAILY_ARCHIVE_DAYS
    const val DAILY_ARCHIVE_UNLOCK_GEMS = EconomyPolicy.DAILY_ARCHIVE_UNLOCK_GEMS

    /** What one watched Store rewarded ad is worth. */
    const val REWARDED_AD_GEMS = EconomyPolicy.REWARDED_AD_GEMS

    /** Hints are a consumable inventory item; a brand-new or migrated player starts with this many. */
    const val STARTING_HINTS = EconomyPolicy.STARTING_HINTS

    const val HINT_SINGLE_GEM_COST = EconomyPolicy.HINT_SINGLE_GEM_COST
    const val HINT_PACK_SIZE = EconomyPolicy.HINT_PACK_SIZE
    const val HINT_PACK_GEM_COST = EconomyPolicy.HINT_PACK_GEM_COST

    /** One missing life comes back after this much elapsed real time. */
    val LIFE_REGENERATION_INTERVAL: Duration = Duration.ofMillis(EconomyPolicy.LIFE_RESTORE_INTERVAL_MS)

    val LIFE_REGENERATION_INTERVAL_MILLIS: Long = LIFE_REGENERATION_INTERVAL.toMillis()
}
