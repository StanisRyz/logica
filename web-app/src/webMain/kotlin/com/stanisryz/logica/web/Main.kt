package com.stanisryz.logica.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val lifecycle = WebHostLifecycle()
    val controller =
        WebBootstrapController(
            bridge = YandexGamesBridge(),
            puzzleDataLoader = BrowserPuzzleDataLoader(),
            lifecycle = lifecycle,
        )
    val balanceController = WebBalanceController.create(controller.puzzleDataLoader)
    val crownsController = WebCrownsController.create(controller.puzzleDataLoader)
    val wordController = WebWordController.create(controller.puzzleDataLoader)

    ComposeViewport {
        WebApp(controller, balanceController, crownsController, wordController, lifecycle)
    }
    controller.start()
}
