package com.stanisryz.logica.ads

/** A movable stand-in for wall-clock time, so five minutes cost a test nothing. */
internal class FakeInterstitialClock(
    var now: Long = 1_700_000_000_000L,
) : InterstitialClock {
    override fun nowEpochMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }
}

/**
 * Durable-enough storage: the value outlives any number of policy instances built on top of it,
 * which is exactly what process recreation looks like to the cooldown.
 */
internal class FakeInterstitialCooldownStore(
    private var lastShownAt: Long? = null,
) : InterstitialCooldownStore {
    var writes = 0
        private set

    override suspend fun lastShownAtEpochMillis(): Long? = lastShownAt

    override suspend fun recordShownAt(epochMillis: Long) {
        lastShownAt = epochMillis
        writes++
    }
}
