package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV7
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV8
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Daily V8 is V7 without Word: a V8 day completes after six entries, a V7 day still needs seven. */
class WebDailyPolicyV8Test {
    private val withoutWord =
        WebDailyPuzzleOrder.maskOf(
            listOf(
                PuzzleType.BALANCE,
                PuzzleType.CROWNS,
                PuzzleType.SUDOKU,
                PuzzleType.GAME_2048,
                PuzzleType.NONOGRAM,
                PuzzleType.BLOCK_SUDOKU,
            ),
        )

    @Test
    fun aV8DayCompletesAfterSixEntries() {
        val v8 = WebDailyDayRecord(V8_DATE, DailyChallengePolicyV8.VERSION, solvedMask = withoutWord)

        assertEquals(withoutWord, v8.requiredMask)
        assertEquals(6, v8.completedEntryCount)
        assertTrue(v8.fullyCompleted)
    }

    @Test
    fun aV7DayStillNeedsItsWordEntry() {
        val sixOfSeven = WebDailyDayRecord(V7_DATE, DailyChallengePolicyV7.VERSION, solvedMask = withoutWord)
        assertEquals(6, sixOfSeven.completedEntryCount)
        assertFalse(sixOfSeven.fullyCompleted)

        val all = sixOfSeven.copy(solvedMask = withoutWord or WebDailyPuzzleOrder.bit(PuzzleType.WORD), wordSolvedAttemptsUsed = 4)
        assertEquals(7, all.completedEntryCount)
        assertTrue(all.fullyCompleted)
    }

    @Test
    fun aSnapshotWithV7AndV8DaysMergesAndRoundTripsWithoutConflict() {
        val v7Day =
            WebDailyDayRecord(
                V7_DATE,
                DailyChallengePolicyV7.VERSION,
                solvedMask = WebDailyPuzzleOrder.bit(PuzzleType.WORD),
                wordSolvedAttemptsUsed = 2,
            )
        val v8Day = WebDailyDayRecord(V8_DATE, DailyChallengePolicyV8.VERSION, solvedMask = WebDailyPuzzleOrder.bit(PuzzleType.CROWNS))
        val local = WebDailySnapshotV1(days = mapOf(V7_DATE to v7Day))
        val cloud = WebDailySnapshotV1(days = mapOf(V8_DATE to v8Day))

        val merged = WebDailyMerger.mergeReporting(local, cloud)

        assertTrue(merged.policyConflicts.isEmpty())
        assertEquals(mapOf(V7_DATE to v7Day, V8_DATE to v8Day), merged.snapshot.days)
        assertEquals(merged.snapshot, WebDailyCodec.decode(WebDailyCodec.encode(merged.snapshot)))
    }

    private companion object {
        val V7_DATE = DailyDate(2026, 10, 11)
        val V8_DATE = DailyDate(2026, 10, 12)
    }
}
