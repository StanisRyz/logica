@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.js.ExperimentalWasmJsInterop

internal enum class WebHostMode {
    YANDEX,
    STANDALONE,
}

internal sealed interface WebBootstrapState {
    data object Loading : WebBootstrapState

    data class Ready(
        val mode: WebHostMode,
    ) : WebBootstrapState

    data class FatalError(
        val message: String,
    ) : WebBootstrapState
}

internal class WebBootstrapController(
    private val bridge: YandexGamesBridge,
    val puzzleDataLoader: BrowserPuzzleDataLoader,
    private val lifecycle: WebHostLifecycle,
) {
    private var applicationStarted = false
    private var composeRootRendered = false
    private var initialHostUiReady = false
    private var notificationAttempted = false

    var state by mutableStateOf<WebBootstrapState>(WebBootstrapState.Loading)
        private set

    /**
     * The resolved host presentation language, read from the Yandex SDK I18N environment during
     * normal Yandex startup. Standalone development keeps the default without any SDK.
     */
    var hostLanguage: WebAppLanguage = WebAppLanguage.RUSSIAN
        private set

    fun start() {
        if (applicationStarted) return
        applicationStarted = true
        lifecycle.start()

        if (!bridge.isAvailable) {
            state =
                if (isStandaloneDevelopmentEnvironment()) {
                    WebBootstrapState.Ready(WebHostMode.STANDALONE)
                } else {
                    WebBootstrapState.FatalError(
                        "Yandex Games SDK is unavailable outside a local development host.",
                    )
                }
            return
        }

        bridge.initialize(
            lifecycleListener = lifecycle,
            onReady = {
                // Read the real platform language once the SDK is initialized; unsupported or
                // unexpected values resolve safely to the application default.
                hostLanguage = resolveWebAppLanguage(bridge.platformLanguage())
                state = WebBootstrapState.Ready(WebHostMode.YANDEX)
                notifyGameReadyIfPossible()
            },
            onFailure = { detail ->
                state = WebBootstrapState.FatalError("Yandex Games SDK initialization failed: $detail")
            },
        )
    }

    fun onComposeRootRendered() {
        composeRootRendered = true
        notifyGameReadyIfPossible()
    }

    fun onInitialHostUiReady() {
        initialHostUiReady = true
        notifyGameReadyIfPossible()
    }

    /** Gameplay lifecycle failures never block the puzzle or host UI. */
    fun setGameplayActive(active: Boolean) {
        bridge.setGameplayActive(active)
    }

    fun dispose() {
        bridge.dispose()
        lifecycle.dispose()
    }

    private fun notifyGameReadyIfPossible() {
        val readyState = state as? WebBootstrapState.Ready ?: return
        if (
            readyState.mode != WebHostMode.YANDEX ||
            !applicationStarted ||
            !bridge.isReady ||
            !composeRootRendered ||
            !initialHostUiReady ||
            notificationAttempted
        ) {
            return
        }

        notificationAttempted = true
        bridge.notifyLoadingReady()?.let { detail ->
            state = WebBootstrapState.FatalError(detail)
        }
    }
}

private fun isStandaloneDevelopmentEnvironment(): Boolean =
    browserProtocol() == "file:" ||
        browserHostname() == "localhost" ||
        browserHostname() == "127.0.0.1" ||
        browserHostname() == "[::1]"

private fun browserHostname(): String = js("globalThis.location.hostname")

private fun browserProtocol(): String = js("globalThis.location.protocol")
