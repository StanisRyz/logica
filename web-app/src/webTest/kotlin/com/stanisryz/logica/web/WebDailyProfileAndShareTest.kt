package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.daily.DailyShareFormatter
import com.stanisryz.logica.ui.profile.DailyProfileMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.8c regressions: durable Daily history maps into the shared Profile metrics with
 * full-completion count kept separate from policy-based streak qualification, and only a fully
 * completed day produces the deterministic spoiler-free share payload/text.
 */
class WebDailyProfileAndShareTest {
    private val today = DailyDate(2026, 8, 24)

    private class FakeDailyStore : WebDailyStore {
        var snapshot: WebDailySnapshotV1 = WebDailySnapshotV1.EMPTY

        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
        }
    }

    private fun repository(): WebDailyRepository =
        WebDailyRepository(WebCatalogProgressScope.STANDALONE, FakeDailyStore()) { today }.also { it.loadLocal() }

    private fun solveAll(
        repository: WebDailyRepository,
        date: DailyDate,
        wordAttempts: Int?,
        puzzles: List<PuzzleType> =
            listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD, PuzzleType.SUDOKU, PuzzleType.GAME_2048),
    ) {
        val definition = DailyChallengePolicyV5.definitionFor(date)
        repository.ensureRun(definition)
        puzzles.forEach { puzzleType ->
            repository.recordSolved(
                definition,
                puzzleType,
                wordAttempts.takeIf { puzzleType == PuzzleType.WORD },
            )
        }
    }

    @Test
    fun durableHistoryMapsToCompletedCountAndPolicyBasedStreaksWithRecentDays() {
        val repository = repository()
        // Aug 20 fully completed, Aug 21 only streak-qualified, Aug 22 fully completed,
        // Aug 23 untouched, Aug 24 (today) fully completed.
        solveAll(repository, DailyDate(2026, 8, 20), wordAttempts = 4)
        solveAll(repository, DailyDate(2026, 8, 21), wordAttempts = null, puzzles = listOf(PuzzleType.CROWNS))
        solveAll(repository, DailyDate(2026, 8, 22), wordAttempts = 2)
        solveAll(repository, today, wordAttempts = 3)

        val metrics: DailyProfileMetrics = repository.snapshot.value.dailyProfileMetrics(today)

        // Full completions only; the partially solved Aug 21 never counts as completed.
        assertEquals(3L, metrics.completedCount)
        // Qualified dates are 20, 21, 22, 24: today continues a run broken by the silent Aug 23.
        assertEquals(1L, metrics.currentStreak)
        assertEquals(3L, metrics.bestStreak)

        val recent = metrics.recentDays
        // Only four durable days exist, so fewer than the compact cap are shown.
        assertEquals(4, recent.size)
        assertEquals("24 августа", recent.first().dateLabel)
        assertTrue(recent.first().fullyCompleted)
        assertEquals(5, recent.first().solvedCount)
        assertEquals(5, recent.first().totalCount)
        val aug21 = recent.single { it.dateLabel == "21 августа" }
        assertFalse(aug21.fullyCompleted)
        assertEquals(1, aug21.solvedCount)
        assertEquals(5, aug21.totalCount)
    }

    @Test
    fun onlyFullyCompletedDaysProduceDeterministicSpoilerFreeShareText() {
        val repository = repository()
        solveAll(repository, today, wordAttempts = 3)
        val fullRecord = repository.snapshot.value.days.getValue(today)

        val payload =
            webDailySharePayloadOrNull(fullRecord, today, currentStreak = 4)
        val text = DailyShareFormatter.format(payload!!)
        val expected =
            listOf(
                "Логика дня — 24 августа",
                "",
                "Баланс  ✓",
                "Короны  ✓",
                "Слово   3/6",
                "Судоку  ✓",
                "2048    ✓",
                "",
                "5 из 5",
                "🔥 Серия: 4 дня",
            ).joinToString(separator = "\n")
        assertEquals(expected, text)
        // Spoiler-free by construction: no seeds, answers, boards, or internal identities.
        listOf("seed", "generator", "answer").forEach { forbidden -> assertFalse(text.contains(forbidden)) }

        // A partially qualified V5 day is never a completed share result.
        val partialRepository = repository()
        val definition = DailyChallengePolicyV5.definitionFor(today)
        partialRepository.ensureRun(definition)
        partialRepository.recordSolved(definition, PuzzleType.BALANCE)
        val partialRecord = partialRepository.snapshot.value.days.getValue(today)
        assertNull(webDailySharePayloadOrNull(partialRecord, today, currentStreak = 1))
    }
}
