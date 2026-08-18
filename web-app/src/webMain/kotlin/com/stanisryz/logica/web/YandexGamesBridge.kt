@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/** The only raw JavaScript boundary for the Yandex Games SDK. */
internal class YandexGamesBridge {
    private var sdk: YandexSdk? = null
    private var initializationStarted = false
    private var disposed = false
    private var loadingReadySent = false
    private var pauseCallback: (() -> Unit)? = null
    private var resumeCallback: (() -> Unit)? = null

    val isAvailable: Boolean
        get() = yandexGamesOrNull() != null

    val isReady: Boolean
        get() = sdk != null

    fun initialize(
        lifecycleListener: YandexLifecycleListener,
        onReady: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (disposed) {
            onFailure("Yandex Games bridge is already disposed.")
            return
        }
        sdk?.let {
            onReady()
            return
        }
        if (initializationStarted) return
        initializationStarted = true

        val yandexGames = yandexGamesOrNull()
        if (yandexGames == null) {
            onFailure("Yandex Games SDK is unavailable.")
            return
        }

        val initialization =
            try {
                yandexGames.init()
            } catch (error: Throwable) {
                onFailure(error.message ?: "Yandex Games SDK initialization failed.")
                return
            }

        initialization.then(
            onFulfilled = { initializedSdk ->
                if (!disposed) {
                    try {
                        sdk = initializedSdk
                        subscribeLifecycle(initializedSdk, lifecycleListener)
                        onReady()
                    } catch (error: Throwable) {
                        sdk = null
                        onFailure(error.message ?: "Unable to subscribe to Yandex lifecycle events.")
                    }
                }
                null
            },
            onRejected = { reason ->
                if (!disposed) onFailure(describeJsFailure(reason))
                null
            },
        )
    }

    /** Returns an error for the caller to surface; repeated calls never reach the real SDK twice. */
    fun notifyLoadingReady(): String? {
        if (loadingReadySent) return null
        val initializedSdk = sdk ?: return "Yandex Games SDK is not ready."
        loadingReadySent = true
        return try {
            initializedSdk.features.loadingApi?.ready()
            null
        } catch (error: Throwable) {
            error.message ?: "Unable to send Yandex Game Ready."
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val initializedSdk = sdk
        val pause = pauseCallback
        val resume = resumeCallback
        if (initializedSdk != null && pause != null) {
            runCatching { initializedSdk.off(GAME_API_PAUSE, pause) }
        }
        if (initializedSdk != null && resume != null) {
            runCatching { initializedSdk.off(GAME_API_RESUME, resume) }
        }
        pauseCallback = null
        resumeCallback = null
        sdk = null
    }

    private fun subscribeLifecycle(
        initializedSdk: YandexSdk,
        listener: YandexLifecycleListener,
    ) {
        val pause = { listener.onPause() }
        val resume = { listener.onResume() }
        initializedSdk.on(GAME_API_PAUSE, pause)
        try {
            initializedSdk.on(GAME_API_RESUME, resume)
        } catch (error: Throwable) {
            runCatching { initializedSdk.off(GAME_API_PAUSE, pause) }
            throw error
        }
        pauseCallback = pause
        resumeCallback = resume
    }

    private companion object {
        const val GAME_API_PAUSE = "game_api_pause"
        const val GAME_API_RESUME = "game_api_resume"
    }
}

internal interface YandexLifecycleListener {
    fun onPause()

    fun onResume()
}

private external interface YandexGamesGlobal : JsAny {
    fun init(): Promise<YandexSdk>
}

private external interface YandexSdk : JsAny {
    val features: YandexFeatures

    fun on(
        eventName: String,
        callback: () -> Unit,
    )

    fun off(
        eventName: String,
        callback: () -> Unit,
    )
}

private external interface YandexFeatures : JsAny {
    @JsName("LoadingAPI")
    val loadingApi: YandexLoadingApi?
}

private external interface YandexLoadingApi : JsAny {
    fun ready()
}

private fun yandexGamesOrNull(): YandexGamesGlobal? = js("typeof globalThis.YaGames === 'undefined' ? null : globalThis.YaGames")

private fun describeJsFailure(reason: JsAny): String = js("reason && reason.message ? String(reason.message) : String(reason)")
