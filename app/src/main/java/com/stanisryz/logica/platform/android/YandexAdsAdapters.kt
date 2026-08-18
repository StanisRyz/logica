package com.stanisryz.logica.platform.android

import android.content.Context
import com.stanisryz.logica.ads.FullscreenAdGate
import com.stanisryz.logica.ads.YandexAdsInitializer
import com.stanisryz.logica.platform.AdDisplayHost
import com.stanisryz.logica.platform.AdShowStart
import com.stanisryz.logica.platform.FullscreenAdEvent
import com.stanisryz.logica.platform.FullscreenAdsGateway
import com.stanisryz.logica.platform.PlatformAdState
import com.stanisryz.logica.platform.RewardedAdEvent
import com.stanisryz.logica.platform.RewardedAdsGateway
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Yandex Mobile Ads mechanics for the rewarded placement; reward policy stays in the controller. */
internal class AndroidYandexRewardedAdsGateway(
    context: Context,
    private val adUnitId: String,
    private val gate: FullscreenAdGate = FullscreenAdGate.SHARED,
) : RewardedAdsGateway {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlatformAdState.IDLE)
    override val state: StateFlow<PlatformAdState> = mutableState.asStateFlow()

    private var loader: RewardedAdLoader? = null
    private var loadedAd: RewardedAd? = null
    private var shownAd: RewardedAd? = null
    private var activeEvents: ((RewardedAdEvent) -> Unit)? = null

    override suspend fun preload() {
        if (mutableState.value != PlatformAdState.IDLE) return
        mutableState.value = PlatformAdState.LOADING
        if (!YandexAdsInitializer.ensureInitialized(appContext)) {
            mutableState.value = PlatformAdState.UNAVAILABLE
            return
        }
        val activeLoader = loader ?: runCatching { RewardedAdLoader(appContext) }.getOrNull()
        if (activeLoader == null) {
            mutableState.value = PlatformAdState.UNAVAILABLE
            return
        }
        loader = activeLoader
        activeLoader.loadAd(AdRequest.Builder(adUnitId).build(), loadListener)
    }

    override fun show(
        host: AdDisplayHost,
        onWillShow: () -> Unit,
        onEvent: (RewardedAdEvent) -> Unit,
    ): AdShowStart {
        val activity = (host as? AndroidAdDisplayHost)?.activity ?: return AdShowStart.UNAVAILABLE
        if (mutableState.value != PlatformAdState.READY) return AdShowStart.UNAVAILABLE
        val ad = loadedAd ?: return AdShowStart.UNAVAILABLE
        if (!gate.tryAcquire()) return AdShowStart.UNAVAILABLE
        loadedAd = null
        shownAd = ad
        activeEvents = onEvent
        mutableState.value = PlatformAdState.SHOWING
        onWillShow()
        ad.setAdEventListener(eventListener)
        if (runCatching { ad.show(activity) }.isFailure) {
            activeEvents?.invoke(RewardedAdEvent.Failed)
            finishShow()
            return AdShowStart.FAILED
        }
        return AdShowStart.STARTED
    }

    override fun release() {
        if (mutableState.value == PlatformAdState.SHOWING) return
        loader?.cancelLoading()
        loadedAd?.setAdEventListener(null)
        loadedAd = null
        mutableState.value = PlatformAdState.IDLE
    }

    private val loadListener =
        object : RewardedAdLoadListener {
            override fun onAdLoaded(rewarded: RewardedAd) {
                loadedAd = rewarded
                mutableState.value = PlatformAdState.READY
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                loadedAd = null
                mutableState.value = PlatformAdState.UNAVAILABLE
            }
        }

    private val eventListener =
        object : RewardedAdEventListener {
            override fun onRewarded(reward: Reward) {
                activeEvents?.invoke(RewardedAdEvent.Rewarded)
            }

            override fun onAdDismissed() {
                activeEvents?.invoke(RewardedAdEvent.Dismissed)
                finishShow()
            }

            override fun onAdFailedToShow(adError: AdError) {
                activeEvents?.invoke(RewardedAdEvent.Failed)
                finishShow()
            }

            override fun onAdShown() = Unit

            override fun onAdClicked() = Unit

            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        }

    private fun finishShow() {
        shownAd?.setAdEventListener(null)
        shownAd = null
        activeEvents = null
        mutableState.value = PlatformAdState.IDLE
        gate.release()
    }
}

/** Yandex Mobile Ads mechanics for interstitials; cooldown and deferred actions stay above it. */
internal class AndroidYandexFullscreenAdsGateway(
    context: Context,
    private val adUnitId: String,
    private val gate: FullscreenAdGate = FullscreenAdGate.SHARED,
) : FullscreenAdsGateway {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlatformAdState.IDLE)
    override val state: StateFlow<PlatformAdState> = mutableState.asStateFlow()

    private var loader: InterstitialAdLoader? = null
    private var loadedAd: InterstitialAd? = null
    private var shownAd: InterstitialAd? = null
    private var activeEvents: ((FullscreenAdEvent) -> Unit)? = null

    override suspend fun preload() {
        if (mutableState.value != PlatformAdState.IDLE) return
        mutableState.value = PlatformAdState.LOADING
        if (!YandexAdsInitializer.ensureInitialized(appContext)) {
            mutableState.value = PlatformAdState.UNAVAILABLE
            return
        }
        val activeLoader = loader ?: runCatching { InterstitialAdLoader(appContext) }.getOrNull()
        if (activeLoader == null) {
            mutableState.value = PlatformAdState.UNAVAILABLE
            return
        }
        loader = activeLoader
        activeLoader.loadAd(AdRequest.Builder(adUnitId).build(), loadListener)
    }

    override fun show(
        host: AdDisplayHost,
        onEvent: (FullscreenAdEvent) -> Unit,
    ): AdShowStart {
        val activity = (host as? AndroidAdDisplayHost)?.activity ?: return AdShowStart.UNAVAILABLE
        if (mutableState.value != PlatformAdState.READY) return AdShowStart.UNAVAILABLE
        val ad = loadedAd ?: return AdShowStart.UNAVAILABLE
        if (!gate.tryAcquire()) return AdShowStart.UNAVAILABLE
        loadedAd = null
        shownAd = ad
        activeEvents = onEvent
        mutableState.value = PlatformAdState.SHOWING
        ad.setAdEventListener(eventListener)
        if (runCatching { ad.show(activity) }.isFailure) {
            activeEvents?.invoke(FullscreenAdEvent.Failed)
            finishShow()
            return AdShowStart.FAILED
        }
        return AdShowStart.STARTED
    }

    override fun release() {
        if (mutableState.value == PlatformAdState.SHOWING) return
        loader?.cancelLoading()
        loadedAd?.setAdEventListener(null)
        loadedAd = null
        mutableState.value = PlatformAdState.IDLE
    }

    private val loadListener =
        object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                loadedAd = interstitialAd
                mutableState.value = PlatformAdState.READY
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                loadedAd = null
                mutableState.value = PlatformAdState.UNAVAILABLE
            }
        }

    private val eventListener =
        object : InterstitialAdEventListener {
            override fun onAdShown() {
                activeEvents?.invoke(FullscreenAdEvent.Shown)
            }

            override fun onAdFailedToShow(adError: AdError) {
                activeEvents?.invoke(FullscreenAdEvent.Failed)
                finishShow()
            }

            override fun onAdDismissed() {
                activeEvents?.invoke(FullscreenAdEvent.Dismissed)
                finishShow()
            }

            override fun onAdClicked() = Unit

            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        }

    private fun finishShow() {
        shownAd?.setAdEventListener(null)
        shownAd = null
        activeEvents = null
        mutableState.value = PlatformAdState.IDLE
        gate.release()
    }
}
