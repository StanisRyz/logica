package com.stanisryz.logica.ads

import com.stanisryz.logica.economy.EconomyRepository
import java.util.UUID

/**
 * The reward bookkeeping of the rewarded placement, with no Yandex or Android types in it.
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

    /**
     * A reward Yandex already confirmed that the local ledger has not accepted yet. It is retried
     * with its original action ID, so a temporary persistence failure never costs the player a
     * second ad.
     */
    var unpersistedActionId: String? = null
        private set

    /** Allocates the single action ID for one show attempt. */
    fun beginShow() {
        showActionId = actionIdFactory()
    }

    /** The official reward callback. Returns `true` when the ledger accepted the grant. */
    suspend fun onRewarded(): Boolean {
        val actionId = showActionId ?: return false
        return persist(actionId)
    }

    /** Re-attempts the ledger write for a reward the player already earned, if there is one. */
    suspend fun retryUnpersisted(): Boolean {
        val actionId = unpersistedActionId ?: return false
        return persist(actionId)
    }

    private suspend fun persist(actionId: String): Boolean =
        runCatching { repository.grantRewardedLife(actionId) }
            .onSuccess { unpersistedActionId = null }
            .onFailure { unpersistedActionId = actionId }
            .isSuccess
}
