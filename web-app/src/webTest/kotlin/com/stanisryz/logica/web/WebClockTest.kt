package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 2.2: one server-corrected Web time, and reward days that only move forward. */
class WebClockTest {
    private val serverNow = 1_790_000_000_000L // a fixed server instant
    private val hour = 60L * 60L * 1000L

    @Test
    fun theServerOffsetIsApplied() {
        var device = serverNow + 3 * hour
        val clock = WebClock { device }
        assertEquals(device, clock.now()) // before synchronizing: the device clock

        clock.synchronize(serverNow)
        assertEquals(serverNow, clock.now())
        assertEquals(-3 * hour, clock.offsetMs)
        device += 1_000L
        assertEquals(serverNow + 1_000L, clock.now())
    }

    @Test
    fun withoutAServerTimeTheOffsetIsZero() {
        val device = serverNow + 3 * hour
        val clock = WebClock { device }
        clock.synchronize(null)
        assertEquals(0L, clock.offsetMs)
        assertEquals(device, clock.now())
        clock.synchronize(0L)
        assertEquals(0L, clock.offsetMs)
    }

    @Test
    fun aDeviceClockMovedForwardNeitherRestoresLivesNorOpensANewGift() {
        // The device clock is 3 hours (and so possibly a day) ahead; the server is not.
        val device = serverNow + 3 * hour
        val clock = WebClock { device }.also { it.synchronize(serverNow) }
        val wallet =
            WebEconomySnapshot.DEFAULT.copy(lives = 2, nextLifeRestoreAtEpochMs = serverNow + EconomyPolicy.LIFE_RESTORE_INTERVAL_MS / 2)
        val economy = WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, MemoryEconomyStore(wallet), currentTimeMs = clock::now)
        economy.loadLocal()
        economy.refresh()
        assertEquals(2, economy.state.value.lives)

        val progress = rewardsRepository()
        val today = clock.currentDate().toDailyEpochDay()
        assertNotNull(progress.claimLoginGift(today))
        // Whatever day the device shows, the corrected clock still says the same day: no second gift.
        assertNull(progress.claimLoginGift(clock.currentDate().toDailyEpochDay()))
    }

    @Test
    fun switchingTheDayBackAndForthNeverPaysAGiftOrAQuestTwice() {
        val progress = rewardsRepository()
        val day = 20_000L
        assertNotNull(progress.claimLoginGift(day + 1))
        assertTrue(progress.claimQuest(day + 1, 0))

        // Back one day: nothing is claimable and nothing is reset.
        assertNull(progress.claimLoginGift(day))
        assertFalse(progress.claimQuest(day, 1))
        assertEquals((0 until Int.SIZE_BITS).toSet(), progress.rewards.value.claimedQuests(day))
        progress.recordQuestActivity(day, PuzzleType.BALANCE, Difficulty.EASY, solved = true)
        assertEquals(0, progress.rewards.value.played)

        // Forward again: the same day keeps its claims.
        assertNull(progress.claimLoginGift(day + 1))
        assertFalse(progress.claimQuest(day + 1, 0))
        assertEquals(setOf(0), progress.rewards.value.claimedQuests(day + 1))

        // The real next day works as before.
        assertNotNull(progress.claimLoginGift(day + 2))
        assertTrue(
            progress.rewards.value
                .claimedQuests(day + 2)
                .isEmpty(),
        )
        assertTrue(progress.claimQuest(day + 2, 0))
    }

    private fun rewardsRepository(): WebCatalogProgressRepository =
        WebCatalogProgressRepository(
            WebCatalogProgressScope.STANDALONE,
            object : WebCatalogProgressStore {
                override fun load() = WebCatalogProgressSnapshot.EMPTY

                override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
            },
        ).also { it.loadLocal() }
}
