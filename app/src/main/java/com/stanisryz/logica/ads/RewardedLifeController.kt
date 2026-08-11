package com.stanisryz.logica.ads

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.BuildConfig
import com.stanisryz.logica.economy.EconomyRepository
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The lifecycle of the one rewarded ad the product loads, as the lives UI sees it. */
internal enum class RewardedAdState {
    /** Nothing is loaded and nothing is in flight; a preload may start. */
    IDLE,
    LOADING,
    READY,

    /** An ad is on screen; a second show request must not go through. */
    SHOWING,

    /** No fill, no network, or a failed initialization. Only an explicit retry leaves this state. */
    UNAVAILABLE,
}

/**
 * The only advertising placement in the product: `LIFE_REFILL_REWARDED`, one rewarded ad that
 * restores one life at zero lives.
 *
 * It owns the Yandex loader and the loaded ad — the SDK requires both references to stay alive — and
 * lives in a ViewModel so a rotation or a recomposition never drops an ad that is being shown. The
 * earned reward itself is not kept here: it goes straight to [RewardedLifeReward] and the Room v6
 * ledger, so it survives whatever the UI does next. The SDK's `mobileads-compose` helpers are
 * deliberately unused: their rewarded loader is scoped to a composition, which is exactly the
 * lifetime a full-screen ad must not have.
 *
 * A loaded ad is consumed exactly once. [show] clears the reference before the ad is even on screen,
 * so the same instance can never be shown again, and dismissal returns the controller to [IDLE].
 */
internal class RewardedLifeController(
    context: Context,
    private val reward: RewardedLifeReward,
    private val adUnitId: String = BuildConfig.REWARDED_AD_UNIT_ID,
) : ViewModel() {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(RewardedAdState.IDLE)
    val state: StateFlow<RewardedAdState> = _state.asStateFlow()

    private var loader: RewardedAdLoader? = null
    private var loadedAd: RewardedAd? = null
    private var shownAd: RewardedAd? = null
    private var rewardRetryJob: Job? = null

    /**
     * Loads an ad for the zero-life offer. It is a no-op unless the controller is [IDLE], so a
     * failed load stays failed instead of retrying itself: the player asks for another attempt
     * through [retry], and regeneration and the gem refill never depended on any of this.
     */
    fun preload() {
        retryUnpersistedReward()
        if (_state.value != RewardedAdState.IDLE) return
        _state.value = RewardedAdState.LOADING
        viewModelScope.launch {
            if (!YandexAdsInitializer.ensureInitialized(appContext)) {
                _state.value = RewardedAdState.UNAVAILABLE
                return@launch
            }
            val request = AdRequest.Builder(adUnitId).build()
            val activeLoader = loader ?: runCatching { RewardedAdLoader(appContext) }.getOrNull()
            if (activeLoader == null) {
                _state.value = RewardedAdState.UNAVAILABLE
                return@launch
            }
            loader = activeLoader
            activeLoader.loadAd(request, loadListener)
        }
    }

    /** The one user-triggered way out of [RewardedAdState.UNAVAILABLE]. */
    fun retry() {
        if (_state.value != RewardedAdState.UNAVAILABLE) return
        _state.value = RewardedAdState.IDLE
        preload()
    }

    /** Shows the loaded ad. The action ID for the possible reward is allocated here, once. */
    fun show(activity: Activity) {
        if (_state.value != RewardedAdState.READY) return
        val ad = loadedAd ?: return
        loadedAd = null
        shownAd = ad
        _state.value = RewardedAdState.SHOWING
        reward.beginShow()
        ad.setAdEventListener(eventListener)
        // A show that never reaches the SDK must not leave the offer stuck in SHOWING forever.
        if (runCatching { ad.show(activity) }.isFailure) finishShow()
    }

    /** Drops the loaded ad once the player has lives again; nothing is preloaded on speculation. */
    fun release() {
        if (_state.value == RewardedAdState.SHOWING) return
        loader?.cancelLoading()
        loadedAd?.setAdEventListener(null)
        loadedAd = null
        _state.value = RewardedAdState.IDLE
    }

    override fun onCleared() {
        loadedAd?.setAdEventListener(null)
        shownAd?.setAdEventListener(null)
        loadedAd = null
        shownAd = null
        loader?.cancelLoading()
        loader = null
    }

    private fun retryUnpersistedReward() {
        if (reward.unpersistedActionId == null || rewardRetryJob?.isActive == true) return
        rewardRetryJob = viewModelScope.launch { reward.retryUnpersisted() }
    }

    private val loadListener =
        object : RewardedAdLoadListener {
            override fun onAdLoaded(rewarded: RewardedAd) {
                loadedAd = rewarded
                _state.value = RewardedAdState.READY
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                loadedAd = null
                _state.value = RewardedAdState.UNAVAILABLE
            }
        }

    private val eventListener =
        object : RewardedAdEventListener {
            /**
             * The only callback that may touch the wallet. Shown, impression, click, and dismissal
             * are economically nothing, and a repeat here reuses the same action ID.
             */
            override fun onRewarded(reward: Reward) {
                viewModelScope.launch { this@RewardedLifeController.reward.onRewarded() }
            }

            override fun onAdDismissed() = finishShow()

            override fun onAdFailedToShow(adError: AdError) = finishShow()

            override fun onAdShown() = Unit

            override fun onAdClicked() = Unit

            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        }

    /** The shown ad is spent: its listener goes so it cannot leak, and it is never reused. */
    private fun finishShow() {
        shownAd?.setAdEventListener(null)
        shownAd = null
        _state.value = RewardedAdState.IDLE
        retryUnpersistedReward()
    }
}

internal class RewardedLifeControllerFactory(
    private val context: Context,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RewardedLifeController::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return RewardedLifeController(context, RewardedLifeReward(economyRepository)) as T
    }
}
