package com.stanisryz.logica.platform

import kotlinx.coroutines.flow.StateFlow

/** SDK-level ad state. Product policy decides where and when a gateway may enter these states. */
enum class PlatformAdState {
    IDLE,
    LOADING,
    READY,
    SHOWING,
    UNAVAILABLE,
}

/** Opaque presentation surface supplied by the platform UI host. */
interface AdDisplayHost

enum class AdShowStart {
    STARTED,
    UNAVAILABLE,
    FAILED,
}

sealed interface RewardedAdEvent {
    data object Rewarded : RewardedAdEvent

    data object Dismissed : RewardedAdEvent

    data object Failed : RewardedAdEvent
}

sealed interface FullscreenAdEvent {
    data object Shown : FullscreenAdEvent

    data object Dismissed : FullscreenAdEvent

    data object Failed : FullscreenAdEvent
}

/** One rewarded placement. Reward persistence and rewarded-life policy stay above this boundary. */
interface RewardedAdsGateway {
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
interface FullscreenAdsGateway {
    val state: StateFlow<PlatformAdState>

    suspend fun preload()

    fun show(
        host: AdDisplayHost,
        onEvent: (FullscreenAdEvent) -> Unit,
    ): AdShowStart

    fun release()
}
