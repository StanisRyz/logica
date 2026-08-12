package com.stanisryz.logica.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The deferral the product asks for: a finished game no longer opens an ad by itself. The player
 * sees the result, and the first terminal action they choose — Retry, a new game, the Game hub — is
 * what an eligible interstitial may come in front of, with that same action continuing afterwards.
 */
class DeferredInterstitialTerminalActionTest {
    @Test
    fun `a pending opportunity is only spent by the first terminal action and its action continues`() {
        val world = TerminalActionWorld()
        world.pending = InterstitialOpportunity("result-1")

        // The result is durable and the terminal card is on screen: nothing has been shown yet.
        assertEquals(0, world.presented)
        assertEquals(emptyList<String>(), world.performed)

        world.coordinator.run { world.performed += "retry" }

        // The tap consumed the one opportunity, the ad went up, and the retry waited behind it.
        assertEquals(1, world.presented)
        assertEquals(emptyList<String>(), world.performed)
        assertNull(world.pending)

        world.dismissAd()
        assertEquals(listOf("retry"), world.performed)

        // A later action on the same finished attempt has nothing left to spend.
        world.advance(SECOND_TAP_MILLIS)
        world.coordinator.run { world.performed += "toGames" }
        assertEquals(1, world.presented)
        assertEquals(listOf("retry", "toGames"), world.performed)
    }

    @Test
    fun `an unavailable ad never blocks the player`() {
        val world = TerminalActionWorld(adAppears = false)
        world.pending = InterstitialOpportunity("result-1")

        world.coordinator.run { world.performed += "newPuzzle" }

        // Not loaded, still cooling down, or failing to appear all look the same from here: the
        // opportunity is spent, no ad is on screen, and the action happens immediately.
        assertEquals(1, world.presented)
        assertEquals(listOf("newPuzzle"), world.performed)
        assertNull(world.pending)
    }

    @Test
    fun `a double tap causes neither two ads nor two actions`() {
        val world = TerminalActionWorld()
        world.pending = InterstitialOpportunity("result-1")

        world.coordinator.run { world.performed += "retry" }
        // The bounced second tap arrives while the ad is on screen.
        world.coordinator.run { world.performed += "retry" }
        world.dismissAd()
        // And a third one right after it, on the finished action.
        world.coordinator.run { world.performed += "retry" }

        assertEquals(1, world.presented)
        assertEquals(listOf("retry"), world.performed)

        // A deliberate later action is not a double tap and still works, ad or no ad.
        world.advance(SECOND_TAP_MILLIS)
        world.coordinator.run { world.performed += "toGames" }
        assertEquals(listOf("retry", "toGames"), world.performed)
        assertEquals(1, world.presented)
    }

    private companion object {
        const val SECOND_TAP_MILLIS = 5_000L
    }
}

/** The shell's terminal gate with the controller's one effect faked: show, then call back once. */
private class TerminalActionWorld(
    private val adAppears: Boolean = true,
) {
    var pending: InterstitialOpportunity? = null
    var presented = 0
        private set
    val performed = mutableListOf<String>()

    private var now = 0L
    private var onAdClosed: (() -> Unit)? = null

    val coordinator =
        TerminalActionCoordinator(
            pendingOpportunity = { pending },
            present = { opportunity, onFinished ->
                presented++
                // The real controller consumes the opportunity before it reaches the SDK.
                if (pending == opportunity) pending = null
                if (adAppears) onAdClosed = onFinished else onFinished()
            },
            nowMillis = { now },
        )

    fun dismissAd() {
        val closed = onAdClosed ?: return
        onAdClosed = null
        closed()
    }

    fun advance(millis: Long) {
        now += millis
    }
}
