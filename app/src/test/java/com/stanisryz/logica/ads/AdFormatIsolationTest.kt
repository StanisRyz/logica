package com.stanisryz.logica.ads

import com.stanisryz.logica.economy.EconomyEvent
import com.stanisryz.logica.economy.EconomyEventType
import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.FakeEconomyDao
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.toEntity
import com.stanisryz.logica.result.toGameResultOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two Yandex formats are separate products and must stay that way: rewarded pays exactly one
 * life for an ad the player asked for, the interstitial pays nothing at all, neither one's state
 * leaks into the other's, and they never try to occupy the screen at the same time.
 */
class AdFormatIsolationTest {
    private val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))

    @Test
    fun `an interstitial changes no gems, no lives, and no ledger row`() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition, PlayerEconomy(gems = 0, lives = 3, nextLifeAtEpochMillis = NOW + INTERVAL))
            val store = FakeInterstitialCooldownStore()
            val clock = FakeInterstitialClock()
            val cooldown = InterstitialCooldownPolicy(store, clock)
            val opportunities = InterstitialOpportunities()
            val coordinator = InterstitialAdCoordinator(cooldown)
            val ad = FakeInterstitialSlot().apply { ready = true }
            val repository = InterstitialAwareGameCompletionRepository(CompletionRepository(dao), opportunities)

            val completion = dao.catalogCompletion(PuzzleType.CROWNS, difficulty = Difficulty.MEDIUM)
            repository.complete(completion)

            // The wallet after the completion transaction and before any advertising.
            val afterCompletion = dao.wallet(NOW)
            assertEquals(EconomyRules.solvedGemReward(Difficulty.MEDIUM), afterCompletion.gems)
            assertEquals(setOf(EconomyEvent.resultEventId(completion.resultId)), dao.economyEvents.keys)

            val opportunity = requireNotNull(opportunities.pending.value)
            assertTrue(coordinator.attemptShow(opportunity, isReady = { ad.ready }, show = { ad.show() }))
            cooldown.recordShown()

            // The ad happened, and the economy is byte-for-byte what it was before it.
            assertEquals(1, ad.shows)
            assertEquals(afterCompletion, dao.wallet(NOW))
            assertEquals(1, dao.economyEvents.size)
            assertEquals(
                EconomyEventType.SOLVED_REWARD.name,
                dao.economyEvents.values
                    .first()
                    .eventType,
            )
            assertEquals(1, dao.results.size)
        }

    @Test
    fun `rewarded grants one life only from its reward callback and never touches the cooldown`() =
        runBlocking {
            val economyDao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))
            val store = FakeInterstitialCooldownStore()
            val cooldown = InterstitialCooldownPolicy(store, FakeInterstitialClock())
            val reward = RewardedLifeReward(RewardedEconomy(economyDao)) { REWARD_ACTION }

            // A rewarded show the player watched to the end.
            reward.beginShow()
            assertTrue(reward.onRewarded())

            assertEquals(1, economyDao.wallet(NOW).lives)
            assertEquals(setOf("rewarded_ad:$REWARD_ACTION"), economyDao.events.keys)
            // Watching a rewarded ad is not an interstitial: the five-minute interval never started.
            assertEquals(0, store.writes)
            assertTrue(cooldown.isEligible())

            // And an interstitial grants nothing back: no reward callback, so no ledger row at all.
            val ad = FakeInterstitialSlot().apply { ready = true }
            val coordinator = InterstitialAdCoordinator(cooldown)
            assertTrue(
                coordinator.attemptShow(
                    InterstitialOpportunity("result-1"),
                    isReady = { ad.ready },
                    show = { ad.show() },
                ),
            )
            cooldown.recordShown()

            assertEquals(1, economyDao.events.size)
            assertEquals(1, economyDao.wallet(NOW).lives)
            // The interstitial started its own interval without changing rewarded availability.
            assertEquals(1, store.writes)
            assertFalse(cooldown.isEligible())
            assertNull(reward.unpersistedActionId)
        }

    @Test
    fun `the fullscreen gate lets exactly one format present at a time`() {
        val gate = FullscreenAdGate()

        assertTrue("The first fullscreen ad owns the screen", gate.tryAcquire())
        assertFalse("The other format must not call show concurrently", gate.tryAcquire())

        // Dismissal, or a show that failed before appearing, hands the screen back.
        gate.release()

        assertTrue(gate.tryAcquire())
        gate.release()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val REWARD_ACTION = "rewarded-show-1"
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}

/** The real completion transaction of the Room DAO, behind the repository contract. */
private class CompletionRepository(
    private val dao: FakeGameCompletionDao,
) : GameCompletionRepository {
    override suspend fun complete(completion: GameCompletion): GameResult =
        requireNotNull(dao.complete(completion.toEntity(NOW)).toGameResultOrNull())

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

/** The real wallet transactions behind the economy contract the rewarded placement writes through. */
private class RewardedEconomy(
    private val dao: FakeEconomyDao,
) : EconomyRepository {
    override fun observe(): Flow<PlayerEconomy> = flowOf(dao.wallet(NOW))

    override suspend fun refresh(): PlayerEconomy = dao.refresh(NOW)

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = dao.refillLifeWithGems(actionId, NOW)

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = dao.grantRewardedLife(actionId, NOW)

    override suspend fun grantPurchasedGems(
        purchaseId: String,
        productId: String,
    ): EconomyGemPurchase = dao.grantPurchasedGems(purchaseId, productId, NOW)

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
