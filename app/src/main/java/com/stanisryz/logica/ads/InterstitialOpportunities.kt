package com.stanisryz.logica.ads

import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One terminal attempt that has just become durable and may therefore be followed by an ad. */
internal data class InterstitialOpportunity(
    val resultId: String,
)

/**
 * Which finished attempts may be followed by an interstitial: every real game, both outcomes, both
 * scopes. Tutorials produce no [GameCompletion] at all, so they can never reach this.
 */
internal fun GameCompletion.opensInterstitialOpportunity(): Boolean {
    val eligibleOutcome =
        when (outcome) {
            GameOutcome.SOLVED, GameOutcome.FAILED -> true
        }
    val eligibleScope =
        when (sessionScope) {
            GameSessionScope.CATALOG, GameSessionScope.DAILY -> true
        }
    return eligibleOutcome && eligibleScope
}

/**
 * The platform-level seam between "an attempt finished durably" and "an ad may now be shown".
 *
 * It exists so the five puzzle ViewModels stay about gameplay, `GameCompletion`, and durable
 * persistence, and none of them ever touches the Yandex SDK. An opportunity is deliberately
 * ephemeral in-memory state: it is created at the moment a completion succeeds, it is consumed by
 * the live UI, and a process death simply loses it. Historical `GameResult`s are never inspected, so
 * an advertisement missed by a kill is never replayed on the next launch.
 */
internal class InterstitialOpportunities {
    private val offered = BoundedIdSet()
    private val mutablePending = MutableStateFlow<InterstitialOpportunity?>(null)

    /** The one opportunity the live UI may act on, or `null` when there is nothing to show for. */
    val pending: StateFlow<InterstitialOpportunity?> = mutablePending.asStateFlow()

    /** Called only after the completion transaction reported success. */
    fun offer(completion: GameCompletion) {
        if (!completion.opensInterstitialOpportunity()) return
        // An idempotent completion retry re-reports the same result; it is not a second opportunity.
        if (!offered.add(completion.resultId)) return
        mutablePending.value = InterstitialOpportunity(completion.resultId)
    }

    /** Takes the opportunity off the flow so recomposition cannot rediscover it. */
    fun consume(opportunity: InterstitialOpportunity) {
        mutablePending.compareAndSet(opportunity, null)
    }
}

/**
 * The strict ordering the product requires: gameplay reaches a terminal state, the result and its
 * economy transaction are persisted, persistence succeeds, and only then may an ad be considered.
 *
 * Wrapping the completion repository is what makes that ordering structural rather than a rule five
 * ViewModels have to remember. A failed completion throws before [InterstitialOpportunities.offer]
 * is reached, so the existing persistence-retry UI runs with no advertising anywhere near it.
 */
internal class InterstitialAwareGameCompletionRepository(
    private val delegate: GameCompletionRepository,
    private val opportunities: InterstitialOpportunities,
) : GameCompletionRepository {
    override suspend fun complete(completion: GameCompletion): GameResult {
        val result = delegate.complete(completion)
        opportunities.offer(completion)
        return result
    }
}

/**
 * A small "have I already seen this ID" memory that cannot grow without bound during a long session.
 * Only the most recent IDs matter: a result that old can no longer be a live opportunity.
 */
internal class BoundedIdSet(
    private val capacity: Int = CAPACITY,
) {
    private val ids = LinkedHashSet<String>()

    /** `true` when the ID is new. */
    @Synchronized
    fun add(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacity) {
            ids.iterator().let {
                it.next()
                it.remove()
            }
        }
        return true
    }

    private companion object {
        const val CAPACITY = 64
    }
}
