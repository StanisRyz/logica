package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/** Why the wallet changed. The ledger and the wallet do not change shape for a new source. */
internal enum class EconomyEventType {
    SOLVED_REWARD,
    FAILED_PENALTY,

    /** An unfinished attempt with real progress the player chose to leave. */
    ABANDONED_PENALTY,
    GEM_LIFE_REFILL,

    /** One rewarded ad the player chose to watch for a life, credited once. */
    REWARDED_AD_LIFE,

    /** One rewarded ad the player chose to watch in the Store for a gem, credited once. */
    REWARDED_AD_GEM,

    /** Persisted legacy name for one confirmed store gem purchase, credited once. */
    RUSTORE_GEM_PURCHASE,

    /** One hint taken from the stock by a gameplay hint request. */
    HINT_USED,

    /** Hints bought for gems through a [HintOffer]. */
    GEM_HINT_PURCHASE,

    /** One claimed daily quest, keyed by its day and index. */
    DAILY_QUEST_REWARD,

    /** One claimed daily login gift, keyed by its day; the source carries its cycle day. */
    LOGIN_GIFT,

    /** The one-time gem reward of one reached achievement. */
    ACHIEVEMENT_REWARD,

    /** The one-time starter pack; its row is also what says it was bought. */
    STARTER_PACK_PURCHASE,

    /** The permanent «no ads» purchase; no deltas, its row is the ownership. */
    NO_ADS_PURCHASE,

    /** One saved Daily streak day, paid with gems or a rewarded ad; its row is also the saved day. */
    STREAK_RESTORE,
}

/**
 * One ledger row. [eventId] is derived from the thing that caused it, so the same terminal result or
 * the same purchase action can never affect the wallet twice, and [sourceId] keeps the trail back to
 * that cause. The deltas are what the wallet actually moved, never an unbounded intent.
 */
internal data class EconomyEvent(
    val eventId: String,
    val type: EconomyEventType,
    val sourceId: String?,
    val gemDelta: Int,
    val lifeDelta: Int,
    val hintDelta: Int = 0,
) {
    init {
        require(eventId.isNotBlank()) { "Economy event ID must not be blank." }
    }

    companion object {
        /** A terminal attempt pays for itself exactly once, keyed by its durable result ID. */
        fun resultEventId(resultId: String): String = "result:$resultId"

        /** One intentional purchase gets one action ID, so a repeated callback is a no-op. */
        fun refillEventId(actionId: String): String = "refill:$actionId"

        /**
         * One rewarded-ad show gets one action ID, allocated before the ad is shown rather than
         * inside the reward callback, so a callback delivered twice for that show is a no-op.
         */
        fun rewardedAdEventId(actionId: String): String = "rewarded_ad:$actionId"

        fun rewardedGemEventId(actionId: String): String = "rewarded_gem:$actionId"

        /** One confirmed exit gets one action ID, so a repeated request cannot spend twice. */
        fun abandonEventId(actionId: String): String = "abandon:$actionId"

        /**
         * The store adapter qualifies its transaction ID with the provider, so reconciliation and
         * callbacks share a key while different providers cannot collide.
         */
        fun purchaseEventId(transactionId: String): String = transactionId

        /** One tap on Hint gets one action ID, so a repeated request cannot spend twice. */
        fun hintUseEventId(actionId: String): String = "hint:$actionId"

        fun hintPurchaseEventId(actionId: String): String = "hint_purchase:$actionId"

        /** One quest of one local day pays once. */
        fun questEventId(
            epochDay: Long,
            index: Int,
        ): String = "quest:$epochDay:$index"

        /** One login gift per local day. */
        fun loginGiftEventId(epochDay: Long): String = "login_gift:$epochDay"

        /** One achievement pays once, ever. */
        fun achievementEventId(achievementId: String): String = "achievement:$achievementId"

        /** One saved Daily streak day, keyed by that day, so it is saved and paid for once. */
        fun streakRestoreEventId(epochDay: Long): String = "$STREAK_RESTORE_PREFIX$epochDay"

        const val STREAK_RESTORE_PREFIX = "streak_restore:"

        /** The ledger's source for a day saved with a rewarded ad rather than with gems. */
        const val STREAK_RESTORE_REWARDED_SOURCE = "rewarded"
        const val STREAK_RESTORE_GEMS_SOURCE = "gems"
    }
}

/** The wallet after one economy event, together with the ledger row that records it. */
internal data class EconomyEffect(
    val economy: PlayerEconomy,
    val event: EconomyEvent,
)

/** The gem reward is derived from the completed game, difficulty, and stars, never from the scope. */
internal fun PlayerEconomy.solvedReward(
    resultId: String,
    puzzleType: PuzzleType,
    difficulty: Difficulty,
    stars: Int?,
    previousBestStars: Int?,
): EconomyEffect =
    effect(
        updated = withGemsGranted(EconomyRules.solvedGemReward(puzzleType, difficulty, stars, previousBestStars)),
        eventId = EconomyEvent.resultEventId(resultId),
        type = EconomyEventType.SOLVED_REWARD,
        sourceId = resultId,
    )

internal fun PlayerEconomy.failedPenalty(
    resultId: String,
    nowEpochMillis: Long,
): EconomyEffect =
    effect(
        updated = withLifeSpent(nowEpochMillis),
        eventId = EconomyEvent.resultEventId(resultId),
        type = EconomyEventType.FAILED_PENALTY,
        sourceId = resultId,
    )

/** Leaving an attempt with real progress costs a life exactly like losing it. */
internal fun PlayerEconomy.abandonedPenalty(
    actionId: String,
    nowEpochMillis: Long,
): EconomyEffect =
    effect(
        updated = withLifeSpent(nowEpochMillis),
        eventId = EconomyEvent.abandonEventId(actionId),
        type = EconomyEventType.ABANDONED_PENALTY,
        sourceId = actionId,
    )

internal fun PlayerEconomy.gemLifeRefill(actionId: String): EconomyEffect =
    effect(
        updated = withGemsSpent(EconomyRules.LIFE_REFILL_GEM_COST).withLifeRestored(),
        eventId = EconomyEvent.refillEventId(actionId),
        type = EconomyEventType.GEM_LIFE_REFILL,
        sourceId = actionId,
    )

/**
 * One watched rewarded ad. It restores a life exactly like the gem refill does — the running
 * countdown is preserved and a full wallet simply gains nothing — but costs no gems.
 */
internal fun PlayerEconomy.rewardedAdLife(actionId: String): EconomyEffect =
    effect(
        updated = withLifeRestored(),
        eventId = EconomyEvent.rewardedAdEventId(actionId),
        type = EconomyEventType.REWARDED_AD_LIFE,
        sourceId = actionId,
    )

/** One watched Store rewarded ad: a gem, keyed by its show so a repeated callback adds nothing. */
internal fun PlayerEconomy.rewardedAdGem(actionId: String): EconomyEffect =
    effect(
        updated = withGemsGranted(EconomyRules.REWARDED_AD_GEMS),
        eventId = EconomyEvent.rewardedGemEventId(actionId),
        type = EconomyEventType.REWARDED_AD_GEM,
        sourceId = actionId,
    )

/**
 * One paid gem pack. The amount comes from the local [GemPack] table, never from the store payload,
 * and lives are not part of a purchase at all.
 */
internal fun PlayerEconomy.purchasedGems(
    transactionId: String,
    pack: GemPack,
): EconomyEffect =
    effect(
        updated =
            withGemsGranted(pack.gems).withHintsGranted(pack.hints).let {
                if (pack.refillsLives) it.copy(lives = EconomyRules.MAX_LIVES, nextLifeAtEpochMillis = null) else it
            },
        eventId = EconomyEvent.purchaseEventId(transactionId),
        type = if (pack == GemPack.STARTER_PACK) EconomyEventType.STARTER_PACK_PURCHASE else EconomyEventType.RUSTORE_GEM_PURCHASE,
        sourceId = transactionId,
    )

internal fun PlayerEconomy.hintUsed(actionId: String): EconomyEffect =
    effect(
        updated = withHintSpent(),
        eventId = EconomyEvent.hintUseEventId(actionId),
        type = EconomyEventType.HINT_USED,
        sourceId = actionId,
    )

internal fun PlayerEconomy.gemHintPurchase(
    actionId: String,
    offer: HintOffer,
): EconomyEffect =
    effect(
        updated = withGemsSpent(offer.gemCost).withHintsGranted(offer.hints),
        eventId = EconomyEvent.hintPurchaseEventId(actionId),
        type = EconomyEventType.GEM_HINT_PURCHASE,
        sourceId = actionId,
    )

private fun PlayerEconomy.effect(
    updated: PlayerEconomy,
    eventId: String,
    type: EconomyEventType,
    sourceId: String,
): EconomyEffect =
    EconomyEffect(
        economy = updated,
        event =
            EconomyEvent(
                eventId = eventId,
                type = type,
                sourceId = sourceId,
                gemDelta = updated.gems - gems,
                lifeDelta = updated.lives - lives,
                hintDelta = updated.hints - hints,
            ),
    )
