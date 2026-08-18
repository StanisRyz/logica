package com.stanisryz.logica.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.platform.AdDisplayHost
import com.stanisryz.logica.platform.PlatformAdState
import com.stanisryz.logica.platform.RewardedAdEvent
import com.stanisryz.logica.platform.RewardedAdsGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal typealias RewardedAdState = PlatformAdState

/** Rewarded-life policy; the platform gateway only loads and presents inventory. */
internal class RewardedLifeController(
    private val ads: RewardedAdsGateway,
    private val reward: RewardedLifeReward,
) : ViewModel() {
    val state: StateFlow<RewardedAdState> = ads.state

    private var rewardRetryJob: Job? = null

    fun preload() {
        retryUnpersistedReward()
        if (state.value != RewardedAdState.IDLE) return
        viewModelScope.launch { ads.preload() }
    }

    fun retry() {
        if (state.value != RewardedAdState.UNAVAILABLE) return
        ads.release()
        preload()
    }

    fun show(host: AdDisplayHost) {
        if (state.value != RewardedAdState.READY) return
        ads.show(
            host = host,
            // Allocated after the platform acquired its fullscreen slot but before the SDK call.
            onWillShow = reward::beginShow,
            onEvent = { event ->
                when (event) {
                    RewardedAdEvent.Rewarded -> viewModelScope.launch { reward.onRewarded() }
                    RewardedAdEvent.Dismissed, RewardedAdEvent.Failed -> retryUnpersistedReward()
                }
            },
        )
    }

    fun release() = ads.release()

    override fun onCleared() {
        ads.release()
    }

    private fun retryUnpersistedReward() {
        if (reward.unpersistedActionId == null || rewardRetryJob?.isActive == true) return
        rewardRetryJob = viewModelScope.launch { reward.retryUnpersisted() }
    }
}

internal class RewardedLifeControllerFactory(
    private val ads: RewardedAdsGateway,
    private val economyRepository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RewardedLifeController::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return RewardedLifeController(ads, RewardedLifeReward(economyRepository)) as T
    }
}
