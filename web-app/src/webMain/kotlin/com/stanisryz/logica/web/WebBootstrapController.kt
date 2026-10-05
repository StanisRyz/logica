@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /** `YaGames.init()` did not answer in time; the player may retry (a page reload). */
    data object TimedOut : WebBootstrapState
}

/** What the bootstrap needs from the Yandex SDK boundary ([YandexGamesBridge]). */
internal interface WebSdkBootstrapBridge {
    val isAvailable: Boolean
    val isReady: Boolean

    fun initialize(
        lifecycleListener: YandexLifecycleListener,
        onReady: () -> Unit,
        onFailure: (String) -> Unit,
    )

    fun platformLanguage(): String?

    fun serverTimeMs(): Long?

    fun notifyLoadingReady(): String?

    fun setGameplayActive(active: Boolean): String?

    fun dispose()
}

/** The page lifecycle as the bootstrap starts and stops it ([WebHostLifecycle]). */
internal interface WebBootstrapLifecycle : YandexLifecycleListener {
    fun start()

    fun dispose()
}

internal class WebBootstrapController(
    private val bridge: WebSdkBootstrapBridge,
    val puzzleDataLoader: BrowserPuzzleDataLoader,
    private val lifecycle: WebBootstrapLifecycle,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: WebClock = webClock,
    private val reloadPage: () -> Unit = ::browserReloadPage,
    private val initTimeoutMs: Long = INIT_TIMEOUT_MS,
    private val standaloneDevelopment: () -> Boolean = ::isStandaloneDevelopmentEnvironment,
) {
    private var initTimeout: Job? = null

    private var applicationStarted = false
    private var composeRootRendered = false
    private var initialHostUiReady = false
    private var notificationAttempted = false

    var state by mutableStateOf<WebBootstrapState>(WebBootstrapState.Loading)
        private set

    /**
     * The resolved host presentation language, read from the Yandex SDK I18N environment during
     * normal Yandex startup. Standalone development follows `?lang=` or the browser, without any SDK.
     */
    var hostLanguage: WebAppLanguage = WebAppLanguage.RUSSIAN
        private set(value) {
            field = value
            applyWebAppLanguage(value)
        }

    fun start() {
        if (applicationStarted) return
        applicationStarted = true
        lifecycle.start()

        if (!bridge.isAvailable) {
            state =
                if (standaloneDevelopment()) {
                    hostLanguage = standaloneWebAppLanguage()
                    WebBootstrapState.Ready(WebHostMode.STANDALONE)
                } else {
                    WebBootstrapState.FatalError(
                        "Yandex Games SDK is unavailable outside a local development host.",
                    )
                }
            return
        }

        // YaGames.init() that never answers must not leave the page on the loading screen forever.
        initTimeout =
            scope.launch {
                delay(initTimeoutMs)
                if (state == WebBootstrapState.Loading) state = WebBootstrapState.TimedOut
            }
        bridge.initialize(
            lifecycleListener = lifecycle,
            onReady = {
                initTimeout?.cancel()
                // Economy, rewards, and the Daily date read the server time from now on.
                clock.attachServerTime(bridge::serverTimeMs)
                // Read the real platform language once the SDK is initialized; unsupported or
                // unexpected values resolve safely to the application default.
                hostLanguage = resolveWebAppLanguage(bridge.platformLanguage())
                state = WebBootstrapState.Ready(WebHostMode.YANDEX)
                notifyGameReadyIfPossible()
            },
            onFailure = { detail ->
                initTimeout?.cancel()
                state = WebBootstrapState.FatalError("Yandex Games SDK initialization failed: $detail")
            },
        )
    }

    /**
     * «Retry» after [WebBootstrapState.TimedOut]. The bridge starts `YaGames.init()` once per page
     * and the SDK offers no way to restart it, so the retry reloads the page; an init that answers
     * late still turns the page Ready by itself.
     */
    fun retryInitialization() {
        if (state == WebBootstrapState.TimedOut) reloadPage()
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
        // A failed LoadingAPI.ready() is not fatal: the game keeps running.
        bridge.notifyLoadingReady()
    }

    private companion object {
        /** How long `YaGames.init()` may take before the page offers a retry. */
        const val INIT_TIMEOUT_MS = 15_000L
    }
}

private fun isStandaloneDevelopmentEnvironment(): Boolean =
    browserProtocol() == "file:" ||
        browserHostname() == "localhost" ||
        browserHostname() == "127.0.0.1" ||
        browserHostname() == "[::1]"

private fun browserHostname(): String = js("globalThis.location.hostname")

private fun browserReloadPage(): Unit = js("globalThis.location.reload()")

private fun browserProtocol(): String = js("globalThis.location.protocol")
