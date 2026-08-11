package com.stanisryz.logica.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Where the interstitial cooldown lives across process recreation and application restarts.
 *
 * It holds one value — the moment Yandex last confirmed an interstitial actually appeared — and it
 * is deliberately DataStore rather than Room: it is a preference-shaped timestamp, not a record, and
 * advertising state must never require a database migration.
 */
internal interface InterstitialCooldownStore {
    /** `null` means no interstitial has ever been shown on this installation. */
    suspend fun lastShownAtEpochMillis(): Long?

    suspend fun recordShownAt(epochMillis: Long)
}

internal class DataStoreInterstitialCooldownStore(
    private val dataStore: DataStore<Preferences>,
) : InterstitialCooldownStore {
    override suspend fun lastShownAtEpochMillis(): Long? =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.first()[LAST_INTERSTITIAL_SHOWN_AT]

    override suspend fun recordShownAt(epochMillis: Long) {
        // An unwritable preference file must not propagate into the terminal result flow; the worst
        // case is one extra interstitial after a restart, never a broken completion.
        runCatching { dataStore.edit { it[LAST_INTERSTITIAL_SHOWN_AT] = epochMillis } }
    }

    private companion object {
        val LAST_INTERSTITIAL_SHOWN_AT = longPreferencesKey("last_interstitial_shown_at")
    }
}

/**
 * The only place the interstitial frequency rule lives: at least five minutes between two ads that
 * were actually shown.
 *
 * The interval is measured from the successful `onAdShown` callback and from nothing else — a game
 * finishing, a load starting, a load failing, or a show that never reached the screen all leave the
 * stored timestamp alone. Backwards system-clock movement is treated as no elapsed time at all, so
 * changing the device clock cannot buy an extra ad.
 */
internal class InterstitialCooldownPolicy(
    private val store: InterstitialCooldownStore,
    private val clock: InterstitialClock = InterstitialClock.SYSTEM,
) {
    suspend fun isEligible(): Boolean = remainingMillis() == 0L

    /** How long is left before another interstitial may be shown; `0` means it may be shown now. */
    suspend fun remainingMillis(): Long {
        val lastShownAt = store.lastShownAtEpochMillis() ?: return 0L
        val elapsed = clock.nowEpochMillis() - lastShownAt
        if (elapsed < 0L) return COOLDOWN_MILLIS
        return (COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L)
    }

    /** Called from the successful shown callback, and from no other event. */
    suspend fun recordShown() {
        store.recordShownAt(clock.nowEpochMillis())
    }

    companion object {
        /** Exactly five minutes between actual interstitial shows. */
        const val COOLDOWN_MILLIS = 5L * 60L * 1000L
    }
}
