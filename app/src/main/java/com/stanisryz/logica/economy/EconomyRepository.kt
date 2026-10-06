package com.stanisryz.logica.economy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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

    /** Credits one watched Store rewarded ad with a gem; `false` when that show already paid. */
    suspend fun grantRewardedGem(actionId: String): Boolean

    /** One confirmed exit from an unfinished attempt with real progress costs a life. */
    suspend fun spendLifeForAbandonedAttempt(actionId: String)

    /**
     * Credits one confirmed store purchase. Both arguments are plain identifiers on purpose: the
     * economy never sees a billing SDK type, and the provider-qualified transaction ID makes the
     * grant idempotent without collisions between store providers.
     */
    suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase

    /** Spends one hint for the hint request identified by [actionId]; a repeat spends nothing. */
    suspend fun consumeHint(actionId: String): EconomyHintUse

    suspend fun buyHintsWithGems(
        actionId: String,
        offer: HintOffer,
    ): EconomyHintPurchase

    /** The one-time and permanent purchases the ledger holds. */
    fun observeOwnedPurchases(): Flow<OwnedPurchases> = flowOf(OwnedPurchases())

    /** Records the permanent «no ads» purchase for [transactionId]; false when it was already recorded. */
    suspend fun grantNoAds(transactionId: String): Boolean = false

    /** The Daily streak days saved so far, as epoch days, read from their ledger rows. */
    fun observeRestoredStreakDays(): Flow<Set<Long>> = flowOf(emptySet())

    /** Saves the streak day [epochDay] for gems or after a watched rewarded ad; a repeat does nothing. */
    suspend fun restoreStreak(
        epochDay: Long,
        withGems: Boolean,
    ): StreakRestoreOutcome = StreakRestoreOutcome.AlreadyRestored

    /** The opened Daily archive days, as epoch day to the moment each was opened. */
    fun observeDailyArchiveUnlocks(): Flow<Map<Long, Long>> = flowOf(emptyMap())

    /** Opens the archive day [epochDay] with [payment]; a repeat does nothing. */
    suspend fun unlockDailyArchive(
        epochDay: Long,
        payment: DailyArchivePayment,
    ): DailyArchiveUnlockOutcome = DailyArchiveUnlockOutcome.AlreadyUnlocked
}

/** How an archive day is opened; [source] is its ledger row's source. */
internal enum class DailyArchivePayment(
    val source: String,
) {
    GEMS("gems"),
    REWARDED("rewarded"),

    /** A day the player already started on its own date is theirs already. */
    FREE("free"),
}

/** What one archive unlock did. */
internal sealed interface DailyArchiveUnlockOutcome {
    data object Unlocked : DailyArchiveUnlockOutcome

    /** That day was already open; nothing was charged again. */
    data object AlreadyUnlocked : DailyArchiveUnlockOutcome

    data class NotEnoughGems(
        val missing: Int,
    ) : DailyArchiveUnlockOutcome
}

/** What one streak save did. */
internal sealed interface StreakRestoreOutcome {
    data object Restored : StreakRestoreOutcome

    /** That day was already saved; nothing was charged again. */
    data object AlreadyRestored : StreakRestoreOutcome

    data class NotEnoughGems(
        val missing: Int,
    ) : StreakRestoreOutcome
}

/** Purchases that change what the Store offers or whether ads show, derived from the ledger. */
internal data class OwnedPurchases(
    val starterPack: Boolean = false,
    val noAds: Boolean = false,
)

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

    override fun observeOwnedPurchases(): Flow<OwnedPurchases> =
        combine(
            dao.observeHasEventType(EconomyEventType.STARTER_PACK_PURCHASE.name),
            dao.observeHasEventType(EconomyEventType.NO_ADS_PURCHASE.name),
        ) { starter, noAds -> OwnedPurchases(starterPack = starter, noAds = noAds) }

    override suspend fun grantNoAds(transactionId: String): Boolean = dao.grantNoAds(transactionId, clock.nowEpochMillis())

    override fun observeRestoredStreakDays(): Flow<Set<Long>> =
        dao.observeEventIds(EconomyEvent.STREAK_RESTORE_PREFIX).map { ids ->
            ids.mapNotNullTo(mutableSetOf()) { it.removePrefix(EconomyEvent.STREAK_RESTORE_PREFIX).toLongOrNull() }
        }

    override suspend fun restoreStreak(
        epochDay: Long,
        withGems: Boolean,
    ): StreakRestoreOutcome = dao.restoreStreak(epochDay, withGems, clock.nowEpochMillis())

    override fun observeDailyArchiveUnlocks(): Flow<Map<Long, Long>> =
        dao.observeEvents(EconomyEvent.DAILY_ARCHIVE_PREFIX).map { events ->
            events
                .mapNotNull { event ->
                    event.eventId
                        .removePrefix(EconomyEvent.DAILY_ARCHIVE_PREFIX)
                        .toLongOrNull()
                        ?.let { it to event.createdAtEpochMillis }
                }.toMap()
        }

    override suspend fun unlockDailyArchive(
        epochDay: Long,
        payment: DailyArchivePayment,
    ): DailyArchiveUnlockOutcome = dao.unlockDailyArchive(epochDay, payment, clock.nowEpochMillis())

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = dao.refillLifeWithGems(actionId, clock.nowEpochMillis())

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = dao.grantRewardedLife(actionId, clock.nowEpochMillis())

    override suspend fun grantRewardedGem(actionId: String): Boolean = dao.grantRewardedGem(actionId, clock.nowEpochMillis())

    override suspend fun spendLifeForAbandonedAttempt(actionId: String) = dao.spendLifeForAbandonedAttempt(actionId, clock.nowEpochMillis())

    override suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase = dao.grantPurchasedGems(purchaseId, productId, clock.nowEpochMillis())

    override suspend fun consumeHint(actionId: String): EconomyHintUse = dao.consumeHint(actionId, clock.nowEpochMillis())

    override suspend fun buyHintsWithGems(
        actionId: String,
        offer: HintOffer,
    ): EconomyHintPurchase = dao.buyHintsWithGems(actionId, offer, clock.nowEpochMillis())
}
