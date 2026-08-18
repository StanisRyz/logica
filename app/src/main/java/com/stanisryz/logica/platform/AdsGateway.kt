package com.stanisryz.logica.platform

import kotlinx.coroutines.flow.StateFlow

/** SDK-level ad state. Product policy decides where and when a gateway may enter these states. */
internal enum class PlatformAdState {
    IDLE,
    LOADING,
    READY,
    SHOWING,
    UNAVAILABLE,
}

/** Opaque presentation surface supplied by the platform UI host. */
internal interface AdDisplayHost

internal enum class AdShowStart {
    STARTED,
    UNAVAILABLE,
    FAILED,
}

internal sealed interface RewardedAdEvent {
    data object Rewarded : RewardedAdEvent

    data object Dismissed : RewardedAdEvent

    data object Failed : RewardedAdEvent
}

internal sealed interface FullscreenAdEvent {
    data object Shown : FullscreenAdEvent

    data object Dismissed : FullscreenAdEvent

    data object Failed : FullscreenAdEvent
}

/** One rewarded placement. Reward persistence and rewarded-life policy stay above this boundary. */
internal interface RewardedAdsGateway {
    val state: StateFlow<PlatformAdState>

    suspend fun preload()

    fun show(
        host: AdDisplayHost,
        onWillShow: () -> Unit,
        onEvent: (RewardedAdEvent) -> Unit,
    ): AdShowStart

    fun release()
}

/** One fullscreen placement. Opportunities, cooldown, and terminal actions stay above this boundary. */
internal interface FullscreenAdsGateway {
    val state: StateFlow<PlatformAdState>

    suspend fun preload()

    fun show(
        host: AdDisplayHost,
        onEvent: (FullscreenAdEvent) -> Unit,
    ): AdShowStart

    fun release()
}
