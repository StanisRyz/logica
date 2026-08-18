package com.stanisryz.logica.platform

internal enum class PlatformKind {
    ANDROID_RUSTORE,
    YANDEX_GAMES,
}

internal data class PlatformCapabilities(
    val purchases: Boolean,
    val playerAuthorization: Boolean,
    val cloudSave: Boolean,
)

internal data class PlatformMetadata(
    val kind: PlatformKind,
    val capabilities: PlatformCapabilities,
)

/** Composition only: policy belongs to the application services that consume these dependencies. */
internal data class PlatformServices(
    val metadata: PlatformMetadata,
    val store: StoreGateway,
    val rewardedAds: RewardedAdsGateway,
    val fullscreenAds: FullscreenAdsGateway,
    val playerIdentity: PlayerIdentityGateway,
    val cloudSave: CloudSaveGateway,
    val lifecycle: PlatformLifecycle,
)
