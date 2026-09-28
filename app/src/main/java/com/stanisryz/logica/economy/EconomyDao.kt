package com.stanisryz.logica.economy

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** Why a gem-to-life exchange did not happen; every reason is re-checked inside the transaction. */
internal enum class EconomyRefillRejection {
    LIVES_FULL,
    NOT_ENOUGH_GEMS,

    /** The same purchase action was already applied, so repeating it is a safe no-op. */
    ALREADY_APPLIED,
}

internal sealed interface EconomyRefill {
    val economy: PlayerEconomy

    data class Applied(
        override val economy: PlayerEconomy,
    ) : EconomyRefill

    data class Rejected(
        override val economy: PlayerEconomy,
        val reason: EconomyRefillRejection,
    ) : EconomyRefill
}

/**
 * The outcome of persisting one rewarded ad. It is never a rejection: the player already watched the
 * ad, so the only questions are whether this action ID was new and whether the wallet had room.
 */
internal sealed interface EconomyRewardedLife {
    val economy: PlayerEconomy

    /**
     * The action ID reached the ledger for the first time. [lifeGranted] is false only when the
     * wallet was already full by the time the reward callback arrived.
     */
    data class Granted(
        override val economy: PlayerEconomy,
        val lifeGranted: Boolean,
    ) : EconomyRewardedLife

    /** The same rewarded show already moved through the ledger, so this callback changed nothing. */
    data class AlreadyGranted(
        override val economy: PlayerEconomy,
    ) : EconomyRewardedLife
}

/** The outcome of crediting one confirmed platform purchase. */
internal sealed interface EconomyGemPurchase {
    val economy: PlayerEconomy

    /** The purchase reached the ledger for the first time and [pack] worth of gems were added. */
    data class Granted(
        override val economy: PlayerEconomy,
        val pack: GemPack,
    ) : EconomyGemPurchase

    /**
     * This purchase ID already moved through the ledger, so nothing was added. It is the normal
     * result of reconciling a purchase that was credited before the process died.
     */
    data class AlreadyGranted(
        override val economy: PlayerEconomy,
    ) : EconomyGemPurchase

    /** The store sold a product this build has no reward for; zero gems, and nothing is inferred. */
    data class UnsupportedProduct(
        override val economy: PlayerEconomy,
        val productId: String,
    ) : EconomyGemPurchase
}

/** The outcome of one gameplay hint request against the consumable hint stock. */
internal sealed interface EconomyHintUse {
    val economy: PlayerEconomy

    /** One hint left the stock for this [actionId]; the caller may now apply the hint. */
    data class Used(
        override val economy: PlayerEconomy,
    ) : EconomyHintUse

    /** The same action already spent its hint, so this repeat spent nothing more. */
    data class AlreadyUsed(
        override val economy: PlayerEconomy,
    ) : EconomyHintUse

    /** The stock is empty: nothing was spent and the hint must not be applied. */
    data class NoHints(
        override val economy: PlayerEconomy,
    ) : EconomyHintUse
}

/** The outcome of buying hints for gems. */
internal sealed interface EconomyHintPurchase {
    val economy: PlayerEconomy

    data class Applied(
        override val economy: PlayerEconomy,
        val offer: HintOffer,
    ) : EconomyHintPurchase

    data class NotEnoughGems(
        override val economy: PlayerEconomy,
    ) : EconomyHintPurchase

    /** The same purchase action was already applied, so repeating it is a safe no-op. */
    data class AlreadyApplied(
        override val economy: PlayerEconomy,
    ) : EconomyHintPurchase
}

@Dao
internal interface EconomyDao {
    // The wallet is a singleton row; `PlayerEconomyEntity.SINGLETON_ID` is that ID.
    @Query("SELECT * FROM player_economy WHERE economy_id = 1 LIMIT 1")
    fun observe(): Flow<PlayerEconomyEntity?>

    @Query("SELECT * FROM player_economy WHERE economy_id = 1 LIMIT 1")
    suspend fun find(): PlayerEconomyEntity?

    @Upsert
    suspend fun upsert(economy: PlayerEconomyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: EconomyEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM economy_events WHERE event_id = :eventId)")
    suspend fun hasEvent(eventId: String): Boolean

    /** Persists whatever regeneration is already due; the wallet is seeded on first use. */
    @Transaction
    suspend fun refresh(nowEpochMillis: Long): PlayerEconomy {
        val stored = find()
        val current = stored.toPlayerEconomy(nowEpochMillis)
        val regenerated = current.regenerated(nowEpochMillis)
        if (stored == null || regenerated != current) upsert(regenerated.toEntity(nowEpochMillis))
        return regenerated
    }

    /**
     * Buys one life for [EconomyRules.LIFE_REFILL_GEM_COST] gems. The balance is re-read here rather
     * than trusted from the UI, and the ledger insert makes a repeated callback for the same
     * [actionId] a no-op instead of a second purchase.
     */
    @Transaction
    suspend fun refillLifeWithGems(
        actionId: String,
        nowEpochMillis: Long,
    ): EconomyRefill {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        // The repeat of an applied purchase is reported before the balance is judged: after the first
        // one succeeded the gems are already gone, which is not the same as never affording it.
        if (hasEvent(EconomyEvent.refillEventId(actionId))) {
            return EconomyRefill.Rejected(current, EconomyRefillRejection.ALREADY_APPLIED)
        }
        if (!current.canRefillLifeWithGems) {
            val reason =
                if (current.isFull) EconomyRefillRejection.LIVES_FULL else EconomyRefillRejection.NOT_ENOUGH_GEMS
            return EconomyRefill.Rejected(current, reason)
        }
        val effect = current.gemLifeRefill(actionId)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) {
            return EconomyRefill.Rejected(current, EconomyRefillRejection.ALREADY_APPLIED)
        }
        upsert(effect.economy.toEntity(nowEpochMillis))
        return EconomyRefill.Applied(effect.economy)
    }

    /**
     * Credits one rewarded ad. The wallet is re-read here, so regeneration that completed while the
     * ad was on screen is applied first and the earned life is added on top of it rather than
     * replacing it; the cap still holds. The ledger insert is the safety boundary: a reward callback
     * delivered twice for the same [actionId] is a no-op, and a full wallet still records the event
     * so the consumed ad is never owed a second life.
     */
    @Transaction
    suspend fun grantRewardedLife(
        actionId: String,
        nowEpochMillis: Long,
    ): EconomyRewardedLife {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        val effect = current.rewardedAdLife(actionId)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) {
            // Whatever regeneration is due is still worth persisting; the reward itself is spent.
            upsert(current.toEntity(nowEpochMillis))
            return EconomyRewardedLife.AlreadyGranted(current)
        }
        upsert(effect.economy.toEntity(nowEpochMillis))
        return EconomyRewardedLife.Granted(effect.economy, lifeGranted = effect.event.lifeDelta > 0)
    }

    /** Spends one life for one confirmed exit from an unfinished attempt; a repeat spends nothing. */
    @Transaction
    suspend fun spendLifeForAbandonedAttempt(
        actionId: String,
        nowEpochMillis: Long,
    ) {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        val effect = current.abandonedPenalty(actionId, nowEpochMillis)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) return
        upsert(effect.economy.toEntity(nowEpochMillis))
    }

    /** Credits one watched Store rewarded ad; the ledger row keyed by [actionId] pays it once. */
    @Transaction
    suspend fun grantRewardedGem(
        actionId: String,
        nowEpochMillis: Long,
    ): Boolean {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        val effect = current.rewardedAdGem(actionId)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) return false
        upsert(effect.economy.toEntity(nowEpochMillis))
        return true
    }

    /**
     * Spends one hint for one gameplay hint request. The stock is re-read here, and the ledger row
     * keyed by [actionId] makes a repeated request for the same tap spend nothing more.
     */
    @Transaction
    suspend fun consumeHint(
        actionId: String,
        nowEpochMillis: Long,
    ): EconomyHintUse {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        if (hasEvent(EconomyEvent.hintUseEventId(actionId))) return EconomyHintUse.AlreadyUsed(current)
        if (current.hints <= 0) return EconomyHintUse.NoHints(current)
        val effect = current.hintUsed(actionId)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) return EconomyHintUse.AlreadyUsed(current)
        upsert(effect.economy.toEntity(nowEpochMillis))
        return EconomyHintUse.Used(effect.economy)
    }

    /**
     * Buys [offer] hints for its gem price. The balance is re-read inside the transaction, and the
     * ledger row keyed by [actionId] turns a repeated tap into a no-op instead of a second purchase.
     */
    @Transaction
    suspend fun buyHintsWithGems(
        actionId: String,
        offer: HintOffer,
        nowEpochMillis: Long,
    ): EconomyHintPurchase {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        if (hasEvent(EconomyEvent.hintPurchaseEventId(actionId))) return EconomyHintPurchase.AlreadyApplied(current)
        if (current.gems < offer.gemCost) return EconomyHintPurchase.NotEnoughGems(current)
        val effect = current.gemHintPurchase(actionId, offer)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) {
            return EconomyHintPurchase.AlreadyApplied(current)
        }
        upsert(effect.economy.toEntity(nowEpochMillis))
        return EconomyHintPurchase.Applied(effect.economy, offer)
    }

    /**
     * Credits one confirmed store purchase. [productId] is an application pack key checked against
     * the [GemPack] whitelist before anything else, so the gem amount is always this build's number
     * rather than the store's. The ledger row keyed by [transactionId] is the duplicate boundary: the
     * same payment reaching this method again — from a repeated callback, from reconciliation after
     * a crash, or from a finalization retry — adds nothing. Lives are never touched by a purchase.
     */
    @Transaction
    suspend fun grantPurchasedGems(
        transactionId: String,
        productId: String,
        nowEpochMillis: Long,
    ): EconomyGemPurchase {
        val current = find().toPlayerEconomy(nowEpochMillis).regenerated(nowEpochMillis)
        val pack =
            GemPack.forKey(productId)
                ?: return EconomyGemPurchase.UnsupportedProduct(current, productId)
        val effect = current.purchasedGems(transactionId, pack)
        if (insertEvent(effect.event.toEntity(nowEpochMillis)) == -1L) {
            // Whatever regeneration is due is still worth persisting; the gems already arrived.
            upsert(current.toEntity(nowEpochMillis))
            return EconomyGemPurchase.AlreadyGranted(current)
        }
        upsert(effect.economy.toEntity(nowEpochMillis))
        return EconomyGemPurchase.Granted(effect.economy, pack)
    }
}
