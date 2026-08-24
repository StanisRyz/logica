@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val lifecycle = WebHostLifecycle()
    val bridge = YandexGamesBridge()
    val controller =
        WebBootstrapController(
            bridge = bridge,
            puzzleDataLoader = BrowserPuzzleDataLoader(),
            lifecycle = lifecycle,
        )
    val progressRepositoryFactory =
        WebCatalogProgressRepositoryFactory { scope ->
            WebCatalogProgressRepository(
                scope = scope,
                localStore = WebCatalogProgressLocalStore(scope),
            )
        }
    val installationIdProvider = WebInstallationIdProvider()
    val statisticsRepositoryFactory =
        WebStatisticsRepositoryFactory { scope ->
            WebStatisticsRepository(
                scope = scope,
                installationId = installationIdProvider.getOrCreate(),
                localStore = WebStatisticsLocalStore(scope),
            )
        }
    val dailyRepositoryFactory =
        WebDailyRepositoryFactory { scope ->
            WebDailyRepository(
                scope = scope,
                localStore = WebDailyLocalStore(scope),
                dateProvider = BrowserLocalWebDailyDateProvider,
            )
        }
    val playerSession =
        if (bridge.isAvailable) {
            WebPlayerSessionController(
                playerIdentityGateway = YandexPlayerIdentityGateway(bridge),
                cloudSaveGateway = YandexCloudSaveGateway(bridge),
                progressRepositoryFactory = progressRepositoryFactory,
                statisticsCloudSaveGateway =
                    YandexCloudSaveGateway(
                        bridge,
                        dataKey = YandexCloudSaveGateway.STATISTICS_STATE_KEY,
                    ),
                statisticsRepositoryFactory = statisticsRepositoryFactory,
                dailyCloudSaveGateway =
                    YandexCloudSaveGateway(
                        bridge,
                        dataKey = YandexCloudSaveGateway.DAILY_STATE_KEY,
                    ),
                dailyRepositoryFactory = dailyRepositoryFactory,
                playerContextEvents = bridge,
            )
        } else {
            WebPlayerSessionController(
                playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                progressRepositoryFactory = progressRepositoryFactory,
                statisticsCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                statisticsRepositoryFactory = statisticsRepositoryFactory,
                dailyCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                dailyRepositoryFactory = dailyRepositoryFactory,
                playerContextEvents = bridge,
            )
        }
    val progressCoordinator = WebCatalogProgressCoordinator(playerSession)
    val statisticsCoordinator = WebGameplayStatisticsCoordinator(playerSession)
    val dailyCoordinator = WebDailyGameplayCoordinator(playerSession)
    val economyCoordinator = WebGameplayEconomyCoordinator(playerSession)
    val storeCoordinator = WebGameplayStoreCoordinator(playerSession)
    val storeProcessor =
        WebStoreProcessor(
            economyRepository = { playerSession.economyRepository },
            storeRepository = { playerSession.storeRepository },
            currentTimeMs = ::currentTimeMillis,
        )
    // Unified cloud save foundation: identity through the SDK, payload over a dedicated key.
    val playerProvider =
        YandexPlayerProvider(
            if (bridge.isAvailable) {
                YandexPlayerIdentityGateway(bridge)
            } else {
                UnsupportedWebPlayerIdentityGateway
            },
        )
    val unifiedSaveRepository =
        YandexCloudSaveRepository(
            YandexCloudSaveGateway(bridge, dataKey = UNIFIED_SAVE_STATE_KEY),
        )
    val saveManager =
        WebSaveManager(WebSaveSections(playerSession).all(), unifiedSaveRepository)
    playerSession.postBindAction = { saveManager.restore() }
    playerProvider.let { /* identity is consumed automatically by the session controller */ }
    val balanceController =
        WebBalanceController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val crownsController =
        WebCrownsController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val wordController =
        WebWordController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )
    val sudokuController =
        WebSudokuController.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
            storeCoordinator,
        )
    val game2048Controller =
        Web2048Controller.create(
            controller.puzzleDataLoader,
            progressCoordinator,
            statisticsCoordinator,
            dailyCoordinator,
            economyCoordinator,
        )

    ComposeViewport {
        WebApp(
            controller,
            balanceController,
            crownsController,
            wordController,
            sudokuController,
            game2048Controller,
            lifecycle,
            playerSession,
            dailyCoordinator,
            storeProcessor,
        )
    }
    controller.start()
}

private fun currentTimeMillis(): Long = js("Date.now()")

private const val UNIFIED_SAVE_STATE_KEY = "logica_unified_save_v1"
