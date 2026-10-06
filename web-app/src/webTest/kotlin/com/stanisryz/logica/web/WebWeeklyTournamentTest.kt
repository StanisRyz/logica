package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The weekly star tournament: its week, its tables and scores, what counts, and the prize. */
class WebWeeklyTournamentTest {
    @Test
    fun aWeekStartsOnMondayMidnightInMoscow() {
        // Sunday 11 Oct 2026, 23:59:59 in Moscow is 20:59:59 UTC; Monday 00:00:00 is 21:00:00 UTC.
        val sundayEnd = utc(2026, 10, 11, 20, 59, 59)
        val mondayStart = utc(2026, 10, 11, 21, 0, 0)

        assertEquals(WebWeeklyTournament.week(sundayEnd) + 1, WebWeeklyTournament.week(mondayStart))
        assertEquals(1_000L, WebWeeklyTournament.millisUntilWeekEnd(sundayEnd))
        assertEquals(WebWeeklyTournament.WEEK_MS, WebWeeklyTournament.millisUntilWeekEnd(mondayStart))
        // The epoch Monday is week 0, and the days around New Year share their week.
        assertEquals(0, WebWeeklyTournament.week(WebWeeklyTournament.EPOCH_MS))
        assertEquals(-1, WebWeeklyTournament.week(WebWeeklyTournament.EPOCH_MS - 1))
        assertEquals(WebWeeklyTournament.week(utc(2026, 12, 31, 12, 0, 0)), WebWeeklyTournament.week(utc(2027, 1, 1, 12, 0, 0)))
        assertEquals(WebWeeklyTournament.week(utc(2026, 12, 31, 12, 0, 0)) + 1, WebWeeklyTournament.week(utc(2027, 1, 3, 21, 0, 0)))
    }

    @Test
    fun weeksTakeTurnsAndScoresCarryTheirWeek() {
        assertEquals(WebWeeklyTournament.BOARD_EVEN, WebWeeklyTournament.board(40))
        assertEquals(WebWeeklyTournament.BOARD_ODD, WebWeeklyTournament.board(41))
        assertEquals(40_000_007, WebWeeklyTournament.score(40, 7))
        assertEquals(7, WebWeeklyTournament.starsIn(40_000_007, 40))
        assertNull(WebWeeklyTournament.starsIn(38_000_120, 40))
        // A score stays within a table's 32-bit value for about 2 000 weeks.
        assertTrue(WebWeeklyTournament.score(2_000, 999_999) > 0)

        // The table of week 40 still holds week 38's entries below this week's: they are dropped.
        val table =
            WebLeaderboardSnapshot(
                listOf(
                    WebLeaderboardEntry(1, 40_000_012, "A"),
                    WebLeaderboardEntry(2, 40_000_003, "B"),
                    WebLeaderboardEntry(3, 38_000_090, "C"),
                ),
                playerRank = 3,
            )
        val shown = WebWeeklyTournament.rowsOfWeek(table, 40)
        assertEquals(listOf(12, 3), shown.entries.map { it.score })
        assertNull(shown.playerRank)
    }

    @Test
    fun onlyFirstExpertSolvesOfStarredGamesCount() {
        assertEquals(3, WebWeeklyTournament.starsForSolve(PuzzleType.BALANCE, Difficulty.EXPERT, replay = false, stars = 3))
        assertEquals(2, WebWeeklyTournament.starsForSolve(PuzzleType.WORD, Difficulty.EXPERT, replay = false, stars = 2))
        assertEquals(0, WebWeeklyTournament.starsForSolve(PuzzleType.BALANCE, Difficulty.EXPERT, replay = true, stars = 3))
        assertEquals(0, WebWeeklyTournament.starsForSolve(PuzzleType.BALANCE, Difficulty.HARD, replay = false, stars = 3))
        assertEquals(0, WebWeeklyTournament.starsForSolve(PuzzleType.GAME_2048, Difficulty.EXPERT, replay = false, stars = null))
        assertEquals(0, WebWeeklyTournament.starsForSolve(PuzzleType.BLOCK_SUDOKU, Difficulty.EXPERT, replay = false, stars = null))
    }

    @Test
    fun aSolveAddsItsStarsOnceAndOnlyWhenSaved() {
        val access = RecordingAccess(failFirstSave = true)
        val completion = WebCatalogCompletionController(access)
        val attempt =
            WebCatalogAttempt(CatalogLevelId(PuzzleType.SUDOKU, Difficulty.EXPERT, CatalogLevelNumber(1)), WebPlayerContextToken(1L))

        completion.startAttempt(attempt)
        completion.saveSolved(attempt, stars = 2)
        assertTrue(access.weekly.isEmpty()) // the failed save added nothing
        completion.saveSolved(attempt, stars = 2)
        completion.saveSolved(attempt, stars = 2)
        assertEquals(listOf(2), access.weekly)
        assertEquals(2, (completion.state as WebCatalogCompletionState.Saved).weeklyStars)

        // A replay and another difficulty add nothing.
        val replay = attempt.copy(replay = true)
        completion.startAttempt(replay)
        completion.saveSolved(replay, stars = 3)
        val hard = WebCatalogAttempt(CatalogLevelId(PuzzleType.CROWNS, Difficulty.HARD, CatalogLevelNumber(1)), WebPlayerContextToken(1L))
        completion.startAttempt(hard)
        completion.saveSolved(hard, stars = 3)
        assertEquals(listOf(2), access.weekly)
    }

    @Test
    fun theCounterStartsOverEachWeekAndMergesByWeek() {
        val progress = progressRepository()

        assertTrue(progress.addWeeklyStars(40, 3))
        assertTrue(progress.addWeeklyStars(40, 2))
        assertEquals(5, progress.rewards.value.weeklyStarsIn(40))
        assertTrue(progress.addWeeklyStars(41, 1))
        assertEquals(1, progress.rewards.value.weeklyStarsIn(41))
        assertEquals(0, progress.rewards.value.weeklyStarsIn(40))
        assertFalse(progress.addWeeklyStars(40, 9)) // a clock moved back adds nothing

        val older = WebDailyRewardsSnapshot(weeklyWeek = 40, weeklyStars = 30)
        val sameWeek = WebDailyRewardsSnapshot(weeklyWeek = 41, weeklyStars = 4)
        assertEquals(
            1,
            progress.rewards.value
                .mergedWith(older)
                .weeklyStarsIn(41),
        )
        // Two devices in one week keep the larger count, not the sum.
        assertEquals(
            4,
            progress.rewards.value
                .mergedWith(sameWeek)
                .weeklyStarsIn(41),
        )

        val encoded = WebDailyRewardsCodec.encode(progress.rewards.value)
        assertTrue(encoded.decodeToString().startsWith("LGDR5|"))
        assertEquals(progress.rewards.value, WebDailyRewardsCodec.decode(encoded))
        val v4 = assertNotNull(WebDailyRewardsCodec.decode("LGDR4|1|0|0|0|1|1||||5".encodeToByteArray()))
        assertEquals(setOf(5L), v4.unlockedArchiveDays)
        assertEquals(0, v4.weeklyStarsIn(40))
    }

    @Test
    fun prizesFollowThePlaceAndNeedStarsOfTheWeekJustEnded() {
        val week = 40
        val places = listOf(1 to 100, 2 to 50, 3 to 25, 4 to 10, 10 to 10, 11 to 5, 20 to 5, 21 to 0)
        places.forEach { (place, gems) -> assertEquals(gems, WebWeeklyTournament.prize(week, place, WebWeeklyTournament.score(week, 4))) }
        assertEquals(0, WebWeeklyTournament.prize(week, 1, WebWeeklyTournament.score(week, 0)))
        // An entry left from an older cycle of the same table wins nothing.
        assertEquals(0, WebWeeklyTournament.prize(week, 1, WebWeeklyTournament.score(week - 2, 9)))
    }

    @Test
    fun aPrizeIsPaidOnceAndNeverToAnotherPlayer() =
        runTest {
            val now = WebWeeklyTournament.EPOCH_MS + 41 * WebWeeklyTournament.WEEK_MS + 1_000
            val bridge = FakeBridge(WebLeaderboardEntry(rank = 2, score = WebWeeklyTournament.score(40, 6), name = null))
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val tournament = WebWeeklyTournamentController(bridge, scope) { now }
            val progress = progressRepository()
            var paid = 0
            val playerA = WebPlayerContextToken(1L)

            tournament.checkPrize(playerA, emptySet())
            scope.advanceUntilIdle()
            assertEquals(WebWeeklyPrize(week = 40, place = 2, gems = 50, token = playerA), tournament.prize.value)
            assertEquals(WebWeeklyTournament.BOARD_EVEN, bridge.readBoards.single())
            assertTrue(tournament.claim(playerA, progress::claimWeeklyPrize) { paid += it })
            assertEquals(50, paid)

            // The same week again, here or on a second device after the merge, pays nothing.
            assertFalse(progress.claimWeeklyPrize(40))
            val otherDevice = progressRepository()
            otherDevice.mergeCloudRewards(progress.rewards.value)
            assertFalse(otherDevice.claimWeeklyPrize(40))
            tournament.checkPrize(playerA, progress.rewards.value.claimedWeeklyPrizes)
            scope.advanceUntilIdle()
            assertNull(tournament.prize.value)

            // Another Player bound between reading the place and claiming it receives nothing.
            val fresh = WebWeeklyTournamentController(bridge, scope) { now }
            fresh.checkPrize(playerA, emptySet())
            scope.advanceUntilIdle()
            val switched = progressRepository()
            assertFalse(fresh.claim(WebPlayerContextToken(2L), switched::claimWeeklyPrize) { paid += it })
            assertEquals(50, paid)
            assertTrue(
                switched.rewards.value.claimedWeeklyPrizes
                    .isEmpty(),
            )
        }

    @Test
    fun noStarsOrAnOldEntryOffersNoPrize() =
        runTest {
            val now = WebWeeklyTournament.EPOCH_MS + 41 * WebWeeklyTournament.WEEK_MS
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val stale =
                WebWeeklyTournamentController(FakeBridge(WebLeaderboardEntry(1, WebWeeklyTournament.score(38, 20), null)), scope) { now }
            val empty =
                WebWeeklyTournamentController(FakeBridge(WebLeaderboardEntry(1, WebWeeklyTournament.score(40, 0), null)), scope) { now }

            stale.checkPrize(WebPlayerContextToken(1L), emptySet())
            empty.checkPrize(WebPlayerContextToken(1L), emptySet())
            scope.advanceUntilIdle()

            assertNull(stale.prize.value)
            assertNull(empty.prize.value)
        }

    private class FakeBridge(
        private val playerEntry: WebLeaderboardEntry?,
    ) : WebLeaderboardBridge {
        val readBoards = mutableListOf<String>()

        override fun isLeaderboardsSupported(): Boolean = true

        override suspend fun setLeaderboardScore(
            name: String,
            score: Int,
        ): Boolean = true

        override suspend fun leaderboardEntries(name: String): WebLeaderboardSnapshot? = null

        override suspend fun leaderboardPlayerEntry(name: String): WebLeaderboardEntry? {
            readBoards += name
            return playerEntry
        }
    }

    /** Catalog progress that records the weekly stars it is given, optionally failing its first save. */
    private class RecordingAccess(
        private var failFirstSave: Boolean,
        private val inner: FakeWebCatalogProgressAccess = FakeWebCatalogProgressAccess(),
    ) : WebCatalogProgressAccess by inner {
        val weekly = mutableListOf<Int>()

        override fun advanceSolved(
            attempt: WebCatalogAttempt,
            stars: Int?,
        ): WebCatalogCompletionResult {
            if (failFirstSave) {
                failFirstSave = false
                return WebCatalogCompletionResult.PersistenceFailed("quota")
            }
            return inner.advanceSolved(attempt, stars)
        }

        override fun recordWeeklyStars(
            attempt: WebCatalogAttempt,
            stars: Int,
        ): Boolean {
            weekly += stars
            return true
        }
    }

    private fun progressRepository(): WebCatalogProgressRepository =
        WebCatalogProgressRepository(
            WebCatalogProgressScope.STANDALONE,
            object : WebCatalogProgressStore {
                override fun load() = WebCatalogProgressSnapshot.EMPTY

                override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
            },
        ).also { it.loadLocal() }

    /** Epoch milliseconds of a UTC civil time, without platform time APIs. */
    private fun utc(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long {
        val epochDay = DailyDate(year, month, day).toDailyEpochDay()
        return ((epochDay * 24 + hour) * 60 + minute) * 60_000L + second * 1_000L
    }
}
