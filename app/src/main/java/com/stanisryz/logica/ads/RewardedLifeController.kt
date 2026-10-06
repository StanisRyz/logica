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

/** Rewarded-ad policy for the life and Store gem offers; the platform gateway only loads and presents inventory. */
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

    fun show(
        host: AdDisplayHost,
        kind: RewardedAdKind,
    ) {
        if (state.value != RewardedAdState.READY) return
        ads.show(
            host = host,
            // Allocated after the platform acquired its fullscreen slot but before the SDK call.
            onWillShow = { reward.beginShow(kind) },
            onEvent = { event ->
                when (event) {
                    RewardedAdEvent.Rewarded -> viewModelScope.launch { reward.onRewarded() }
                    RewardedAdEvent.Dismissed, RewardedAdEvent.Failed -> retryUnpersistedReward()
                }
            },
        )
    }

    /** Saves the Daily streak day [epochDay] once the official reward callback arrives, never before. */
    fun showStreakRestore(
        host: AdDisplayHost,
        epochDay: Long,
    ) {
        if (state.value != RewardedAdState.READY) return
        ads.show(
            host = host,
            onWillShow = { reward.beginShow(RewardedAdKind.STREAK_RESTORE, epochDay) },
            onEvent = { event ->
                when (event) {
                    RewardedAdEvent.Rewarded -> viewModelScope.launch { reward.onRewarded() }
                    RewardedAdEvent.Dismissed, RewardedAdEvent.Failed -> retryUnpersistedReward()
                }
            },
        )
    }

    /**
     * The second chance after a third mistake. It pays nothing into the economy: the official
     * reward callback only lets the waiting board go on, at most once for this show.
     */
    fun showContinue(
        host: AdDisplayHost,
        onGranted: () -> Unit,
    ) {
        if (state.value != RewardedAdState.READY) return
        var granted = false
        ads.show(
            host = host,
            onWillShow = {},
            onEvent = { event ->
                if (event == RewardedAdEvent.Rewarded && !granted) {
                    granted = true
                    onGranted()
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
