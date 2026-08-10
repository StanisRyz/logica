package com.stanisryz.logica.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regeneration is pure arithmetic over an anchor and a fake clock, never a repeating worker: the
 * whole 15-minute model has to hold across app restarts and long offline gaps.
 */
class LifeRegenerationTest {
    @Test
    fun oneCountdownRunsThroughSeveralLostLivesAndRestoresThemOneIntervalAtATime() {
        val full = PlayerEconomy()

        // The first loss starts the countdown.
        val fourLives = full.withLifeSpent(START)
        assertEquals(4, fourLives.lives)
        assertEquals(START + INTERVAL, fourLives.nextLifeAtEpochMillis)

        // Losing another life five minutes later must not restart the running countdown.
        val threeLives = fourLives.regenerated(START + 5 * MINUTE).withLifeSpent(START + 5 * MINUTE)
        assertEquals(3, threeLives.lives)
        assertEquals(START + INTERVAL, threeLives.nextLifeAtEpochMillis)

        // One second short of the interval nothing has come back yet.
        assertEquals(threeLives, threeLives.regenerated(START + INTERVAL - 1_000))

        val afterOneInterval = threeLives.regenerated(START + INTERVAL)
        assertEquals(4, afterOneInterval.lives)
        assertEquals(START + 2 * INTERVAL, afterOneInterval.nextLifeAtEpochMillis)
    }

    @Test
    fun aLongOfflineGapRestoresSeveralLivesAndKeepsThePartialInterval() {
        val twoLives = PlayerEconomy(gems = 3, lives = 2, nextLifeAtEpochMillis = START + INTERVAL)

        // 31 minutes after the countdown started: two whole intervals elapsed, one minute remains.
        val refreshed = twoLives.regenerated(START + 31 * MINUTE)
        assertEquals(4, refreshed.lives)
        assertEquals(START + 3 * INTERVAL, refreshed.nextLifeAtEpochMillis)
        assertEquals(14 * MINUTE, refreshed.millisUntilNextLife(START + 31 * MINUTE))

        // A gap far longer than the missing lives stops at the cap and clears the anchor.
        val capped = twoLives.regenerated(START + 10 * 60 * MINUTE)
        assertEquals(EconomyRules.MAX_LIVES, capped.lives)
        assertNull(capped.nextLifeAtEpochMillis)
        assertEquals(3, capped.gems)
    }

    @Test
    fun aBackwardsClockRestoresNothingAndNeverCorruptsTheWallet() {
        val fourLives = PlayerEconomy(lives = 4, nextLifeAtEpochMillis = START + INTERVAL)

        // Seen at START + 10 minutes and then moved back to START + 1: elapsed time counts as zero.
        assertEquals(fourLives, fourLives.regenerated(START + MINUTE))
        assertEquals(14 * MINUTE, fourLives.millisUntilNextLife(START + MINUTE))

        // A clock far in the past would leave an impossible wait, so it is repaired to one interval.
        val movedBackADay = START - 24 * 60 * MINUTE
        val repaired = fourLives.regenerated(movedBackADay)
        assertEquals(4, repaired.lives)
        assertEquals(movedBackADay + INTERVAL, repaired.nextLifeAtEpochMillis)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val MINUTE = 60_000L
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}
