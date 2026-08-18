package com.stanisryz.logica

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.stanisryz.logica.ads.DataStoreInterstitialCooldownStore
import com.stanisryz.logica.ads.InterstitialAwareGameCompletionRepository
import com.stanisryz.logica.ads.InterstitialCooldownPolicy
import com.stanisryz.logica.ads.InterstitialOpportunities
import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.createCatalogLevelRepository
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.RoomDailyChallengeRepository
import com.stanisryz.logica.daily.RoomDailyResultRepository
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.RoomEconomyRepository
import com.stanisryz.logica.platform.android.AndroidPlatformComposition
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.RoomGameCompletionRepository
import com.stanisryz.logica.session.LogicaDatabase
import com.stanisryz.logica.settings.DataStoreSettingsRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.statistics.RoomStatisticsRepository
import com.stanisryz.logica.statistics.StatisticsRepository

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

/**
 * Advertising state is kept apart from user settings and out of Room: the interstitial cooldown is
 * one preference-shaped timestamp, and it must survive a restart without a database migration.
 */
private val Context.advertisingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "advertising",
)

class LogicaApplication : Application() {
    internal val container: AppContainer by lazy {
        AppContainer(this)
    }
}

internal class AppContainer(
    context: Application,
) {
    val platform = AndroidPlatformComposition(context)

    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(
            dataStore = context.userSettingsDataStore,
        )

    private val database: LogicaDatabase by lazy {
        LogicaDatabase.create(context)
    }

    /**
     * Catalog progression plus the read-only frozen Level Pack V1 assets. Nothing here is touched at
     * cold start: a bucket is streamed only when a level is actually opened.
     */
    val catalogLevelRepository: CatalogLevelRepository by lazy {
        createCatalogLevelRepository(database.catalogLevelProgressDao(), context.assets)
    }

    private val completionPersistenceRepository: RoomGameCompletionRepository by lazy {
        RoomGameCompletionRepository(database.gameCompletionDao())
    }

    /**
     * Ephemeral by design: an opportunity exists only between a completion succeeding and the live
     * UI acting on it, so a process death loses it and no missed advertisement is ever replayed.
     */
    val interstitialOpportunities: InterstitialOpportunities = InterstitialOpportunities()

    val interstitialCooldownPolicy: InterstitialCooldownPolicy by lazy {
        InterstitialCooldownPolicy(DataStoreInterstitialCooldownStore(context.advertisingDataStore))
    }

    /**
     * Completion is wrapped rather than reimplemented, which is what makes the ordering structural:
     * the result and its economy transaction are durable before an interstitial can even be offered,
     * and a failed completion never reaches the advertising layer at all.
     */
    val gameCompletionRepository: GameCompletionRepository by lazy {
        InterstitialAwareGameCompletionRepository(completionPersistenceRepository, interstitialOpportunities)
    }

    val dailyChallengeRepository: DailyChallengeRepository by lazy {
        RoomDailyChallengeRepository(database.dailyChallengeDao(), database.dailyRunDao())
    }

    val statisticsRepository: StatisticsRepository by lazy {
        RoomStatisticsRepository(database.gameResultDao(), database.dailyRunDao())
    }

    val dailyResultRepository: DailyResultRepository by lazy {
        RoomDailyResultRepository(database.gameResultDao())
    }

    val economyRepository: EconomyRepository by lazy {
        RoomEconomyRepository(database.economyDao())
    }

}
