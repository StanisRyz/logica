@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PaymentPurchaseSnapshot
import com.stanisryz.logica.platform.PaymentResult
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

internal interface WebPlayerContextEvents {
    fun setAccountSelectionOpenedListener(listener: (() -> Unit)?)

    fun setPlayerContextChangedListener(listener: (() -> Unit)?)
}

/** The only raw JavaScript boundary for the Yandex Games SDK. */
internal class YandexGamesBridge :
    WebPlayerContextEvents,
    WebStickyBannerBridge {
    private var sdk: YandexSdk? = null
    private var cachedPlayer: YandexPlayer? = null
    private var playerRequest: Promise<YandexPlayer>? = null
    private var initializationStarted = false
    private var paymentsCache: YandexPayments? = null
    private var disposed = false
    private var loadingReadySent = false
    private var gameplayActive = false
    private var pauseCallback: (() -> Unit)? = null
    private var resumeCallback: (() -> Unit)? = null
    private var accountSelectionOpenedEvent: String? = null
    private var accountSelectionOpenedCallback: (() -> Unit)? = null
    private var accountSelectionClosedEvent: String? = null
    private var accountSelectionClosedCallback: (() -> Unit)? = null
    private var accountSelectionOpenedListener: (() -> Unit)? = null
    private var playerContextChangedListener: (() -> Unit)? = null

    val isAvailable: Boolean
        get() = yandexGamesOrNull() != null

    val isReady: Boolean
        get() = sdk != null

    /**
     * Platform language reported by `ysdk.environment.i18n.lang`, read after SDK initialization;
     * raw SDK objects never leave this bridge. Null when unavailable or structurally unexpected.
     */
    fun platformLanguage(): String? =
        runCatching { sdk?.let(::sdkPlatformLanguage) }.getOrNull()

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
                        subscribePlayerContextChanges(initializedSdk)
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

    /** Resolves the current Player once and reuses it until the SDK reports an account-context change. */
    suspend fun playerSnapshot(): YandexPlayerSnapshot = player().snapshot()

    suspend fun readPlayerData(key: String): String? {
        val data = player().getData(singleStringArray(key)).await()
        return stringPropertyOrNull(data, key)
    }

    suspend fun writePlayerData(
        key: String,
        value: String,
        flush: Boolean,
    ) {
        player().setData(singlePropertyObject(key, value), flush).await()
    }

    override fun setAccountSelectionOpenedListener(listener: (() -> Unit)?) {
        accountSelectionOpenedListener = listener
    }

    override fun setPlayerContextChangedListener(listener: (() -> Unit)?) {
        playerContextChangedListener = listener
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

    /** Shows one rewarded video; callbacks arrive on the JS thread exactly once each. */
    fun showRewardedVideo(
        onOpen: () -> Unit,
        onRewarded: () -> Unit,
        onClose: () -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        val initializedSdk = sdk ?: return false
        val adv = initializedSdk.adv ?: return false
        return try {
            adv.showRewardedVideo(
                rewardedVideoCallbacksJs(onOpen, onRewarded, onClose) { reason ->
                    onError(describeJsFailure(reason))
                },
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Shows one fullscreen advertisement; [wasShown] reports whether anything was displayed. */
    fun showFullscreenAdv(
        onOpen: () -> Unit,
        onClose: (wasShown: Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        val initializedSdk = sdk ?: return false
        val adv = initializedSdk.adv ?: return false
        return try {
            adv.showFullscreenAdv(
                fullscreenCallbacksJs(onOpen, onClose) { reason ->
                    onError(describeJsFailure(reason))
                },
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Sticky banner support. The real advertisement is owned and rendered by Yandex Games; these
     * methods only forward the platform calls. Missing SDK APIs are safe no-ops (return false).
     */
    override fun showStickyBanner(): Boolean =
        try {
            val adv = sdk?.adv ?: return false
            adv.showBannerAdv()
            true
        } catch (_: Throwable) {
            false
        }

    override fun hideStickyBanner(): Boolean =
        try {
            val adv = sdk?.adv ?: return false
            adv.hideBannerAdv()
            true
        } catch (_: Throwable) {
            false
        }

    /** Queries whether the platform currently shows the sticky banner; null when unsupported. */
    override suspend fun stickyBannerStatus(): Boolean? =
        try {
            val adv = sdk?.adv ?: return null
            val status = adv.getBannerAdvStatus()?.await() ?: return null
            booleanPropertyOrNull(status, STICKY_BANNER_SHOWING_KEY)
        } catch (_: Throwable) {
            null
        }

    // region Yandex Games Payments (client-side consumable flow)

    /** Whether the loaded SDK exposes the Payments API at all (cheap structural check). */
    fun isPaymentsSupported(): Boolean =
        runCatching {
            val initializedSdk = sdk ?: return false
            sdkSupportsPayments(initializedSdk)
        }.getOrDefault(false)

    private suspend fun paymentsOrNull(): YandexPayments? {
        paymentsCache?.let { return it }
        val initializedSdk = sdk ?: return null
        if (!sdkSupportsPayments(initializedSdk)) return null
        val created =
            try {
                initializedSdk.getPayments(paymentsOptions()).await()
            } catch (_: Throwable) {
                return null
            }
        // Client-side unsigned flow for this stage: no secret key ever reaches the Web build.
        paymentsCache = created
        return created
    }

    /** Yandex catalog snapshot; the portal is the single source of truth for price/currency. */
    suspend fun paymentsCatalog(): List<PaymentProductSnapshot>? =
        try {
            val payments = paymentsOrNull() ?: return null
            val rawCatalog = payments.getCatalog().await()
            val count = jsArrayLength(rawCatalog)
            val result = ArrayList<PaymentProductSnapshot>(count)
            for (index in 0 until count) {
                val entry = jsArrayGet(rawCatalog, index)
                result +=
                    PaymentProductSnapshot(
                        productId = stringPropertyOrNull(entry, PRODUCT_ID_KEY) ?: continue,
                        title = stringPropertyOrNull(entry, TITLE_KEY),
                        description = stringPropertyOrNull(entry, DESCRIPTION_KEY),
                        price = stringPropertyOrNull(entry, PRICE_KEY),
                        priceValue = stringPropertyOrNull(entry, PRICE_VALUE_KEY),
                        priceCurrencyCode = stringPropertyOrNull(entry, PRICE_CURRENCY_CODE_KEY),
                        priceCurrencyImageUrl = stringPropertyOrNull(entry, PRICE_CURRENCY_IMAGE_URL_KEY),
                    )
            }
            result
        } catch (_: Throwable) {
            null
        }

    /** Interactive client-side purchase; the frame/cancellation/failure handling stays here. */
    suspend fun purchaseProduct(productId: String): PaymentResult =
        try {
            val payments = paymentsOrNull() ?: return PaymentResult.Unavailable
            val purchase = payments.purchase(singlePropertyObject(PRODUCT_ID_KEY, productId)).await()
            val token = stringPropertyOrNull(purchase, PURCHASE_TOKEN_KEY)
            val purchasedProductId = stringPropertyOrNull(purchase, PURCHASE_PRODUCT_ID_KEY) ?: productId
            if (token.isNullOrBlank()) {
                PaymentResult.Failed("The payment response carried no purchase token.")
            } else {
                PaymentResult.Completed(PaymentPurchaseSnapshot(purchaseToken = token, productId = purchasedProductId))
            }
        } catch (error: Throwable) {
            val detail = error.message
            if (detail?.contains(USER_CANCELLED_MARKER) == true) {
                PaymentResult.Cancelled
            } else {
                PaymentResult.Failed(detail)
            }
        }

    /** Unconsumed purchases for the current Player; null when unsupported/failed/malformed. */
    suspend fun pendingPurchases(): List<PaymentPurchaseSnapshot>? =
        try {
            val payments = paymentsOrNull() ?: return null
            // Unsigned client-side contract: getPurchases() resolves to the purchase array
            // itself, not to a wrapper object carrying a `purchases` property.
            val rawPurchases = payments.getPurchases().await()
            if (!isJsArray(rawPurchases)) return null // malformed result: failed query, NOT "none"
            val count = jsArrayLength(rawPurchases)
            val result = ArrayList<PaymentPurchaseSnapshot>(count)
            for (index in 0 until count) {
                val entry = jsArrayGet(rawPurchases, index)
                val token = stringPropertyOrNull(entry, PURCHASE_TOKEN_KEY) ?: continue
                result += PaymentPurchaseSnapshot(
                    purchaseToken = token,
                    productId = stringPropertyOrNull(entry, PURCHASE_PRODUCT_ID_KEY) ?: "",
                )
            }
            result
        } catch (_: Throwable) {
            null
        }

    /** Consumes one fulfilled consumable; true only when the platform confirmed it. */
    suspend fun consumePurchaseToken(purchaseToken: String): Boolean =
        try {
            val payments = paymentsOrNull() ?: return false
            payments.consumePurchase(purchaseToken).await()
            true
        } catch (_: Throwable) {
            false
        }

    // endregion


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
        val accountOpenedEvent = accountSelectionOpenedEvent
        val accountOpenedCallback = accountSelectionOpenedCallback
        if (initializedSdk != null && accountOpenedEvent != null && accountOpenedCallback != null) {
            runCatching { initializedSdk.off(accountOpenedEvent, accountOpenedCallback) }
        }
        val accountEvent = accountSelectionClosedEvent
        val accountCallback = accountSelectionClosedCallback
        if (initializedSdk != null && accountEvent != null && accountCallback != null) {
            runCatching { initializedSdk.off(accountEvent, accountCallback) }
        }
        pauseCallback = null
        resumeCallback = null
        accountSelectionOpenedEvent = null
        accountSelectionOpenedCallback = null
        accountSelectionClosedEvent = null
        accountSelectionClosedCallback = null
        accountSelectionOpenedListener = null
        playerContextChangedListener = null
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

    private suspend fun player(): YandexPlayer {
        cachedPlayer?.let { return it }
        val initializedSdk = requireSdk()
        val request = playerRequest ?: initializedSdk.getPlayer().also { playerRequest = it }

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

    private fun requireSdk(): YandexSdk = checkNotNull(sdk) { "Yandex Games SDK is not ready." }

    private fun YandexPlayer.snapshot(): YandexPlayerSnapshot =
        YandexPlayerSnapshot(
            isAuthorized = runCatching { isAuthorized() }.getOrDefault(false),
            uniqueId = optionalText { getUniqueID() },
            displayName = optionalText { getName() },
            avatarReference = optionalText { getPhoto(PLAYER_PHOTO_SIZE) },
        )

    private fun optionalText(block: () -> String?): String? = runCatching(block).getOrNull()?.trim()?.takeIf(String::isNotEmpty)

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

    private fun subscribePlayerContextChanges(initializedSdk: YandexSdk) {
        runCatching {
            val eventName = initializedSdk.events?.accountSelectionDialogOpened ?: return@runCatching
            val callback: () -> Unit = {
                accountSelectionOpenedListener?.invoke()
                Unit
            }
            initializedSdk.on(eventName, callback)
            accountSelectionOpenedEvent = eventName
            accountSelectionOpenedCallback = callback
        }
        runCatching {
            val eventName = initializedSdk.events?.accountSelectionDialogClosed ?: return
            val callback: () -> Unit = {
                cachedPlayer = null
                playerRequest = null
                playerContextChangedListener?.invoke()
                Unit
            }
            initializedSdk.on(eventName, callback)
            accountSelectionClosedEvent = eventName
            accountSelectionClosedCallback = callback
        }
    }

    private companion object {
        const val GAME_API_PAUSE = "game_api_pause"
        const val GAME_API_RESUME = "game_api_resume"
        const val PLAYER_PHOTO_SIZE = "small"
        const val STICKY_BANNER_SHOWING_KEY = "stickyAdvIsShowing"
        const val PRODUCT_ID_KEY = "id"
        const val TITLE_KEY = "title"
        const val DESCRIPTION_KEY = "description"
        const val PRICE_KEY = "price"
        const val PRICE_VALUE_KEY = "priceValue"
        const val PRICE_CURRENCY_CODE_KEY = "priceCurrencyCode"
        const val PRICE_CURRENCY_IMAGE_URL_KEY = "priceCurrencyImageUrl"
        const val PURCHASE_TOKEN_KEY = "purchaseToken"
        const val PURCHASE_PRODUCT_ID_KEY = "productID"
        const val USER_CANCELLED_MARKER = "USER_CANCELLED"
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

    @JsName("getPayments")
    fun getPayments(options: JsAny): Promise<YandexPayments>

    @JsName("EVENTS")
    val events: YandexSdkEvents?

    val adv: YandexAdv?

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

private external interface YandexPayments : JsAny {
    fun getCatalog(): Promise<JsAny>

    fun purchase(options: JsAny): Promise<JsAny>

    fun getPurchases(): Promise<JsAny>

    fun consumePurchase(purchaseToken: String): Promise<JsAny>
}
private external interface YandexAdv : JsAny {
    fun showRewardedVideo(callbacks: YandexRewardedVideoCallbacks)

    fun showFullscreenAdv(callbacks: YandexFullscreenCallbacks)

    @JsName("showBannerAdv")
    fun showBannerAdv(): Promise<JsAny>?

    @JsName("hideBannerAdv")
    fun hideBannerAdv()

    @JsName("getBannerAdvStatus")
    fun getBannerAdvStatus(): Promise<JsAny>?
}

private external interface YandexRewardedVideoCallbacks : JsAny {
    fun onOpen()

    fun onRewarded()

    fun onClose()

    fun onError(error: JsAny?)
}

private external interface YandexFullscreenCallbacks : JsAny {
    fun onOpen()

    fun onClose(wasShown: Boolean)

    fun onError(error: JsAny?)
}

private external interface YandexSdkEvents : JsAny {
    @JsName("ACCOUNT_SELECTION_DIALOG_OPENED")
    val accountSelectionDialogOpened: String?

    @JsName("ACCOUNT_SELECTION_DIALOG_CLOSED")
    val accountSelectionDialogClosed: String?
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

private fun rewardedVideoCallbacksJs(
    onOpen: () -> Unit,
    onRewarded: () -> Unit,
    onClose: () -> Unit,
    onError: (JsAny?) -> Unit,
): YandexRewardedVideoCallbacks =
    js(
        """({
            onOpen: () => onOpen(),
            onRewarded: () => onRewarded(),
            onClose: () => onClose(),
            onError: (error) => onError(error),
        })""",
    )

private fun fullscreenCallbacksJs(
    onOpen: () -> Unit,
    onClose: (Boolean) -> Unit,
    onError: (JsAny?) -> Unit,
): YandexFullscreenCallbacks =
    js(
        """({
            onOpen: () => onOpen(),
            onClose: (wasShown) => onClose(wasShown === true),
            onError: (error) => onError(error),
        })""",
    )

private fun singleStringArray(value: String): JsArray<JsString> = js("[value]")

private fun singlePropertyObject(
    key: String,
    value: String,
): JsAny = js("({ [key]: value })")

private fun stringPropertyOrNull(
    data: JsAny,
    key: String,
): String? = js("typeof data[key] === 'string' ? data[key] : null")

private fun sdkSupportsPayments(sdk: YandexSdk): Boolean = js("typeof sdk.getPayments === 'function'")

private fun isJsArray(value: JsAny): Boolean = js("Array.isArray(value)")

private fun sdkPlatformLanguage(sdk: YandexSdk): String? =
    js("typeof sdk.environment === 'object' && sdk.environment != null && typeof sdk.environment.i18n === 'object' && sdk.environment.i18n != null && typeof sdk.environment.i18n.lang === 'string' ? sdk.environment.i18n.lang : null")

private fun paymentsOptions(): JsAny = js("({ signed: false })")

private fun jsArrayLength(array: JsAny): Int = js("array.length")

private fun jsArrayGet(
    array: JsAny,
    index: Int,
): JsAny = js("array[index]")

private fun anyPropertyOrNull(
    data: JsAny,
    key: String,
): JsAny? = js("data[key] === undefined || data[key] === null ? null : data[key]")

private fun booleanPropertyOrNull(
    data: JsAny,
    key: String,
): Boolean? = js("typeof data[key] === 'boolean' ? data[key] : null")

private fun describeJsFailure(reason: JsAny?): String = js("reason && reason.message ? String(reason.message) : String(reason)")
