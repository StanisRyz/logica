package com.stanisryz.logica.puzzle.core

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.quest.DailyQuestActivity
import com.stanisryz.logica.puzzle.core.quest.DailyQuestKind
import com.stanisryz.logica.puzzle.core.quest.DailyQuests
import com.stanisryz.logica.puzzle.core.quest.LoginGift
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyRewardsTest {
    @Test
    fun everyDayHasThreeStableQuestsAndTheSlotsVary() {
        val days = (20_000L until 20_120L)
        days.forEach { day ->
            val quests = DailyQuests.forDay(day)
            assertEquals(quests, DailyQuests.forDay(day))
            assertEquals(listOf(0, 1, 2), quests.map { it.index })
            assertTrue(quests[0].kind in setOf(DailyQuestKind.PLAY, DailyQuestKind.SOLVE))
        }
        val secondKinds = days.map { DailyQuests.forDay(it)[1].puzzleType ?: DailyQuests.forDay(it)[1].kind }.toSet()
        assertEquals(7, secondKinds.size)
        assertEquals(3, days.map { DailyQuests.forDay(it)[2].kind to DailyQuests.forDay(it)[2].target }.toSet().size)
    }

    @Test
    fun activityCountsOnlyWhatEachQuestAsks() {
        val activity =
            DailyQuestActivity()
                .plus(PuzzleType.SUDOKU, Difficulty.HARD, solved = true, daily = false)
                .plus(PuzzleType.WORD, Difficulty.MEDIUM, solved = true, daily = true)
                .plus(PuzzleType.WORD, Difficulty.EASY, solved = false, daily = false)
        assertEquals(3, activity.played)
        assertEquals(2, activity.solved)
        assertEquals(1, activity.solvedHard)
        assertEquals(1, activity.dailySolved)
        val different = DailyQuests.forDay(0).first().copy(kind = DailyQuestKind.DIFFERENT_GAMES, target = 3)
        assertEquals(2, different.progress(activity))
    }

    @Test
    fun theLoginGiftWalksASevenDayCycleAndAMissedDayStartsOver() {
        assertEquals(1, LoginGift.streakDay(null, 0, 100))
        assertEquals(4, LoginGift.streakDay(99, 3, 100))
        assertEquals(3, LoginGift.streakDay(100, 3, 100))
        assertEquals(1, LoginGift.streakDay(98, 3, 100))
        assertEquals(1, LoginGift.streakDay(99, 7, 100))
        assertEquals(3, LoginGift.gemsFor(7))
    }
}
