@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PlatformLifecycle
import com.stanisryz.logica.platform.PlatformLifecycleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.ExperimentalWasmJsInterop

/** Browser/Yandex host activity only; future gameplay policy remains outside this adapter. */
internal class WebHostLifecycle :
    PlatformLifecycle,
    WebBootstrapLifecycle,
    WebFullscreenAdActivity {
    private val mutableState = MutableStateFlow(PlatformLifecycleState.INACTIVE)
    override val state: StateFlow<PlatformLifecycleState> = mutableState.asStateFlow()

    private var started = false
    private var yandexPaused = false
    private var fullscreenAdActive = false
    private var browserVisible = isBrowserDocumentVisible()
    private var browserFocused = browserDocumentHasFocus()
    private val visibilityCallback = { refreshBrowserState() }
    private val focusCallback = { setBrowserFocused(true) }
    private val blurCallback = { setBrowserFocused(false) }

    // Inside the Yandex iframe the page can be played while `document.hasFocus()` stays false (the
    // game canvas takes the pointer without moving focus), which would keep the host INACTIVE and
    // every sound silent. A tap or key press inside the page is proof the player is here; a later
    // blur or hidden document still makes it inactive.
    private val interactionCallback = { setBrowserFocused(true) }

    override fun start() {
        if (started) return
        started = true
        addDocumentEventListener("visibilitychange", visibilityCallback)
        addWindowEventListener("focus", focusCallback)
        addWindowEventListener("blur", blurCallback)
        addWindowCaptureListener("pointerdown", interactionCallback)
        addWindowCaptureListener("keydown", interactionCallback)
        browserFocused = browserDocumentHasFocus()
        refreshBrowserState()
    }

    override fun onPause() {
        yandexPaused = true
        updateState()
    }

    override fun onResume() {
        yandexPaused = false
        refreshBrowserState()
    }

    /**
     * Fullscreen-ad suppression input of the EFFECTIVE lifecycle: while a rewarded/interstitial
     * advertisement owns the screen the host reports INACTIVE (GameplayAPI stops, audio pauses).
     * Clearing the flag recomputes from the real browser/Yandex conditions instead of forcing
     * ACTIVE — closing an ad over a hidden tab keeps the application inactive.
     */
    override fun setFullscreenAdActive(active: Boolean) {
        fullscreenAdActive = active
        updateState()
    }

    override fun dispose() {
        if (!started) return
        started = false
        removeDocumentEventListener("visibilitychange", visibilityCallback)
        removeWindowEventListener("focus", focusCallback)
        removeWindowEventListener("blur", blurCallback)
        removeWindowCaptureListener("pointerdown", interactionCallback)
        removeWindowCaptureListener("keydown", interactionCallback)
        mutableState.value = PlatformLifecycleState.INACTIVE
    }

    private fun setBrowserFocused(focused: Boolean) {
        browserFocused = focused
        refreshBrowserState()
    }

    private fun refreshBrowserState() {
        browserVisible = isBrowserDocumentVisible()
        updateState()
    }

    private fun updateState() {
        mutableState.value =
            if (
                WebEffectiveLifecycle.isActive(
                    started = started,
                    yandexPaused = yandexPaused,
                    fullscreenAdActive = fullscreenAdActive,
                    browserVisible = browserVisible,
                    browserFocused = browserFocused,
                )
            ) {
                PlatformLifecycleState.ACTIVE
            } else {
                PlatformLifecycleState.INACTIVE
            }
    }
}

/**
 * The one effective Web lifecycle rule shared by GameplayAPI and audio consumers:
 * ACTIVE requires the host started, no Yandex pause, no fullscreen advertisement,
 * a visible document, and window focus. One direction only:
 * raw conditions (+ fullscreen-ad flag) -> effective state -> consumers.
 */
internal object WebEffectiveLifecycle {
    fun isActive(
        started: Boolean,
        yandexPaused: Boolean,
        fullscreenAdActive: Boolean,
        browserVisible: Boolean,
        browserFocused: Boolean,
    ): Boolean = started && !yandexPaused && !fullscreenAdActive && browserVisible && browserFocused
}

private fun isBrowserDocumentVisible(): Boolean = js("globalThis.document.visibilityState !== 'hidden'")

private fun browserDocumentHasFocus(): Boolean = js("globalThis.document.hasFocus()")

private fun addDocumentEventListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.document.addEventListener(eventName, callback)")

private fun removeDocumentEventListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.document.removeEventListener(eventName, callback)")

private fun addWindowEventListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.addEventListener(eventName, callback)")

private fun removeWindowEventListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.removeEventListener(eventName, callback)")

private fun addWindowCaptureListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.addEventListener(eventName, callback, true)")

private fun removeWindowCaptureListener(
    eventName: String,
    callback: () -> Unit,
): Unit = js("globalThis.removeEventListener(eventName, callback, true)")
