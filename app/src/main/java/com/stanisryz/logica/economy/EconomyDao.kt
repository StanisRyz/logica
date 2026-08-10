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
}
