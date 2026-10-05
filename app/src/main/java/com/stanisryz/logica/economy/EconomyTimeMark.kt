package com.stanisryz.logica.economy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.stanisryz.logica.runCatchingCancellable

/**
 * The latest economy time ([EconomyClock]) the app has seen. Android has no server time, so this is
 * what notices a device clock turned back: rewards pause while the clock is more than
 * [CLOCK_TURNED_BACK_TOLERANCE_MILLIS] behind it, until real time catches up again.
 */
internal interface EconomyTimeMark {
    /** Records [nowEpochMillis] when it is the latest seen and returns the latest seen so far. */
    suspend fun record(nowEpochMillis: Long): Long

    companion object {
        /** Ordinary clock corrections stay well inside this. */
        const val CLOCK_TURNED_BACK_TOLERANCE_MILLIS = 10L * 60L * 1000L
    }
}

/** True when [nowEpochMillis] lies more than the tolerance behind the latest time seen. */
internal suspend fun EconomyTimeMark.clockTurnedBack(nowEpochMillis: Long): Boolean =
    nowEpochMillis < record(nowEpochMillis) - EconomyTimeMark.CLOCK_TURNED_BACK_TOLERANCE_MILLIS

/** For one process only; tests and a build without the DataStore mark. */
internal class InMemoryEconomyTimeMark : EconomyTimeMark {
    private var latest = Long.MIN_VALUE

    override suspend fun record(nowEpochMillis: Long): Long {
        latest = maxOf(latest, nowEpochMillis)
        return latest
    }
}

/**
 * The mark in DataStore rather than Room, so it needs no table or migration. A storage failure never
 * blocks rewards: the reading then counts as the latest time.
 */
internal class DataStoreEconomyTimeMark(
    private val dataStore: DataStore<Preferences>,
) : EconomyTimeMark {
    override suspend fun record(nowEpochMillis: Long): Long =
        runCatchingCancellable {
            var latest = nowEpochMillis
            dataStore.edit { preferences ->
                latest = maxOf(preferences[LATEST_ECONOMY_TIME] ?: Long.MIN_VALUE, nowEpochMillis)
                preferences[LATEST_ECONOMY_TIME] = latest
            }
            latest
        }.getOrDefault(nowEpochMillis)

    private companion object {
        val LATEST_ECONOMY_TIME = longPreferencesKey("latest_economy_time_ms")
    }
}
