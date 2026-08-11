package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.model.Difficulty

/** Why the wallet changed. The ledger and the wallet do not change shape for a new source. */
internal enum class EconomyEventType {
    SOLVED_REWARD,
    FAILED_PENALTY,
    GEM_LIFE_REFILL,

    /** One rewarded ad the player chose to watch at zero lives, credited once. */
    REWARDED_AD_LIFE,

    /** One confirmed RuStore purchase of a gem pack, credited once. */
    RUSTORE_GEM_PURCHASE,
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

        /**
         * RuStore's own purchase ID is the economic identity of a payment, so the ledger row for it
         * is the same whether it arrives from the purchase callback or from reconciliation later.
         */
        fun purchaseEventId(purchaseId: String): String = "rustore:$purchaseId"
    }
}

/** The wallet after one economy event, together with the ledger row that records it. */
internal data class EconomyEffect(
    val economy: PlayerEconomy,
    val event: EconomyEvent,
)

/** The gem reward is derived from the completed difficulty, never from the puzzle type or scope. */
internal fun PlayerEconomy.solvedReward(
    resultId: String,
    difficulty: Difficulty,
): EconomyEffect =
    effect(
        updated = withGemsGranted(EconomyRules.solvedGemReward(difficulty)),
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

/**
 * One paid gem pack. The amount comes from the local [GemPack] table, never from the store payload,
 * and lives are not part of a purchase at all.
 */
internal fun PlayerEconomy.purchasedGems(
    purchaseId: String,
    pack: GemPack,
): EconomyEffect =
    effect(
        updated = withGemsGranted(pack.gems),
        eventId = EconomyEvent.purchaseEventId(purchaseId),
        type = EconomyEventType.RUSTORE_GEM_PURCHASE,
        sourceId = purchaseId,
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
            ),
    )
