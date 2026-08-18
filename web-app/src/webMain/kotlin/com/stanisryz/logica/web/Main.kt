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

    ComposeViewport {
        WebApp(controller, lifecycle)
    }
    controller.start()
}
