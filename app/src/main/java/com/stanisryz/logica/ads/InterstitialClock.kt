package com.stanisryz.logica.ads

/**
 * The one time source the interstitial cooldown is allowed to read, mirroring `EconomyClock`.
 * Controllers and UI never call [System.currentTimeMillis] themselves, so the five-minute interval
 * is exercised in tests without waiting for it.
 */
internal fun interface InterstitialClock {
    fun nowEpochMillis(): Long

    companion object {
        val SYSTEM = InterstitialClock { System.currentTimeMillis() }
    }
}
