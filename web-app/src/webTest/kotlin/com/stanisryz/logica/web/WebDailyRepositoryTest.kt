package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV4
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebDailyRepositoryTest {
    @Test
    fun multiDeviceMergeUnionsFactsAndIsCommutativeIdempotent() {
        val date = DailyDate(2026, 8, 20)
        val local =
            snapshot(
                record(
                    date,
                    failed = setOf(PuzzleType.BALANCE),
                    solved = setOf(PuzzleType.WORD),
                    wordAttempts = 5,
                ),
            )
        val cloud =
            snapshot(
                record(
                    date,
                    solved = setOf(PuzzleType.BALANCE, PuzzleType.SUDOKU),
                ),
            )
        val store = FakeDailyStore(local)
        val repository = WebDailyRepository(WebCatalogProgressScope.STANDALONE, store) { date }
        repository.loadLocal()

        val first = assertIs<WebDailyMergeResult.Merged>(repository.mergeCloud(cloud))
        val repeated = assertIs<WebDailyMergeResult.Merged>(repository.mergeCloud(first.snapshot))
        val reversed = WebDailyMerger.merge(cloud, local)
        val mergedRecord = first.snapshot.days.getValue(date)

        assertEquals(first.snapshot, reversed)
        assertEquals(first.snapshot, repeated.snapshot)
        assertFalse(repeated.cloudWriteRequired)
        assertTrue(mergedRecord.facts(PuzzleType.BALANCE).failedSeen)
        assertTrue(mergedRecord.facts(PuzzleType.BALANCE).solved)
        assertTrue(mergedRecord.facts(PuzzleType.WORD).solved)
        assertTrue(mergedRecord.facts(PuzzleType.SUDOKU).solved)
        assertEquals(WebDailyEntryState.COMPLETED, repository.stateFor(date).entries[PuzzleType.BALANCE])
        assertEquals(5, mergedRecord.wordSolvedAttemptsUsed)

        val encoded = WebDailyCodec.encode(first.snapshot)
        assertContentEquals(encoded, WebDailyCodec.encode(first.snapshot))
        assertEquals(first.snapshot, WebDailyCodec.decode(encoded))
        encoded[WebDailyHeader.RECORD_SOLVED_MASK_OFFSET] = 0x80.toByte()
        assertNull(WebDailyCodec.decode(encoded))

        val conflicting = snapshot(record(date, policyV4 = true))
        assertIs<WebDailyMergeResult.PolicyConflict>(repository.mergeCloud(conflicting))
        assertEquals(first.snapshot, repository.snapshot.value)
    }

    @Test
    fun v5OneSolveQualifiesWithoutFullCompletionAndUsesSharedStreak() {
        val today = DailyDate(2026, 8, 20)
        val store = FakeDailyStore(WebDailySnapshotV1.EMPTY)
        val repository = WebDailyRepository(WebCatalogProgressScope.STANDALONE, store) { today }
        repository.loadLocal()

        assertFalse(repository.stateFor(today).isDurable)
        assertEquals(0, store.saveCount)
        listOf(18, 19, 20).forEach { day ->
            val definition = DailyChallengePolicyV5.definitionFor(DailyDate(2026, 8, day))
            assertIs<WebDailyMutationResult.Updated>(repository.ensureRun(definition))
            assertIs<WebDailyMutationResult.Updated>(repository.recordSolved(definition, PuzzleType.BALANCE))
            assertIs<WebDailyMutationResult.Idempotent>(repository.recordSolved(definition, PuzzleType.BALANCE))
        }

        val historicalPartial = DailyChallengePolicyV4.definitionFor(DailyDate(2026, 8, 17))
        assertIs<WebDailyMutationResult.Updated>(repository.ensureRun(historicalPartial))
        assertIs<WebDailyMutationResult.Updated>(repository.recordSolved(historicalPartial, PuzzleType.BALANCE))
        val future = DailyChallengePolicyV5.definitionFor(DailyDate(2026, 8, 21))
        assertIs<WebDailyMutationResult.Updated>(repository.ensureRun(future))
        assertIs<WebDailyMutationResult.Updated>(repository.recordSolved(future, PuzzleType.BALANCE))

        val history = repository.history()

        assertEquals(3, history.qualifiedDates.size)
        assertFalse(historicalPartial.challengeDate in history.qualifiedDates)
        assertFalse(future.challengeDate in history.qualifiedDates)
        assertEquals(0, history.fullyCompletedDailyCount)
        assertEquals(1, history.today.completedEntryCount)
        assertFalse(history.today.fullyCompleted)
        assertTrue(history.today.qualifiedForStreak)
        assertEquals(3, history.streak.current)
        assertEquals(3, history.streak.best)
    }

    private fun snapshot(record: WebDailyDayRecord): WebDailySnapshotV1 = WebDailySnapshotV1(days = mapOf(record.date to record))

    private fun record(
        date: DailyDate,
        failed: Set<PuzzleType> = emptySet(),
        solved: Set<PuzzleType> = emptySet(),
        wordAttempts: Int? = null,
        policyV4: Boolean = false,
    ): WebDailyDayRecord =
        WebDailyDayRecord(
            date = date,
            policyVersion = if (policyV4) DailyChallengePolicyV4.VERSION else DailyChallengePolicyV5.VERSION,
            failedMask = WebDailyPuzzleOrder.maskOf(failed),
            solvedMask = WebDailyPuzzleOrder.maskOf(solved),
            wordSolvedAttemptsUsed = wordAttempts,
        )

    private class FakeDailyStore(
        var snapshot: WebDailySnapshotV1,
    ) : WebDailyStore {
        var saveCount = 0

        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
            saveCount++
        }
    }

    private object WebDailyHeader {
        const val RECORD_SOLVED_MASK_OFFSET = 8 + 6
    }
}
