package com.stanisryz.logica.ads

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interstitial frequency rule and the storage it depends on. What is checked is the boundary
 * itself, what may move it — only an ad Yandex confirmed was shown — and that nothing about a game
 * finishing, a load, or a failed show can.
 */
class InterstitialCooldownTest {
    @Test
    fun `the five minute boundary is measured from the last confirmed show`() =
        runBlocking {
            val clock = FakeInterstitialClock()
            val store = FakeInterstitialCooldownStore()
            val policy = InterstitialCooldownPolicy(store, clock)

            assertEquals(5L * 60L * 1000L, InterstitialCooldownPolicy.COOLDOWN_MILLIS)

            // Nothing has ever been shown, so there is nothing to wait for.
            assertNull(store.lastShownAtEpochMillis())
            assertTrue(policy.isEligible())
            assertEquals(0L, policy.remainingMillis())

            policy.recordShown()

            assertEquals(clock.now, store.lastShownAtEpochMillis())
            assertFalse(policy.isEligible())

            // One second short of the interval is still inside it.
            clock.advance(COOLDOWN - 1_000L)
            assertFalse(policy.isEligible())
            assertEquals(1_000L, policy.remainingMillis())

            // Exactly five minutes is eligible; the boundary is inclusive.
            clock.advance(1_000L)
            assertTrue(policy.isEligible())
            assertEquals(0L, policy.remainingMillis())
        }

    @Test
    fun `only a confirmed show writes the timestamp and it survives recreation`() =
        runBlocking {
            val clock = FakeInterstitialClock()
            val store = FakeInterstitialCooldownStore()
            val policy = InterstitialCooldownPolicy(store, clock)

            // Everything that is not the shown callback: asking whether an ad may be shown, a game
            // finishing, a load starting or failing, and a show that never reached the screen. None
            // of them goes through the policy's one writing method, so none of them can start a
            // cooldown the player never actually saw an ad for.
            repeat(3) { policy.isEligible() }
            assertEquals(0, store.writes)
            assertNull(store.lastShownAtEpochMillis())

            policy.recordShown()
            val shownAt = clock.now
            assertEquals(1, store.writes)

            // Process recreation: a brand-new policy over the same stored value, and the remaining
            // time is still counted from the original show rather than restarting at zero.
            clock.advance(COOLDOWN / 2)
            val afterRestart = InterstitialCooldownPolicy(store, clock)

            assertFalse(afterRestart.isEligible())
            assertEquals(COOLDOWN / 2, afterRestart.remainingMillis())
            assertEquals(shownAt, store.lastShownAtEpochMillis())
            assertEquals(1, store.writes)
        }

    @Test
    fun `moving the system clock backwards does not bypass the cooldown`() =
        runBlocking {
            val clock = FakeInterstitialClock()
            val store = FakeInterstitialCooldownStore()
            val policy = InterstitialCooldownPolicy(store, clock)

            policy.recordShown()
            clock.advance(-DAY)

            // Elapsed time is treated as zero rather than negative, so the interval is not expired.
            assertFalse(policy.isEligible())
            assertEquals(COOLDOWN, policy.remainingMillis())

            // And a clock that comes back forwards resumes counting from the stored moment.
            clock.advance(DAY + COOLDOWN)
            assertTrue(policy.isEligible())
        }

    private companion object {
        const val COOLDOWN = InterstitialCooldownPolicy.COOLDOWN_MILLIS
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
