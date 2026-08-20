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
                playerContextEvents = bridge,
            )
        } else {
            WebPlayerSessionController(
                playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                progressRepositoryFactory = progressRepositoryFactory,
                statisticsCloudSaveGateway = UnsupportedWebCloudSaveGateway,
                statisticsRepositoryFactory = statisticsRepositoryFactory,
                playerContextEvents = bridge,
            )
        }
    val progressCoordinator = WebCatalogProgressCoordinator(playerSession)
    val statisticsCoordinator = WebGameplayStatisticsCoordinator(playerSession)
    val balanceController =
        WebBalanceController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator)
    val crownsController =
        WebCrownsController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator)
    val wordController =
        WebWordController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator)
    val sudokuController =
        WebSudokuController.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator)
    val game2048Controller =
        Web2048Controller.create(controller.puzzleDataLoader, progressCoordinator, statisticsCoordinator)

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
        )
    }
    controller.start()
}
