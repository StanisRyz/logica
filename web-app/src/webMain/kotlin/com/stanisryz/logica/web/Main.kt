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
    val playerSession =
        if (bridge.isAvailable) {
            WebPlayerSessionController(
                playerIdentityGateway = YandexPlayerIdentityGateway(bridge),
                cloudSaveGateway = YandexCloudSaveGateway(bridge),
                progressRepositoryFactory = progressRepositoryFactory,
                playerContextEvents = bridge,
            )
        } else {
            WebPlayerSessionController(
                playerIdentityGateway = UnsupportedWebPlayerIdentityGateway,
                cloudSaveGateway = UnsupportedWebCloudSaveGateway,
                progressRepositoryFactory = progressRepositoryFactory,
                playerContextEvents = bridge,
            )
        }
    val progressCoordinator = WebCatalogProgressCoordinator(playerSession)
    val balanceController = WebBalanceController.create(controller.puzzleDataLoader, progressCoordinator)
    val crownsController = WebCrownsController.create(controller.puzzleDataLoader, progressCoordinator)
    val wordController = WebWordController.create(controller.puzzleDataLoader, progressCoordinator)
    val sudokuController = WebSudokuController.create(controller.puzzleDataLoader, progressCoordinator)
    val game2048Controller = Web2048Controller.create(controller.puzzleDataLoader, progressCoordinator)

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
