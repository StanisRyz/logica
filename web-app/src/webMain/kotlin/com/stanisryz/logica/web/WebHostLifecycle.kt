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

    /** Whether sound may play by the host's conditions ([WebEffectiveLifecycle.isAudible]); focus plays no part. */
    var audioConditionsMet = false
        private set

    /**
     * Told synchronously whenever [audioConditionsMet] changes, inside the very event that changed
     * it, so the page's audio state is already decided when a tap reaches its own listener.
     */
    var onAudioConditionsChanged: (() -> Unit)? = null

    /** The host conditions that keep sound off right now, for the console diagnostics; empty when none. */
    val audioBlockers: String
        get() =
            listOfNotNull(
                "not started".takeUnless { started },
                "Yandex pause".takeIf { yandexPaused },
                "fullscreen ad".takeIf { fullscreenAdActive },
                "hidden tab".takeUnless { browserVisible },
                "window blur".takeIf { blurredSinceInteraction },
            ).joinToString()

    private var started = false
    private var yandexPaused = false
    private var fullscreenAdActive = false
    private var browserVisible = isBrowserDocumentVisible()
    private var browserFocused = browserDocumentHasFocus()

    // Sound stops on a blur event (requirement 1.3), not because the game never had focus: inside
    // the Yandex iframe `document.hasFocus()` starts false. A focus event or any tap or key press ends it.
    private var blurredSinceInteraction = false
    private val visibilityCallback = { refreshBrowserState() }
    private val focusCallback = { onPageInteraction() }
    private val blurCallback = {
        blurredSinceInteraction = true
        setBrowserFocused(false)
    }

    // Inside the Yandex iframe the page can be played while `document.hasFocus()` stays false (the
    // game canvas takes the pointer without moving focus), which would keep the host INACTIVE and
    // every sound silent. A tap or key press inside the page is proof the player is here; a later
    // blur or hidden document still makes it inactive.
    private val interactionCallback = { onPageInteraction() }

    /**
     * The player is here: a focus event, or a tap or key press in the page. The sound player calls it
     * from its own gesture listener too, so sound is allowed again before that gesture wakes the audio.
     */
    fun onPageInteraction() {
        blurredSinceInteraction = false
        setBrowserFocused(true)
    }

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
        val audible =
            WebEffectiveLifecycle.isAudible(
                started = started,
                yandexPaused = yandexPaused,
                fullscreenAdActive = fullscreenAdActive,
                browserVisible = browserVisible,
                blurredSinceInteraction = blurredSinceInteraction,
            )
        if (audible != audioConditionsMet) {
            audioConditionsMet = audible
            onAudioConditionsChanged?.invoke()
        }
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
 * The one effective Web lifecycle rule for GameplayAPI (audio follows [isAudible], the same without focus):
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

    /**
     * The audio rule: the same conditions, but a lost focus counts only as a blur event since the
     * player's last focus, tap, or key press (requirement 1.3). A game that never had focus — inside
     * the Yandex iframe `document.hasFocus()` starts false — still sounds.
     */
    fun isAudible(
        started: Boolean,
        yandexPaused: Boolean,
        fullscreenAdActive: Boolean,
        browserVisible: Boolean,
        blurredSinceInteraction: Boolean,
    ): Boolean = started && !yandexPaused && !fullscreenAdActive && browserVisible && !blurredSinceInteraction
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
