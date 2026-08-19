@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.asJsException

/** A Kotlin-only view of the cached Yandex Player. Raw SDK values never leave the bridge. */
internal data class YandexPlayerSnapshot(
    val isAuthorized: Boolean,
    val uniqueId: String?,
    val displayName: String?,
    val avatarReference: String?,
)

/** The only raw JavaScript boundary for the Yandex Games SDK. */
internal class YandexGamesBridge {
    private var sdk: YandexSdk? = null
    private var cachedPlayer: YandexPlayer? = null
    private var playerRequest: Promise<YandexPlayer>? = null
    private var initializationStarted = false
    private var disposed = false
    private var loadingReadySent = false
    private var gameplayActive = false
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
                        prefetchPlayer(initializedSdk)
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

    /** Resolves the Player once and then reuses it until an explicit authorization refresh. */
    suspend fun playerSnapshot(): YandexPlayerSnapshot = player(refresh = false).snapshot()

    /** Opens the SDK auth dialog and reacquires the Player before exposing its new identity. */
    suspend fun requestPlayerAuthorization(): YandexPlayerSnapshot {
        val initializedSdk = requireSdk()
        initializedSdk.auth.openAuthDialog().await()
        return player(refresh = true).snapshot()
    }

    suspend fun readPlayerData(key: String): String? {
        val data = player(refresh = false).getData(singleStringArray(key)).await()
        return stringPropertyOrNull(data, key)
    }

    suspend fun writePlayerData(
        key: String,
        value: String,
        flush: Boolean,
    ) {
        player(refresh = false).setData(singlePropertyObject(key, value), flush).await()
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

    /** Idempotent Yandex gameplay activity; hosts decide when a real in-progress route is active. */
    fun setGameplayActive(active: Boolean): String? {
        if (gameplayActive == active) return null
        val gameplayApi = sdk?.features?.gameplayApi
        return try {
            if (active) gameplayApi?.start() else gameplayApi?.stop()
            gameplayActive = active
            null
        } catch (error: Throwable) {
            error.message ?: "Unable to update Yandex gameplay activity."
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val initializedSdk = sdk
        if (gameplayActive) {
            runCatching { initializedSdk?.features?.gameplayApi?.stop() }
            gameplayActive = false
        }
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
        cachedPlayer = null
        playerRequest = null
        sdk = null
    }

    private fun prefetchPlayer(initializedSdk: YandexSdk) {
        if (cachedPlayer != null || playerRequest != null) return
        val request =
            try {
                initializedSdk.getPlayer()
            } catch (_: Throwable) {
                return
            }
        playerRequest = request
        request.then(
            onFulfilled = { resolved ->
                if (!disposed && playerRequest === request) {
                    cachedPlayer = resolved
                    playerRequest = null
                }
                null
            },
            onRejected = {
                if (playerRequest === request) playerRequest = null
                null
            },
        )
    }

    private suspend fun player(refresh: Boolean): YandexPlayer {
        if (!refresh) cachedPlayer?.let { return it }
        val initializedSdk = requireSdk()
        val request =
            if (!refresh) {
                playerRequest ?: initializedSdk.getPlayer().also { playerRequest = it }
            } else {
                cachedPlayer = null
                initializedSdk.getPlayer().also { playerRequest = it }
            }

        return try {
            request.await().also { resolved ->
                if (!disposed && playerRequest === request) {
                    cachedPlayer = resolved
                    playerRequest = null
                }
            }
        } catch (error: Throwable) {
            if (playerRequest === request) playerRequest = null
            throw error
        }
    }

    private fun requireSdk(): YandexSdk =
        checkNotNull(sdk) { "Yandex Games SDK is not ready." }

    private fun YandexPlayer.snapshot(): YandexPlayerSnapshot =
        YandexPlayerSnapshot(
            isAuthorized = isAuthorized(),
            uniqueId = optionalText { getUniqueID() },
            displayName = optionalText { getName() },
            avatarReference = optionalText { getPhoto(PLAYER_PHOTO_SIZE) },
        )

    private fun optionalText(block: () -> String?): String? =
        runCatching(block).getOrNull()?.trim()?.takeIf(String::isNotEmpty)

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
        const val PLAYER_PHOTO_SIZE = "small"
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
    val auth: YandexAuth

    fun getPlayer(): Promise<YandexPlayer>

    fun on(
        eventName: String,
        callback: () -> Unit,
    )

    fun off(
        eventName: String,
        callback: () -> Unit,
    )
}

private external interface YandexAuth : JsAny {
    fun openAuthDialog(): Promise<JsAny?>
}

private external interface YandexPlayer : JsAny {
    fun isAuthorized(): Boolean

    fun getUniqueID(): String?

    fun getName(): String?

    fun getPhoto(size: String): String?

    fun getData(keys: JsArray<JsString>): Promise<JsAny>

    fun setData(
        data: JsAny,
        flush: Boolean,
    ): Promise<JsAny?>
}

private external interface YandexFeatures : JsAny {
    @JsName("LoadingAPI")
    val loadingApi: YandexLoadingApi?

    @JsName("GameplayAPI")
    val gameplayApi: YandexGameplayApi?
}

private external interface YandexLoadingApi : JsAny {
    fun ready()
}

private external interface YandexGameplayApi : JsAny {
    fun start()

    fun stop()
}

private suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCoroutine { continuation ->
        then(
            onFulfilled = { value ->
                continuation.resume(value)
                null
            },
            onRejected = { reason ->
                continuation.resumeWithException(reason.asJsException())
                null
            },
        )
    }

private fun yandexGamesOrNull(): YandexGamesGlobal? = js("typeof globalThis.YaGames === 'undefined' ? null : globalThis.YaGames")

private fun singleStringArray(value: String): JsArray<JsString> = js("[value]")

private fun singlePropertyObject(
    key: String,
    value: String,
): JsAny = js("({ [key]: value })")

private fun stringPropertyOrNull(
    data: JsAny,
    key: String,
): String? = js("typeof data[key] === 'string' ? data[key] : null")

private fun describeJsFailure(reason: JsAny): String = js("reason && reason.message ? String(reason.message) : String(reason)")
