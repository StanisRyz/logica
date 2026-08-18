@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.js.ExperimentalWasmJsInterop

/** Browser/Yandex host activity only; future gameplay policy remains outside this adapter. */
internal enum class WebHostLifecycleState {
    ACTIVE,
    INACTIVE,
}

internal class WebHostLifecycle : YandexLifecycleListener {
    private var started = false
    private var yandexPaused = false
    private var browserVisible = isBrowserDocumentVisible()
    private var browserFocused = browserDocumentHasFocus()
    private val visibilityCallback = { refreshBrowserState() }
    private val focusCallback = { refreshBrowserState() }
    private val blurCallback = { refreshBrowserState() }

    var state by mutableStateOf(WebHostLifecycleState.INACTIVE)
        private set

    fun start() {
        if (started) return
        started = true
        addDocumentEventListener("visibilitychange", visibilityCallback)
        addWindowEventListener("focus", focusCallback)
        addWindowEventListener("blur", blurCallback)
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

    fun dispose() {
        if (!started) return
        started = false
        removeDocumentEventListener("visibilitychange", visibilityCallback)
        removeWindowEventListener("focus", focusCallback)
        removeWindowEventListener("blur", blurCallback)
        state = WebHostLifecycleState.INACTIVE
    }

    private fun refreshBrowserState() {
        browserVisible = isBrowserDocumentVisible()
        browserFocused = browserDocumentHasFocus()
        updateState()
    }

    private fun updateState() {
        state =
            if (started && !yandexPaused && browserVisible && browserFocused) {
                WebHostLifecycleState.ACTIVE
            } else {
                WebHostLifecycleState.INACTIVE
            }
    }
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
