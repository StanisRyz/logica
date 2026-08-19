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
    val sudokuController = WebSudokuController.create(controller.puzzleDataLoader)
    val game2048Controller = Web2048Controller.create(controller.puzzleDataLoader)

    ComposeViewport {
        WebApp(
            controller,
            balanceController,
            crownsController,
            wordController,
            sudokuController,
            game2048Controller,
            lifecycle,
        )
    }
    controller.start()
}
