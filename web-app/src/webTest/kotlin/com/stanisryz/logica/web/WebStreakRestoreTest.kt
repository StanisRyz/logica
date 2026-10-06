package com.stanisryz.logica.web

import com.stanisryz.logica.platform.AdShowResult
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.ui.profile.DailyCalendarDayState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Saving a Daily streak on Web: the rewards record keeps the day, and the day keeps the streak alive. */
class WebStreakRestoreTest {
    private val today = DailyDate(2026, 10, 6)
    private val todayEpochDay = today.toDailyEpochDay()
    private val yesterday = todayEpochDay - 1L

    @Test
    fun theRecordWritesItsNewestVersionAndStillReadsLgdr2() {
        val snapshot = WebDailyRewardsSnapshot(claimedAchievements = setOf("first"), restoredStreakDays = setOf(20_000L, 20_010L))

        val encoded = WebDailyRewardsCodec.encode(snapshot)

        // LGDR4 since the Daily archive (stage 11.4) added its opened days.
        assertTrue(encoded.decodeToString().startsWith("LGDR4|"))
        assertEquals(snapshot, WebDailyRewardsCodec.decode(encoded))
        // A record written before saved days existed reads with none.
        val v2 = WebDailyRewardsCodec.decode("LGDR2|20000|1|0|0|19999|2||first".encodeToByteArray())
        assertNotNull(v2)
        assertEquals(setOf("first"), v2.claimedAchievements)
        assertEquals(emptySet(), v2.restoredStreakDays)
    }

    @Test
    fun savedDaysMergeByUnion() {
        val a = WebDailyRewardsSnapshot(restoredStreakDays = setOf(10L))
        val b = WebDailyRewardsSnapshot(restoredStreakDays = setOf(30L))

        assertEquals(setOf(10L, 30L), a.mergedWith(b).restoredStreakDays)
        assertEquals(setOf(10L, 30L), b.mergedWith(a).restoredStreakDays)
    }

    @Test
    fun aStreakBrokenYesterdayIsOfferedOnlyFromThePolicyLength() {
        val short = snapshotWithSolvedDays(EconomyPolicy.STREAK_RESTORE_MIN_STREAK - 1)
        val long = snapshotWithSolvedDays(EconomyPolicy.STREAK_RESTORE_MIN_STREAK)

        assertNull(webStreakRestoreOfferOrNull(short, today, emptySet()))
        assertEquals(
            WebStreakRestoreOffer(epochDay = yesterday, streakLength = EconomyPolicy.STREAK_RESTORE_MIN_STREAK),
            webStreakRestoreOfferOrNull(long, today, emptySet()),
        )
        // Once saved, the streak goes on through yesterday and the calendar marks the day.
        assertNull(webStreakRestoreOfferOrNull(long, today, setOf(yesterday)))
        val metrics = long.dailyProfileMetrics(today, setOf(yesterday))
        assertEquals(EconomyPolicy.STREAK_RESTORE_MIN_STREAK + 1L, metrics.currentStreak)
        assertEquals(DailyCalendarDayState.STREAK_SAVED, metrics.calendar?.days?.get(today.getDayOfMonth() - 1))
        // Another save within the cooldown is not offered.
        val recentSave = yesterday - (EconomyPolicy.STREAK_RESTORE_COOLDOWN_DAYS - 1)
        assertNull(webStreakRestoreOfferOrNull(long, today, setOf(recentSave)))
    }

    @Test
    fun aDayIsSavedOnceAndChargedOnce() {
        val progress = progressRepository()
        val economy = economyWith(gems = 40)

        fun restoreWithGems() {
            if (progress.claimStreakRestore(yesterday)) economy.spendGems(EconomyPolicy.STREAK_RESTORE_GEMS)
        }
        restoreWithGems()
        restoreWithGems()

        assertEquals(40 - EconomyPolicy.STREAK_RESTORE_GEMS, economy.state.value.gems)
        assertEquals(setOf(yesterday), progress.rewards.value.restoredStreakDays)
    }

    @Test
    fun aLostBrowserWriteSavesNothing() {
        val progress =
            progressRepository(
                object : WebDailyRewardsStore {
                    override fun load() = WebDailyRewardsSnapshot.EMPTY

                    override fun save(snapshot: WebDailyRewardsSnapshot): Unit = error("quota")
                },
            )

        assertFalse(progress.claimStreakRestore(yesterday))
        assertEquals(emptySet(), progress.rewards.value.restoredStreakDays)
    }

    @Test
    fun anAdFinishedAfterAPlayerSwitchSavesNothing() {
        val progress = progressRepository()
        val provider = ScriptedRewardedProvider()
        var context: WebPlayerContextToken? = WebPlayerContextToken(1L)
        val ad = streakRestoreAd(provider) { context }

        ad.requestReward { progress.claimStreakRestore(yesterday) }
        provider.onOpened?.invoke()
        context = WebPlayerContextToken(2L)
        provider.onResult?.invoke(AdShowResult.Completed)

        assertEquals(emptySet(), progress.rewards.value.restoredStreakDays)

        // The same Player watching to the end saves the day.
        ad.requestReward { progress.claimStreakRestore(yesterday) }
        provider.onResult?.invoke(AdShowResult.Completed)
        assertEquals(setOf(yesterday), progress.rewards.value.restoredStreakDays)
    }

    private fun snapshotWithSolvedDays(count: Int): WebDailySnapshotV1 {
        // Solved days end the day before yesterday; yesterday was missed.
        val days =
            (2..count + 1).associate { back ->
                val date = DailyDate(2026, 10, 6 - back)
                date to WebDailyDayRecord(date = date, policyVersion = DailyPolicyVersion(8), solvedMask = 1)
            }
        return WebDailySnapshotV1(days = days)
    }

    private fun progressRepository(rewardsStore: WebDailyRewardsStore = WebDailyRewardsStore.InMemory()): WebCatalogProgressRepository =
        WebCatalogProgressRepository(
            WebCatalogProgressScope.STANDALONE,
            object : WebCatalogProgressStore {
                override fun load() = WebCatalogProgressSnapshot.EMPTY

                override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
            },
            rewardsStore = rewardsStore,
        ).also { it.loadLocal() }

    private fun economyWith(gems: Int): WebPlayerEconomyRepository =
        WebPlayerEconomyRepository(
            WebCatalogProgressScope.STANDALONE,
            object : WebEconomyStore {
                private var snapshot = WebEconomySnapshot.DEFAULT.copy(gems = gems)

                override fun load() = snapshot

                override fun save(snapshot: WebEconomySnapshot) {
                    this.snapshot = snapshot
                }
            },
            WebPlayerStateRevisions(),
        ).also { it.loadLocal() }

    private class ScriptedRewardedProvider : RewardedAdProvider {
        var onOpened: (() -> Unit)? = null
        var onResult: ((AdShowResult) -> Unit)? = null

        override fun show(
            onOpened: () -> Unit,
            onResult: (AdShowResult) -> Unit,
        ) {
            this.onOpened = onOpened
            this.onResult = onResult
        }
    }

    private fun streakRestoreAd(
        provider: ScriptedRewardedProvider,
        context: () -> WebPlayerContextToken?,
    ): WebRewardedPlacementController =
        WebRewardedPlacementController(
            reward = null,
            provider = provider,
            // No cooldown, so the second request in the test is shown at once.
            policy = WebAdPolicy(rewardedCooldownMs = 0L),
            rewardService = WebRewardService(economyRepository = { null }, storeRepository = { null }),
            analytics = WebMonetizationAnalytics(),
            fullscreenAdActivity = {},
            currentPlayerContext = context,
            currentTimeMs = { 1_000L },
        )
}
