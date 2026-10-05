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
    fun questsBeforeTheBlockSudokuDayStayExactlyAsTheyWere() {
        // Recorded from the six-game list for the two weeks before the switch.
        val recorded =
            (20_724L until DailyQuests.BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY).map { day ->
                DailyQuests.forDay(day).joinToString("|") { "${it.kind}:${it.target}:${it.gems}:${it.puzzleType}" }
            }
        assertEquals(QUESTS_BEFORE_BLOCK_SUDOKU, recorded)
        val earlier = (20_000L until DailyQuests.BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY).map { DailyQuests.forDay(it)[1].puzzleType }
        assertTrue(PuzzleType.BLOCK_SUDOKU !in earlier)
    }

    @Test
    fun fromTheBlockSudokuDayBlockSudokuMayBeNamed() {
        val days = DailyQuests.BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY until DailyQuests.BLOCK_SUDOKU_QUESTS_FROM_EPOCH_DAY + 120
        val secondKinds = days.map { DailyQuests.forDay(it)[1].puzzleType ?: DailyQuests.forDay(it)[1].kind }.toSet()
        assertTrue(PuzzleType.BLOCK_SUDOKU in secondKinds)
        // Six games, Block Sudoku, and the Daily entry.
        assertEquals(8, secondKinds.size)
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

    private companion object {
        val QUESTS_BEFORE_BLOCK_SUDOKU =
            listOf(
                "PLAY:3:1:null|SOLVE_GAME:1:1:WORD|SOLVE_HARD:1:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:NONOGRAM|DIFFERENT_GAMES:3:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:WORD|SOLVE:5:2:null",
                "SOLVE:2:1:null|SOLVE_GAME:1:1:NONOGRAM|DIFFERENT_GAMES:3:2:null",
                "SOLVE:2:1:null|SOLVE_GAME:1:1:SUDOKU|SOLVE:5:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:GAME_2048|SOLVE_HARD:1:2:null",
                "PLAY:3:1:null|SOLVE_DAILY:1:1:null|SOLVE:5:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:CROWNS|SOLVE_HARD:1:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:NONOGRAM|SOLVE_HARD:1:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:CROWNS|DIFFERENT_GAMES:3:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:NONOGRAM|SOLVE_HARD:1:2:null",
                "PLAY:3:1:null|SOLVE_DAILY:1:1:null|SOLVE_HARD:1:2:null",
                "SOLVE:2:1:null|SOLVE_DAILY:1:1:null|SOLVE:5:2:null",
                "PLAY:3:1:null|SOLVE_GAME:1:1:CROWNS|SOLVE_HARD:1:2:null",
            )
    }
}
