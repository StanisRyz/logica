package com.stanisryz.logica.ads

import android.content.Context
import com.yandex.mobile.ads.common.YandexAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Brings up the Yandex Mobile Ads SDK on demand, with the advertising privacy state declared first.
 *
 * The SDK is initialized manually rather than left to its own bootstrap so the ordering is
 * guaranteed: consent, location tracking, and the age declaration are set before `initialize`, and
 * no ad request can happen before that returns.
 *
 * There is one initializer for both placements. Nothing here runs at application startup: the first
 * caller is whichever preload happens first — the rewarded one, which only happens at zero lives, or
 * the interstitial one, which only happens from an active gameplay destination — and every later
 * caller of either format reuses the initialization this one completed.
 *
 * Every failure is contained: a refused or timed-out initialization leaves the application, the
 * wallet, regeneration, the gem refill, and every terminal result untouched, and simply reports that
 * ads are unavailable.
 */
internal object YandexAdsInitializer {
    /**
     * The application shows no consent dialog and collects nothing for advertising, so the SDK is
     * told the honest thing: no consent for personalized advertising was obtained, no location is
     * tracked, and the audience is not age-restricted. A real consent flow, if one is ever needed,
     * belongs to the Stage 33 release/privacy audit and would replace only these three values.
     */
    private const val HAS_PERSONALIZED_ADS_CONSENT = false
    private const val TRACKS_LOCATION = false
    private const val IS_AGE_RESTRICTED_AUDIENCE = false

    /** A hung initialization must not hold the rewarded UI, or any preload, waiting forever. */
    private const val INITIALIZATION_TIMEOUT_MILLIS = 10_000L

    private val mutex = Mutex()
    private var initialized = false

    /** `true` once the SDK is ready to take ad requests; `false` means ads stay unavailable. */
    suspend fun ensureInitialized(context: Context): Boolean =
        mutex.withLock {
            if (initialized) return@withLock true
            initialized = runCatching { initializeWithPrivacyState(context.applicationContext) }.getOrDefault(false)
            initialized
        }

    private suspend fun initializeWithPrivacyState(context: Context): Boolean =
        withContext(Dispatchers.Main) {
            YandexAds.setUserConsent(HAS_PERSONALIZED_ADS_CONSENT)
            YandexAds.setLocationTracking(TRACKS_LOCATION)
            YandexAds.setAgeRestricted(IS_AGE_RESTRICTED_AUDIENCE)
            withTimeoutOrNull(INITIALIZATION_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    YandexAds.initialize(context) { if (continuation.isActive) continuation.resume(Unit) }
                }
            } != null
        }
}
