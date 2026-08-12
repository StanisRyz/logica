package com.stanisryz.logica.ads

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.BuildConfig
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The lifecycle of the one interstitial the product keeps around. */
internal enum class InterstitialAdState {
    /** Nothing is loaded and nothing is in flight; a gameplay preload may start. */
    IDLE,
    LOADING,
    READY,

    /** An ad is on screen; nothing else may present a fullscreen ad. */
    SHOWING,

    /** No fill, no network, or a failed initialization. Only a later gameplay session tries again. */
    UNAVAILABLE,
}

/**
 * The second Yandex placement in the product: `TERMINAL_INTERSTITIAL`, an optional fullscreen ad
 * after a finished attempt. It is kept separate from [RewardedLifeController] on purpose — rewarded
 * is an explicit player action at zero lives that grants one life, this is an automatic presentation
 * that grants nothing at all and never touches gems, lives, results, Daily state, or statistics.
 *
 * It owns the Yandex loader and both the loaded and the shown ad, because the SDK requires those
 * references to stay alive, and it lives in a ViewModel so a rotation cannot drop an ad mid-show.
 * The regular loader API is used rather than the `mobileads-compose` helpers, whose ad ownership is
 * scoped to a composition — exactly the lifetime a fullscreen ad must not have.
 *
 * A loaded ad is consumed exactly once: [show] clears the reusable slot and moves to
 * [InterstitialAdState.SHOWING] before the SDK is called, so the same instance can never appear
 * twice. Every failure — initialization, load, or show — leaves the terminal flow untouched.
 */
internal class InterstitialAdController(
    context: Context,
    private val opportunities: InterstitialOpportunities,
    private val cooldown: InterstitialCooldownPolicy,
    private val gate: FullscreenAdGate = FullscreenAdGate.SHARED,
    private val adUnitId: String = BuildConfig.INTERSTITIAL_AD_UNIT_ID,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val coordinator = InterstitialAdCoordinator(cooldown)

    private val mutableState = MutableStateFlow(InterstitialAdState.IDLE)
    val state: StateFlow<InterstitialAdState> = mutableState.asStateFlow()

    /** What the shell watches: the terminal result that just became durable, if there is one. */
    val pendingOpportunity: StateFlow<InterstitialOpportunity?> = opportunities.pending

    private var loader: InterstitialAdLoader? = null
    private var loadedAd: InterstitialAd? = null
    private var shownAd: InterstitialAd? = null
    private var gameplayPreloadJob: Job? = null

    /** The terminal action waiting behind the ad currently on screen, if there is one. */
    private var pendingTerminalAction: (() -> Unit)? = null

    /**
     * Gameplay-aware preloading: the download starts while the player is still playing rather than
     * at the terminal moment, and only from a real gameplay destination — never from the Game hub,
     * the Store, Profile, Settings, or a tutorial.
     *
     * If the cooldown is still running the preload is not abandoned but scheduled once: a single
     * suspending wait for the remaining time, cancelled the moment gameplay ends. No worker, no
     * repetition, and no polling.
     */
    fun onGameplayStarted() {
        if (gameplayPreloadJob?.isActive == true) return
        gameplayPreloadJob =
            viewModelScope.launch {
                val remaining = cooldown.remainingMillis()
                if (remaining > 0L) delay(remaining)
                preload()
            }
    }

    /** Gameplay ended before the scheduled preload could happen, so that work is dropped. */
    fun onGameplayStopped() {
        gameplayPreloadJob?.cancel()
        gameplayPreloadJob = null
    }

    /**
     * The terminal hand-off, driven by the player's first action on a finished attempt rather than
     * by the attempt ending. The opportunity is consumed synchronously so a recomposition cannot
     * rediscover it, and the decision itself is delegated to the coordinator, which allows at most
     * one show attempt per result.
     *
     * [onFinished] is what continues the action the player asked for and runs exactly once: right
     * away when no ad reaches the screen, and after the ad is dismissed or fails when one does. It
     * is armed before the SDK is called, because `show` may report a failure synchronously.
     */
    fun showForTerminalAction(
        opportunity: InterstitialOpportunity,
        activity: Activity?,
        onFinished: () -> Unit,
    ) {
        opportunities.consume(opportunity)
        viewModelScope.launch {
            pendingTerminalAction = onFinished
            val shown =
                coordinator.attemptShow(
                    opportunity = opportunity,
                    isReady = { mutableState.value == InterstitialAdState.READY },
                    show = { activity != null && show(activity) },
                )
            if (!shown) continueTerminalAction()
        }
    }

    private suspend fun preload() {
        if (mutableState.value != InterstitialAdState.IDLE) return
        mutableState.value = InterstitialAdState.LOADING
        if (!YandexAdsInitializer.ensureInitialized(appContext)) {
            mutableState.value = InterstitialAdState.UNAVAILABLE
            return
        }
        val activeLoader = loader ?: runCatching { InterstitialAdLoader(appContext) }.getOrNull()
        if (activeLoader == null) {
            mutableState.value = InterstitialAdState.UNAVAILABLE
            return
        }
        loader = activeLoader
        activeLoader.loadAd(AdRequest.Builder(adUnitId).build(), loadListener)
    }

    /** Consumes the loaded ad. `false` means nothing reached the screen and nothing was spent. */
    private fun show(activity: Activity): Boolean {
        if (mutableState.value != InterstitialAdState.READY) return false
        val ad = loadedAd ?: return false
        // The rewarded ad may be on screen; losing the gate is simply no interstitial this time.
        if (!gate.tryAcquire()) return false
        loadedAd = null
        shownAd = ad
        mutableState.value = InterstitialAdState.SHOWING
        ad.setAdEventListener(eventListener)
        if (runCatching { ad.show(activity) }.isFailure) {
            finishShow()
            return false
        }
        return true
    }

    override fun onCleared() {
        gameplayPreloadJob?.cancel()
        gameplayPreloadJob = null
        // The screen that asked for the action is gone; running it into a dead composition is not.
        pendingTerminalAction = null
        loadedAd?.setAdEventListener(null)
        shownAd?.setAdEventListener(null)
        loadedAd = null
        shownAd = null
        loader?.cancelLoading()
        loader = null
        if (mutableState.value == InterstitialAdState.SHOWING) gate.release()
    }

    private val loadListener =
        object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                loadedAd = interstitialAd
                mutableState.value = InterstitialAdState.READY
            }

            /** A failed load is final until a later gameplay session; it never retries in a loop. */
            override fun onAdFailedToLoad(error: AdRequestError) {
                loadedAd = null
                mutableState.value = InterstitialAdState.UNAVAILABLE
            }
        }

    private val eventListener =
        object : InterstitialAdEventListener {
            /** The only event that starts the cooldown: Yandex confirmed the ad actually appeared. */
            override fun onAdShown() {
                viewModelScope.launch { cooldown.recordShown() }
            }

            /** Failed before the ad appeared: no cooldown, no retry, straight on to the result UI. */
            override fun onAdFailedToShow(adError: AdError) = finishShow()

            override fun onAdDismissed() = finishShow()

            /** Advertising engagement is economically nothing. */
            override fun onAdClicked() = Unit

            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        }

    /** The shown ad is spent: its listener goes, the fullscreen gate opens, and it is never reused. */
    private fun finishShow() {
        shownAd?.setAdEventListener(null)
        shownAd = null
        mutableState.value = InterstitialAdState.IDLE
        gate.release()
        // Whatever the player asked for is what happens next, dismissal or failure alike.
        continueTerminalAction()
    }

    /** Runs the waiting terminal action once; a second call after it has run does nothing. */
    private fun continueTerminalAction() {
        val action = pendingTerminalAction ?: return
        pendingTerminalAction = null
        action()
    }
}

internal class InterstitialAdControllerFactory(
    private val context: Context,
    private val opportunities: InterstitialOpportunities,
    private val cooldown: InterstitialCooldownPolicy,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InterstitialAdController::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return InterstitialAdController(context, opportunities, cooldown) as T
    }
}
