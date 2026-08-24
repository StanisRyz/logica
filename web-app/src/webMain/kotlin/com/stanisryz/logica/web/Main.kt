package com.stanisryz.logica.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

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
    val balanceController =
        WebBalanceController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator, dailyCoordinator)
    val crownsController =
        WebCrownsController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator, dailyCoordinator)
    val wordController =
        WebWordController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator, dailyCoordinator)
    val sudokuController =
        WebSudokuController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator, dailyCoordinator)
    val game2048Controller =
        Web2048Controller.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator, dailyCoordinator)

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
        )
    }
    controller.start()
}
