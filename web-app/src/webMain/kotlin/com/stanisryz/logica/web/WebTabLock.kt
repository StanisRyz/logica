@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/** Whether this browser tab may run the game. */
internal enum class WebTabLockState {
    /** The lock request has not answered yet. */
    CHECKING,

    /** This tab holds the lock and runs the game. */
    ACTIVE,

    /** Another tab runs the game; this one shows «open in another tab». */
    ELSEWHERE,

    /** «Play here» was tapped: this tab waits for the other one to hand the lock over. */
    WAITING,

    /** The browser has no Web Locks: every tab runs the game, unprotected, as before. */
    UNSUPPORTED,
    ;

    val canPlay: Boolean
        get() = this == ACTIVE || this == UNSUPPORTED
}

/** The browser APIs behind [WebTabLock]: one origin-wide Web Lock plus one broadcast channel. */
internal interface WebTabLockPlatform {
    /** False without `navigator.locks` (or `BroadcastChannel`): the game runs unprotected. */
    val supported: Boolean

    /**
     * Requests the lock and holds it until [release]. With [ifAvailable] a busy lock answers
     * [onUnavailable] at once; without it the request waits for the lock. A failed request answers
     * [onError].
     */
    fun request(
        ifAvailable: Boolean,
        onGranted: () -> Unit,
        onUnavailable: () -> Unit,
        onError: () -> Unit,
    )

    /** Releases the lock this tab holds, if any. */
    fun release()

    /** Asks the tab holding the lock to hand it over. */
    fun postHandoverRequest()

    /** Listens for another tab's handover request. */
    fun setHandoverListener(listener: (() -> Unit)?)
}

/**
 * One active game tab per origin. The page asks for the lock without waiting: the tab that gets
 * it runs the game, any other shows «open in another tab» and binds, loads, and writes nothing.
 * «Play here» broadcasts a handover request and waits for the lock; the active tab receiving it
 * calls [onRelinquish] — which must drop its Player context synchronously, pending cloud write
 * included — before it releases the lock and shows the same screen itself.
 */
internal class WebTabLock(
    private val platform: WebTabLockPlatform,
    private val onRelinquish: () -> Unit,
    private val onReclaim: () -> Unit,
) {
    private val mutableState = MutableStateFlow(WebTabLockState.CHECKING)
    val state: StateFlow<WebTabLockState> = mutableState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        if (!platform.supported) {
            mutableState.value = WebTabLockState.UNSUPPORTED
            return
        }
        platform.setHandoverListener { if (mutableState.value == WebTabLockState.ACTIVE) relinquish() }
        platform.request(
            ifAvailable = true,
            onGranted = { mutableState.value = WebTabLockState.ACTIVE },
            onUnavailable = { mutableState.value = WebTabLockState.ELSEWHERE },
            onError = ::fallBackUnprotected,
        )
    }

    /** «Play here»: waits for the lock, then this tab binds its Player context afresh. */
    fun playHere() {
        if (mutableState.value != WebTabLockState.ELSEWHERE) return
        mutableState.value = WebTabLockState.WAITING
        platform.request(
            ifAvailable = false,
            onGranted = {
                mutableState.value = WebTabLockState.ACTIVE
                onReclaim()
            },
            onUnavailable = { mutableState.value = WebTabLockState.ELSEWHERE },
            onError = ::fallBackUnprotected,
        )
        platform.postHandoverRequest()
    }

    private fun relinquish() {
        // The context (and its deferred cloud write) is gone before another tab can take over.
        onRelinquish()
        platform.release()
        mutableState.value = WebTabLockState.ELSEWHERE
    }

    private fun fallBackUnprotected() {
        val wasPlaying = mutableState.value.canPlay
        mutableState.value = WebTabLockState.UNSUPPORTED
        if (!wasPlaying && started) onReclaim()
    }
}

/** The real browser APIs; a missing `navigator.locks` or `BroadcastChannel` reads as unsupported. */
internal class BrowserWebTabLockPlatform : WebTabLockPlatform {
    private var handle: JsAny? = null
    private var channel: JsAny? = null
    private var listener: (() -> Unit)? = null

    override val supported: Boolean = runCatching { tabLockSupportedJs() }.getOrDefault(false)

    override fun request(
        ifAvailable: Boolean,
        onGranted: () -> Unit,
        onUnavailable: () -> Unit,
        onError: () -> Unit,
    ) {
        release()
        handle =
            runCatching { requestTabLockJs(TAB_LOCK_NAME, ifAvailable, onGranted, onUnavailable, onError) }
                .getOrElse {
                    onError()
                    null
                }
    }

    override fun release() {
        handle?.let { runCatching { releaseTabLockJs(it) } }
        handle = null
    }

    override fun postHandoverRequest() {
        runCatching { postHandoverJs(openChannel()) }
    }

    override fun setHandoverListener(listener: (() -> Unit)?) {
        this.listener = listener
        openChannel()
    }

    private fun openChannel(): JsAny = channel ?: openTabChannelJs(TAB_LOCK_NAME) { listener?.invoke() }.also { channel = it }

    private companion object {
        const val TAB_LOCK_NAME = "logica_active_tab"
    }
}

private fun tabLockSupportedJs(): Boolean =
    js(
        "typeof navigator !== 'undefined' && !!navigator.locks && typeof navigator.locks.request === 'function' " +
            "&& typeof BroadcastChannel === 'function'",
    )

private fun requestTabLockJs(
    name: String,
    ifAvailable: Boolean,
    onGranted: () -> Unit,
    onUnavailable: () -> Unit,
    onError: () -> Unit,
): JsAny =
    js(
        """(function () {
            var handle = { release: null, released: false };
            navigator.locks.request(name, { ifAvailable: ifAvailable }, function (lock) {
                if (lock === null) { onUnavailable(); return undefined; }
                if (handle.released) return undefined;
                return new Promise(function (resolve) { handle.release = resolve; onGranted(); });
            }).catch(function () { if (!handle.released) onError(); });
            return handle;
        })()""",
    )

private fun releaseTabLockJs(handle: JsAny): Unit =
    js(
        """(function () {
            handle.released = true;
            var release = handle.release;
            handle.release = null;
            if (release) release();
        })()""",
    )

private fun openTabChannelJs(
    name: String,
    onHandover: () -> Unit,
): JsAny =
    js(
        """(function () {
            var channel = new BroadcastChannel(name);
            channel.onmessage = function (event) { if (event && event.data === 'handover') onHandover(); };
            return channel;
        })()""",
    )

private fun postHandoverJs(channel: JsAny): Unit = js("channel.postMessage('handover')")
