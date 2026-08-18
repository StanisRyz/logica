package com.stanisryz.logica.platform.android

import android.app.Application
import android.content.Intent
import com.stanisryz.logica.BuildConfig
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.platform.PlatformCapabilities
import com.stanisryz.logica.platform.PlatformKind
import com.stanisryz.logica.platform.PlatformMetadata
import com.stanisryz.logica.platform.PlatformServices
import com.stanisryz.logica.store.GemPackProductMapping
import com.stanisryz.logica.store.createRuStorePayGateway
import com.stanisryz.logica.store.proceedRuStorePayIntent
import com.stanisryz.logica.store.ruStoreSdkTheme

/** The Android composition root for platform-dependent services and configuration. */
internal class AndroidPlatformComposition(
    private val application: Application,
) {
    private val purchasesConfigured = BuildConfig.RUSTORE_CONSOLE_APP_ID.isNotBlank()
    private val ruStore =
        createRuStorePayGateway(
            consoleApplicationId = BuildConfig.RUSTORE_CONSOLE_APP_ID,
            sdkTheme = { application.ruStoreSdkTheme() },
        )

    val gemPackProducts =
        GemPackProductMapping(
            mapOf(
                GemPack.GEMS_50 to "gems_50",
                GemPack.GEMS_250 to "gems_250",
                GemPack.GEMS_600 to "gems_600",
            ),
        )

    val services =
        PlatformServices(
            metadata =
                PlatformMetadata(
                    kind = PlatformKind.ANDROID_RUSTORE,
                    capabilities =
                        PlatformCapabilities(
                            purchases = purchasesConfigured,
                            playerAuthorization = false,
                            cloudSave = false,
                        ),
                ),
            store = AndroidRuStoreAdapter(ruStore),
            rewardedAds = AndroidYandexRewardedAdsGateway(application, BuildConfig.REWARDED_AD_UNIT_ID),
            fullscreenAds = AndroidYandexFullscreenAdsGateway(application, BuildConfig.INTERSTITIAL_AD_UNIT_ID),
            playerIdentity = AndroidLocalPlayerIdentityGateway,
            cloudSave = UnsupportedAndroidCloudSaveGateway,
            lifecycle = AndroidPlatformLifecycle(application),
        )

    /** Android-only deeplink hand-off; ordinary launches and unconfigured builds never touch Pay. */
    fun proceedPaymentReturn(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW || intent.data == null || !purchasesConfigured) return
        application.proceedRuStorePayIntent(intent)
    }
}
