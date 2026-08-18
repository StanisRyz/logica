package com.stanisryz.logica.platform

enum class PlatformKind {
    ANDROID_RUSTORE,
    YANDEX_GAMES,
}

data class PlatformCapabilities(
    val purchases: Boolean,
    val playerAuthorization: Boolean,
    val cloudSave: Boolean,
)

data class PlatformMetadata(
    val kind: PlatformKind,
    val capabilities: PlatformCapabilities,
)

/** Composition only: policy belongs to the application services that consume these dependencies. */
data class PlatformServices(
    val metadata: PlatformMetadata,
    val store: StoreGateway,
    val rewardedAds: RewardedAdsGateway,
    val fullscreenAds: FullscreenAdsGateway,
    val playerIdentity: PlayerIdentityGateway,
    val cloudSave: CloudSaveGateway,
    val lifecycle: PlatformLifecycle,
)
