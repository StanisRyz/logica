package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdKind
import com.stanisryz.logica.platform.AdRewardDefinition
import com.stanisryz.logica.platform.AdShowResult
import com.stanisryz.logica.platform.MonetizationAnalyticsEvent
import com.stanisryz.logica.platform.StoreRewardType

/** Lightweight internal monetization analytics; no external SDK is attached in this stage. */
internal class WebMonetizationAnalytics {
    private val recorded = ArrayDeque<Pair<Long, MonetizationAnalyticsEvent>>()

    /** Most recent events first, bounded; future sinks can forward these without reshaping callers. */
    val recentEvents: List<Pair<Long, MonetizationAnalyticsEvent>>
        get() = recorded.toList()

    fun record(
        timestampMs: Long,
        event: MonetizationAnalyticsEvent,
    ) {
        recorded.addFirst(timestampMs to event)
        while (recorded.size > ANALYTICS_LIMIT) {
            recorded.removeLast()
        }
    }

    private companion object {
        const val ANALYTICS_LIMIT = 100
    }
}

/**
 * Minimal ad policy: one cooldown per [AdKind], evaluated against a host-injected clock, so an
 * advertisement can never be shown twice immediately and no wall-clock API enters this layer.
 */
internal class WebAdPolicy(
    private val rewardedCooldownMs: Long = DEFAULT_REWARDED_COOLDOWN_MS,
    private val interstitialCooldownMs: Long = DEFAULT_INTERSTITIAL_COOLDOWN_MS,
) {
    private val lastShownAt = mutableMapOf<AdKind, Long>()

    fun canShow(
        kind: AdKind,
        nowMs: Long,
    ): Boolean =
        when (val shownAt = lastShownAt[kind]) {
            null -> true
            else -> nowMs - shownAt >= cooldownFor(kind)
        }

    fun markShown(
        kind: AdKind,
        nowMs: Long,
    ) {
        lastShownAt[kind] = nowMs
    }

    fun msUntilNextShow(
        kind: AdKind,
        nowMs: Long,
    ): Long =
        when (val shownAt = lastShownAt[kind]) {
            null -> 0L
            else -> maxOf(0L, cooldownFor(kind) - (nowMs - shownAt))
        }

    private fun cooldownFor(kind: AdKind): Long =
        when (kind) {
            AdKind.REWARDED -> rewardedCooldownMs
            AdKind.INTERSTITIAL -> interstitialCooldownMs
        }

    companion object {
        const val DEFAULT_REWARDED_COOLDOWN_MS = 30_000L
        const val DEFAULT_INTERSTITIAL_COOLDOWN_MS = 90_000L
    }
}

/**
 * Applies completed advertisement rewards through the existing systems only: hints go to Store
 * inventory, life restores go to the Economy wallet. The service knows nothing about UI or any
 * individual puzzle, and it never touches Daily state.
 */
internal class WebRewardService(
    private val economyRepository: () -> WebPlayerEconomyRepository?,
    private val storeRepository: () -> WebPlayerStoreRepository?,
) {
    fun apply(reward: AdRewardDefinition): Boolean =
        when (reward.rewardType) {
            StoreRewardType.HINTS -> {
                val store = storeRepository() ?: return false
                store.grantInventory(STORE_INVENTORY_HINTS, reward.amount)
            }
            StoreRewardType.LIFE_RESTORE -> {
                val economy = economyRepository() ?: return false
                economy.restoreLives(reward.amount)
            }
        }
}

/**
 * Provider boundary for one rewarded invocation. [onOpened] fires only when the platform actually
 * starts rendering the advertisement (never for unavailable/failed-to-start requests), so callers
 * can begin anti-spam cooldowns from real exposure. Terminal results are exactly-once.
 */
internal fun interface RewardedAdProvider {
    fun show(
        onOpened: () -> Unit,
        onResult: (AdShowResult) -> Unit,
    )
}

/** Same contract as [RewardedAdProvider] for interstitial invocations. */
internal fun interface InterstitialAdProvider {
    fun show(
        onOpened: () -> Unit,
        onResult: (AdShowResult) -> Unit,
    )
}

/** Yandex `ysdk.adv.showRewardedVideo` behind the provider abstraction. */
internal class YandexRewardedAdProvider(
    private val bridge: YandexGamesBridge,
) : RewardedAdProvider {
    override fun show(
        onOpened: () -> Unit,
        onResult: (AdShowResult) -> Unit,
    ) {
        // All mutable state belongs to THIS invocation; two show() calls never share rewards.
        var rewarded = false
        var finished = false

        fun finish(result: AdShowResult) {
            if (finished) return // onError-then-onClose / repeated terminal callbacks: first wins
            finished = true
            onResult(result)
        }

        val started =
            bridge.showRewardedVideo(
                onOpen = { onOpened() },
                onRewarded = { rewarded = true },
                onClose = { finish(if (rewarded) AdShowResult.Completed else AdShowResult.Dismissed) },
                onError = { detail -> finish(AdShowResult.Failed(detail)) },
            )
        if (!started) finish(AdShowResult.Unavailable)
    }
}

/** Yandex `ysdk.adv.showFullscreenAdv` behind the provider abstraction. */
internal class YandexInterstitialAdProvider(
    private val bridge: YandexGamesBridge,
) : InterstitialAdProvider {
    override fun show(
        onOpened: () -> Unit,
        onResult: (AdShowResult) -> Unit,
    ) {
        var shown = false
        var finished = false

        fun finish(result: AdShowResult) {
            if (finished) return
            finished = true
            onResult(result)
        }

        val started =
            bridge.showFullscreenAdv(
                onOpen = {
                    shown = true
                    onOpened()
                },
                onClose = { wasShown -> finish(if (wasShown || shown) AdShowResult.Completed else AdShowResult.Dismissed) },
                onError = { detail -> finish(AdShowResult.Failed(detail)) },
            )
        if (!started) finish(AdShowResult.Unavailable)
    }
}

/**
 * Ties one rewarded placement together: policy gate -> provider -> reward application, with
 * analytics recorded at each transition. Rewards are applied only for Completed results and only
 * through [WebRewardService]; failures never modify Economy or inventory.
 */
internal class WebRewardedAdController(
    private val provider: RewardedAdProvider,
    private val policy: WebAdPolicy,
    private val rewardService: WebRewardService,
    private val analytics: WebMonetizationAnalytics,
    private val currentTimeMs: () -> Long,
) {
    fun requestReward(
        reward: AdRewardDefinition,
        onFinished: (granted: Boolean) -> Unit,
    ) {
        val now = currentTimeMs()
        if (!policy.canShow(AdKind.REWARDED, now)) {
            analytics.record(now, MonetizationAnalyticsEvent.AD_FAILED)
            onFinished(false)
            return
        }
        policy.markShown(AdKind.REWARDED, now)
        analytics.record(now, MonetizationAnalyticsEvent.AD_STARTED)
        provider.show(
            onOpened = {},
            onResult = { result ->
                when (result) {
                    AdShowResult.Completed -> {
                        analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_COMPLETED)
                        val granted = rewardService.apply(reward)
                        if (granted) {
                            analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.REWARD_GRANTED)
                        }
                        onFinished(granted)
                    }
                    else -> {
                        analytics.record(currentTimeMs(), MonetizationAnalyticsEvent.AD_FAILED)
                        onFinished(false)
                    }
                }
            },
        )
    }
}
