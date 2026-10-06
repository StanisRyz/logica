package com.stanisryz.logica.ads

import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.runCatchingCancellable
import java.util.UUID

/** What a rewarded show pays: a life (Lives dialog, Store), a gem (Store), or a saved streak day (Game Hub). */
internal enum class RewardedAdKind {
    LIFE,
    GEM,
    STREAK_RESTORE,
}

/**
 * The reward bookkeeping of the rewarded placements (a life or a Store gem), with no Yandex or
 * Android types in it.
 *
 * The invariant it exists to hold is `one rewarded ad show -> at most one economy reward`. The action
 * ID belongs to the show attempt and is allocated by [beginShow] before the ad is put on screen,
 * never inside a reward callback: the SDK may deliver `onRewarded` more than once for one show, and
 * every one of those repeats reuses the same ID, which the Room v6 ledger turns into a no-op.
 *
 * Only the official reward callback may call [onRewarded]. Loading, opening, impression, clicking,
 * dismissing, and failing to show all reach the wallet through nothing at all.
 */
internal class RewardedLifeReward(
    private val repository: EconomyRepository,
    private val actionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * The action ID of the most recent show. It deliberately outlives the show so a reward callback
     * arriving after dismissal still lands on the right ID; the next [beginShow] replaces it.
     */
    private var showActionId: String? = null
    private var showKind: RewardedAdKind = RewardedAdKind.LIFE

    /** The streak day a [RewardedAdKind.STREAK_RESTORE] show saves; the day itself keys its ledger row. */
    private var showStreakDay: Long? = null

    /**
     * A reward Yandex already confirmed that the local ledger has not accepted yet. It is retried
     * with its original action ID, so a temporary persistence failure never costs the player a
     * second ad.
     */
    var unpersistedActionId: String? = null
        private set
    private var unpersistedKind: RewardedAdKind = RewardedAdKind.LIFE
    private var unpersistedStreakDay: Long? = null

    /** Allocates the single action ID for one show attempt that pays [kind] ([streakDay] for a streak save). */
    fun beginShow(
        kind: RewardedAdKind = RewardedAdKind.LIFE,
        streakDay: Long? = null,
    ) {
        showActionId = actionIdFactory()
        showKind = kind
        showStreakDay = streakDay
    }

    /** The official reward callback. Returns `true` when the ledger accepted the grant. */
    suspend fun onRewarded(): Boolean {
        val actionId = showActionId ?: return false
        return persist(actionId, showKind, showStreakDay)
    }

    /** Re-attempts the ledger write for a reward the player already earned, if there is one. */
    suspend fun retryUnpersisted(): Boolean {
        val actionId = unpersistedActionId ?: return false
        return persist(actionId, unpersistedKind, unpersistedStreakDay)
    }

    private suspend fun persist(
        actionId: String,
        kind: RewardedAdKind,
        streakDay: Long?,
    ): Boolean =
        runCatchingCancellable {
            when (kind) {
                RewardedAdKind.LIFE -> repository.grantRewardedLife(actionId)
                RewardedAdKind.GEM -> repository.grantRewardedGem(actionId)
                // Keyed by the day, so a second callback for the same show saves nothing more.
                RewardedAdKind.STREAK_RESTORE -> repository.restoreStreak(checkNotNull(streakDay), withGems = false)
            }
        }.onSuccess { unpersistedActionId = null }
            .onFailure {
                unpersistedActionId = actionId
                unpersistedKind = kind
                unpersistedStreakDay = streakDay
            }.isSuccess
}
