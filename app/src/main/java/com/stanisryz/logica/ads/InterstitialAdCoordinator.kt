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

/**
 * When the interstitial happens: not the moment a game ends, but the moment the player takes their
 * first action on the finished attempt — Retry, New game, To Games. The result and its economy
 * effect are durable and the terminal card has been on screen long enough to be read before any ad
 * appears, and the action the player asked for is what continues afterwards.
 *
 * This is the deferral half of the flow and holds no Android or Yandex types either: [present] is
 * the controller's one effect — put this opportunity on screen if it can, and call back exactly once
 * either way — so an unavailable, failed, or cooled-down ad simply continues the action immediately.
 */
internal class TerminalActionCoordinator(
    private val pendingOpportunity: () -> InterstitialOpportunity?,
    private val present: (InterstitialOpportunity, () -> Unit) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private var adInFlight = false
    private var lastActionAt: Long? = null

    /**
     * Runs one terminal action, possibly behind an ad. A second tap while an ad is on screen, or a
     * bounced double tap on the same button, does nothing at all: one tap is one retry, one
     * navigation, and at most one advertisement.
     */
    fun run(action: () -> Unit) {
        if (adInFlight) return
        val now = nowMillis()
        if (lastActionAt?.let { now - it < REPEAT_TAP_WINDOW_MILLIS } == true) return
        lastActionAt = now
        val opportunity = pendingOpportunity()
        if (opportunity == null) {
            action()
            return
        }
        adInFlight = true
        var continued = false
        present(opportunity) {
            if (continued) return@present
            continued = true
            adInFlight = false
            action()
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** A bounced double tap arrives well inside this; a deliberate second action never does. */
        const val REPEAT_TAP_WINDOW_MILLIS = 700L
    }
}
