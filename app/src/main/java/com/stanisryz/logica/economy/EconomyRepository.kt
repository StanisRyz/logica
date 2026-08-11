package com.stanisryz.logica.economy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one way the application reads and changes the wallet outside the terminal-completion
 * transaction. Reward and penalty are never applied here by scanning old results: they belong to the
 * Room transaction that persists the result itself.
 */
internal interface EconomyRepository {
    /** The current wallet, with any regeneration that is already due projected onto it. */
    fun observe(): Flow<PlayerEconomy>

    /** Persists the regeneration that elapsed while the app was closed or idle. */
    suspend fun refresh(): PlayerEconomy

    suspend fun refillLifeWithGems(actionId: String): EconomyRefill

    /**
     * Credits the life earned by one rewarded ad. [actionId] belongs to the ad-show attempt, not to
     * the callback, so calling this again with the same value is deliberately a no-op.
     */
    suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife

    /**
     * Credits one confirmed store purchase. Both arguments are plain identifiers on purpose: the
     * economy never sees a billing SDK type, and RuStore's purchase ID is what makes the grant
     * idempotent.
     */
    suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase
}

internal class RoomEconomyRepository(
    private val dao: EconomyDao,
    private val clock: EconomyClock = EconomyClock.SYSTEM,
) : EconomyRepository {
    override fun observe(): Flow<PlayerEconomy> =
        dao.observe().map { stored ->
            val now = clock.nowEpochMillis()
            stored.toPlayerEconomy(now).regenerated(now)
        }

    override suspend fun refresh(): PlayerEconomy = dao.refresh(clock.nowEpochMillis())

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = dao.refillLifeWithGems(actionId, clock.nowEpochMillis())

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = dao.grantRewardedLife(actionId, clock.nowEpochMillis())

    override suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase = dao.grantPurchasedGems(purchaseId, productId, clock.nowEpochMillis())
}
