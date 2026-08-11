package com.stanisryz.logica.ads

import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.FakeEconomyDao
import com.stanisryz.logica.economy.PlayerEconomy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge between the Yandex reward callback and the wallet, without the SDK. What is checked is
 * the invariant the SDK cannot enforce for us: one show yields at most one economy reward, and
 * everything that is not the reward callback yields none.
 */
class RewardedLifeRewardTest {
    @Test
    fun onlyTheRewardCallbackGrantsAndOneShowGrantsAtMostOnce() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))
            val actionIds = FixedActionIds("show-1", "show-2")
            val reward = RewardedLifeReward(FakeEconomyRepository(dao), actionIds)

            // A show the player leaves without earning anything: no callback, no ledger row.
            reward.beginShow()
            assertEquals(0, dao.events.size)
            assertEquals(0, dao.wallet(NOW).lives)

            // The second show earns the reward, and the SDK repeats the callback for that same show.
            reward.beginShow()
            assertTrue(reward.onRewarded())
            assertTrue(reward.onRewarded())

            assertEquals(setOf("rewarded_ad:show-2"), dao.events.keys)
            assertEquals(1, dao.wallet(NOW).lives)
            assertTrue(dao.wallet(NOW).isGameplayAllowed)
            assertNull(reward.unpersistedActionId)
            // The repeated callback reused the show's ID instead of opening a second reward action.
            assertEquals(listOf("show-1", "show-2"), actionIds.issued)
        }

    /**
     * A reward Yandex already confirmed must never cost the player a second ad, so a failed ledger
     * write keeps its original action ID and is retried with it.
     */
    @Test
    fun aFailedGrantIsRetriedWithTheSameActionIdRatherThanAnotherAd() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0, lives = 0, nextLifeAtEpochMillis = NOW + INTERVAL))
            val actionIds = FixedActionIds("show-1")
            val reward = RewardedLifeReward(FakeEconomyRepository(dao, failingGrants = 1), actionIds)

            reward.beginShow()
            assertFalse(reward.onRewarded())

            assertEquals("show-1", reward.unpersistedActionId)
            assertEquals(0, dao.events.size)
            assertEquals(0, dao.wallet(NOW).lives)

            assertTrue(reward.retryUnpersisted())

            assertEquals(setOf("rewarded_ad:show-1"), dao.events.keys)
            assertEquals(1, dao.wallet(NOW).lives)
            assertNull(reward.unpersistedActionId)
            assertEquals(listOf("show-1"), actionIds.issued)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val INTERVAL = EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS
    }
}

/** Hands out predetermined action IDs and records exactly how many were consumed. */
private class FixedActionIds(
    private vararg val ids: String,
) : () -> String {
    val issued = mutableListOf<String>()

    override fun invoke(): String = ids[issued.size].also { issued += it }
}

/** The real DAO transactions behind the repository contract, plus optional transient failures. */
private class FakeEconomyRepository(
    private val dao: FakeEconomyDao,
    private var failingGrants: Int = 0,
) : EconomyRepository {
    override fun observe(): Flow<PlayerEconomy> = flowOf(dao.wallet(NOW))

    override suspend fun refresh(): PlayerEconomy = dao.refresh(NOW)

    override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = dao.refillLifeWithGems(actionId, NOW)

    override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife {
        if (failingGrants > 0) {
            failingGrants--
            error("The economy transaction failed while persisting the rewarded life.")
        }
        return dao.grantRewardedLife(actionId, NOW)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
