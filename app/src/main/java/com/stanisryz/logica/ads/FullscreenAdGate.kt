package com.stanisryz.logica.ads

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The whole mutual exclusion between the two fullscreen formats: rewarded and interstitial must
 * never call `show(Activity)` at the same time.
 *
 * It is deliberately one boolean rather than an ad manager. Neither controller asks the other
 * anything, neither one queues, and losing the gate simply means that show does not happen — which
 * both formats already treat as a normal, non-blocking outcome.
 */
internal class FullscreenAdGate {
    private val presenting = AtomicBoolean(false)

    /** `true` when this caller now owns the screen and must eventually [release] it. */
    fun tryAcquire(): Boolean = presenting.compareAndSet(false, true)

    fun release() {
        presenting.set(false)
    }

    companion object {
        /** The single gate both controllers use in the application. */
        val SHARED = FullscreenAdGate()
    }
}
