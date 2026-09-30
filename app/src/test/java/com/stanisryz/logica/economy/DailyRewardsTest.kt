package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.quest.DailyQuestKind
import com.stanisryz.logica.puzzle.core.quest.DailyQuests
import com.stanisryz.logica.puzzle.core.quest.LoginGift
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.GameResultEntity
import com.stanisryz.logica.ui.profile.Achievement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Daily rewards are derived from durable results and pay through the ledger exactly once. */
class DailyRewardsTest {
    @Test
    fun theLoginGiftPaysOncePerDayAndWalksItsCycle() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0))
            var now = DAY * MILLIS_PER_DAY + 1_000L
            val repository = DailyRewardsRepository(FakeResults(emptyList()), dao, { now }, { ZoneOffset.UTC })

            assertTrue(repository.claimLoginGift(DAY))
            assertFalse(repository.claimLoginGift(DAY))
            assertEquals(LoginGift.gemsFor(1), dao.wallet(now).gems)

            now += MILLIS_PER_DAY
            assertTrue(repository.claimLoginGift(DAY + 1))
            assertEquals(LoginGift.gemsFor(1) + LoginGift.gemsFor(2), dao.wallet(now).gems)
            assertEquals(2, repository.observe(DAY + 1).first().giftStreakDay)

            // A missed day starts the cycle over.
            now += 2 * MILLIS_PER_DAY
            assertEquals(1, repository.observe(DAY + 3).first().giftStreakDay)
        }

    @Test
    fun aQuestPaysOnlyOnceItsDurableResultsCompleteIt() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0))
            val now = DAY * MILLIS_PER_DAY + 1_000L
            val quest = DailyQuests.forDay(DAY).first { it.kind == DailyQuestKind.PLAY || it.kind == DailyQuestKind.SOLVE }
            val results = FakeResults(emptyList())
            val repository = DailyRewardsRepository(results, dao, { now }, { ZoneOffset.UTC })

            assertFalse(repository.claimQuest(DAY, quest.index))
            results.rows = List(quest.target) { solved("r$it", now) }
            assertTrue(
                repository
                    .observe(DAY)
                    .first()
                    .quests[quest.index]
                    .complete,
            )
            assertTrue(repository.claimQuest(DAY, quest.index))
            assertFalse(repository.claimQuest(DAY, quest.index))
            assertEquals(quest.gems, dao.wallet(now).gems)
            assertTrue(
                repository
                    .observe(DAY)
                    .first()
                    .quests[quest.index]
                    .claimed,
            )
        }

    @Test
    fun anAchievementRewardPaysOnce() =
        runBlocking {
            val dao = FakeEconomyDao(PlayerEconomy(gems = 0))
            val repository = DailyRewardsRepository(FakeResults(emptyList()), dao, { 1_000L }, { ZoneOffset.UTC })

            assertTrue(repository.claimAchievement(Achievement.FIRST_SOLVE))
            assertFalse(repository.claimAchievement(Achievement.FIRST_SOLVE))
            assertEquals(Achievement.FIRST_SOLVE.gems, dao.wallet(1_000L).gems)
            assertEquals(setOf(Achievement.FIRST_SOLVE.id), repository.observeClaimedAchievements().first())
        }

    private fun solved(
        id: String,
        at: Long,
    ) = GameResultEntity(
        resultId = id,
        puzzleType = "SUDOKU",
        difficulty = "EASY",
        puzzleSeed = 1L,
        generatorVersion = 1,
        resultScope = "CATALOG",
        hintsUsed = 0,
        completedAtEpochMillis = at,
        outcome = "SOLVED",
        attemptsUsed = null,
        challengeDate = null,
        dailyPolicyVersion = null,
    )

    private class FakeResults(
        var rows: List<GameResultEntity>,
    ) : GameResultDao {
        override fun observeAll(): Flow<List<GameResultEntity>> = flowOf(rows)

        override fun observeCompletedBetween(
            fromEpochMillis: Long,
            untilEpochMillis: Long,
        ): Flow<List<GameResultEntity>> = flowOf(rows.filter { it.completedAtEpochMillis in fromEpochMillis until untilEpochMillis })

        override suspend fun find(resultId: String): GameResultEntity? = rows.firstOrNull { it.resultId == resultId }

        override suspend fun findDailyResults(
            challengeDate: String,
            dailyPolicyVersion: Int,
        ): List<GameResultEntity> = emptyList()
    }

    private companion object {
        const val DAY = 20_000L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
