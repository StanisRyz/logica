package com.stanisryz.logica.game2048

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The best score of any single 2048 game — Catalog levels (freeplay after the target included) and
 * the Daily alike — which is 2048's rating. It is one preference-shaped number in its own DataStore,
 * so it needs no Room migration, and it only ever grows.
 */
internal class Game2048BestScore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val best: Flow<Long> =
        dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .map { it[BEST_SCORE] ?: 0L }

    private val offered = MutableStateFlow(0L)

    init {
        // Offers are conflated, so a record run writes its latest score rather than every move.
        scope.launch {
            offered.collect { score ->
                if (score <= 0L) return@collect
                runCatching { dataStore.edit { if (score > (it[BEST_SCORE] ?: 0L)) it[BEST_SCORE] = score } }
            }
        }
    }

    /** Records [score] when it beats the best; runs outside any screen, so leaving never loses it. */
    fun offer(score: Long) {
        offered.update { maxOf(it, score) }
    }

    private companion object {
        val BEST_SCORE = longPreferencesKey("best_2048_score")
    }
}
