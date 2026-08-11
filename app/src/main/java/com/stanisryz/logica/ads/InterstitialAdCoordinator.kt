package com.stanisryz.logica.ads

/**
 * The decision half of the interstitial flow, with no Android or Yandex types in it.
 *
 * Everything that decides whether an ad happens lives here: one attempt per terminal result, no
 * waiting for a load, and the five-minute cooldown. The controller supplies the two effects — is an
 * ad ready, and put it on screen — so this contract is the seam tests drive with fakes instead of a
 * real ad network.
 */
internal class InterstitialAdCoordinator(
    private val cooldown: InterstitialCooldownPolicy,
) {
    private val attempted = BoundedIdSet()

    /**
     * At most one show attempt per terminal result, whatever recomposition, repeated state
     * collection, screen recreation, or a duplicated callback does. Returns `true` only when an ad
     * actually went to the SDK; every other path returns immediately so the terminal and result UI
     * is never held up by advertising.
     */
    suspend fun attemptShow(
        opportunity: InterstitialOpportunity,
        isReady: () -> Boolean,
        show: () -> Boolean,
    ): Boolean {
        if (!attempted.add(opportunity.resultId)) return false
        // Nothing loaded is not something to wait for: the result is already durable and on screen.
        if (!isReady()) return false
        if (!cooldown.isEligible()) return false
        return show()
    }
}
