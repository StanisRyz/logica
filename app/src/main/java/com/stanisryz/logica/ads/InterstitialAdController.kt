package com.stanisryz.logica.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.platform.AdDisplayHost
import com.stanisryz.logica.platform.AdShowStart
import com.stanisryz.logica.platform.FullscreenAdEvent
import com.stanisryz.logica.platform.FullscreenAdsGateway
import com.stanisryz.logica.platform.PlatformAdState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal typealias InterstitialAdState = PlatformAdState

/** Interstitial policy; platform code does not own opportunities, cooldown, or terminal actions. */
internal class InterstitialAdController(
    private val ads: FullscreenAdsGateway,
    private val opportunities: InterstitialOpportunities,
    private val cooldown: InterstitialCooldownPolicy,
) : ViewModel() {
    private val coordinator = InterstitialAdCoordinator(cooldown)

    val state: StateFlow<InterstitialAdState> = ads.state
    val pendingOpportunity: StateFlow<InterstitialOpportunity?> = opportunities.pending

    private var gameplayPreloadJob: Job? = null
    private var pendingTerminalAction: (() -> Unit)? = null

    fun onGameplayStarted() {
        if (gameplayPreloadJob?.isActive == true) return
        gameplayPreloadJob =
            viewModelScope.launch {
                val remaining = cooldown.remainingMillis()
                if (remaining > 0L) delay(remaining)
                ads.preload()
            }
    }

    fun onGameplayStopped() {
        gameplayPreloadJob?.cancel()
        gameplayPreloadJob = null
    }

    fun showForTerminalAction(
        opportunity: InterstitialOpportunity,
        host: AdDisplayHost?,
        onFinished: () -> Unit,
    ) {
        opportunities.consume(opportunity)
        viewModelScope.launch {
            pendingTerminalAction = onFinished
            val shown =
                coordinator.attemptShow(
                    opportunity = opportunity,
                    isReady = { state.value == InterstitialAdState.READY },
                    show = { host != null && show(host) },
                )
            if (!shown) continueTerminalAction()
        }
    }

    private fun show(host: AdDisplayHost): Boolean =
        ads.show(
            host = host,
            onEvent = { event ->
                when (event) {
                    FullscreenAdEvent.Shown -> viewModelScope.launch { cooldown.recordShown() }
                    FullscreenAdEvent.Dismissed, FullscreenAdEvent.Failed -> continueTerminalAction()
                }
            },
        ) == AdShowStart.STARTED

    override fun onCleared() {
        gameplayPreloadJob?.cancel()
        gameplayPreloadJob = null
        pendingTerminalAction = null
        ads.release()
    }

    private fun continueTerminalAction() {
        val action = pendingTerminalAction ?: return
        pendingTerminalAction = null
        action()
    }
}

internal class InterstitialAdControllerFactory(
    private val ads: FullscreenAdsGateway,
    private val opportunities: InterstitialOpportunities,
    private val cooldown: InterstitialCooldownPolicy,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InterstitialAdController::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return InterstitialAdController(ads, opportunities, cooldown) as T
    }
}
