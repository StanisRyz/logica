package com.stanisryz.logica

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.stanisryz.logica.daily.DailyChallengeRepository
import com.stanisryz.logica.daily.DailyResultRepository
import com.stanisryz.logica.daily.RoomDailyChallengeRepository
import com.stanisryz.logica.daily.RoomDailyResultRepository
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.RoomEconomyRepository
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.LogicaDatabase
import com.stanisryz.logica.session.RoomGameSessionRepository
import com.stanisryz.logica.settings.DataStoreSettingsRepository
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.statistics.RoomStatisticsRepository
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.store.RuStoreGemPayGateway
import com.stanisryz.logica.store.RuStorePayGateway
import ru.rustore.sdk.pay.model.SdkTheme

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

class LogicaApplication : Application() {
    internal val container: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}

internal class AppContainer(
    context: Context,
) {
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(
            dataStore = context.userSettingsDataStore,
        )

    private val database: LogicaDatabase by lazy {
        LogicaDatabase.create(context)
    }

    private val gamePersistenceRepository: RoomGameSessionRepository by lazy {
        RoomGameSessionRepository(database.gameSessionDao(), database.gameCompletionDao())
    }

    val gameSessionRepository: GameSessionRepository by lazy {
        gamePersistenceRepository
    }

    val gameCompletionRepository: GameCompletionRepository by lazy {
        gamePersistenceRepository
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

    /**
     * Constructing this touches nothing: the Pay SDK is brought up by its own ContentProvider from
     * the manifest, and every call through the gateway is allowed to fail without the application
     * noticing. The payment sheet follows the device's night mode, which is the only theme signal
     * available this early.
     */
    val ruStorePayGateway: RuStorePayGateway by lazy {
        RuStoreGemPayGateway(
            sdkTheme = {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) SdkTheme.DARK else SdkTheme.LIGHT
            },
        )
    }
}
