package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV7
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.daily.DailyHubEntryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Web Daily archive: a past day opens for good in the rewards record, its play fills the day
 * (solvedMask) but never the streak (onTimeSolvedMask), and the snapshot keeps reading older data.
 */
class WebDailyArchiveTest {
    private val today = DailyDate(2026, 10, 6)
    private val pastDay = DailyDate(2026, 10, 3)
    private val balance = WebDailyPuzzleOrder.bit(PuzzleType.BALANCE)
    private val crowns = WebDailyPuzzleOrder.bit(PuzzleType.CROWNS)

    @Test
    fun anOnTimeOnlySnapshotStaysSchemaOneAndAnArchiveSolveWritesSchemaTwo() {
        val onTime = snapshot(record(pastDay, solved = balance))
        val legacyBytes = WebDailyCodec.encode(onTime)

        assertEquals(1, legacyBytes[4].toInt())
        // A schema-1 record reads with every solve on time.
        assertEquals(
            balance,
            WebDailyCodec
                .decode(legacyBytes)!!
                .days
                .getValue(pastDay)
                .onTimeSolvedMask,
        )

        val archive = snapshot(record(pastDay, solved = balance or crowns, onTime = balance))
        val bytes = WebDailyCodec.encode(archive)
        assertEquals(2, bytes[4].toInt())
        assertEquals(archive, WebDailyCodec.decode(bytes))
        assertContentEquals(bytes, WebDailyCodec.encode(WebDailyCodec.decode(bytes)!!))
    }

    @Test
    fun mergeUnitesOnTimeMasksAndOnlyOnTimeSolvesKeepTheStreak() {
        val local = snapshot(record(pastDay, solved = balance, onTime = 0))
        val cloud = snapshot(record(pastDay, solved = crowns, onTime = crowns))

        val merged = WebDailyMerger.merge(local, cloud).days.getValue(pastDay)
        assertEquals(balance or crowns, merged.solvedMask)
        assertEquals(crowns, merged.onTimeSolvedMask)

        // An archive-only solve fills the calendar, never the streak.
        val archiveOnly = record(pastDay, solved = balance, onTime = 0)
        assertFalse(archiveOnly.qualifiedForStreak)
        assertTrue(record(pastDay, solved = balance).qualifiedForStreak)
    }

    @Test
    fun archivePlayCompletesTheDayWithoutTheStreak() {
        val repository = repository()
        val session = session(repository, token = 7L)
        val coordinator = WebDailyGameplayCoordinator(session) { today }
        val definition = DailyChallengePolicyResolver.definitionFor(pastDay, DailyChallengePolicyResolver.NEW_RUN_VERSION)

        definition.entries.forEach { entry ->
            val started = assertIs<WebDailyStartResult.Started>(coordinator.start(entry.puzzleType, archiveDate = pastDay))
            assertTrue(started.attempt.archive)
            assertEquals(pastDay, started.attempt.definition.challengeDate)
            assertEquals(
                WebDailyRecordResult.Recorded,
                coordinator.recordTerminalResult(started.attempt, WebStatisticsTerminalOutcome.SOLVED),
            )
        }

        val record =
            repository.snapshot.value.days
                .getValue(pastDay)
        assertTrue(record.fullyCompleted)
        assertEquals(0, record.onTimeSolvedMask)
        val metrics = repository.snapshot.value.dailyProfileMetrics(today)
        assertEquals(1L, metrics.completedCount)
        assertEquals(0L, metrics.currentStreak)
        assertEquals(0L, metrics.bestStreak)
        // Today's own Daily still counts on time.
        val todays = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.BALANCE))
        assertFalse(todays.attempt.archive)
        coordinator.recordTerminalResult(todays.attempt, WebStatisticsTerminalOutcome.SOLVED)
        assertEquals(
            1L,
            repository.snapshot.value
                .dailyProfileMetrics(today)
                .currentStreak,
        )
    }

    @Test
    fun aDayOutsideTheWindowNeverStartsAndAPlayerSwitchWritesNothing() {
        val repositoryA = repository()
        val repositoryB = repository()
        val session = session(repositoryA, token = 7L)
        val coordinator = WebDailyGameplayCoordinator(session) { today }

        val tooOld = DailyDate(2026, 9, 5)
        assertIs<WebDailyStartResult.NotStarted>(coordinator.start(PuzzleType.BALANCE, archiveDate = tooOld))
        assertIs<WebDailyStartResult.NotStarted>(coordinator.start(PuzzleType.BALANCE, archiveDate = today))

        val started = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.BALANCE, archiveDate = pastDay))
        session.dailyBinding.value = ready(repositoryB, token = 8L)

        assertEquals(
            WebDailyRecordResult.StaleContext,
            coordinator.recordTerminalResult(started.attempt, WebStatisticsTerminalOutcome.SOLVED),
        )
        assertTrue(
            repositoryB.snapshot.value.days
                .isEmpty(),
        )
    }

    @Test
    fun theArchiveListsThirtyDaysAndAStartedDayIsAlreadyOpen() {
        val started = snapshot(record(pastDay, solved = balance, policy = DailyChallengePolicyV7.VERSION.value))
        val opened = DailyDate(2026, 10, 1).toDailyEpochDay()

        val days = webArchiveDays(started, today, unlockedDays = setOf(opened))

        assertEquals(EconomyPolicy.DAILY_ARCHIVE_DAYS, days.size)
        assertEquals(DailyDate(2026, 10, 5), days.first().date)
        val startedDay = days.single { it.date == pastDay }
        assertTrue(startedDay.unlocked)
        // A started day keeps its own policy: V7 still has its Word entry.
        assertTrue(startedDay.entries.any { it.puzzleType == PuzzleType.WORD })
        assertEquals(DailyHubEntryState.COMPLETED, startedDay.entries.single { it.puzzleType == PuzzleType.BALANCE }.state)
        assertTrue(days.single { it.epochDay == opened }.unlocked)
        val fresh = days.single { it.date == DailyDate(2026, 10, 2) }
        assertFalse(fresh.unlocked)
        assertFalse(fresh.entries.any { it.puzzleType == PuzzleType.WORD })
    }

    @Test
    fun theRewardsRecordKeepsOpenedDaysOnceAndReadsLgdr3() {
        val progress =
            WebCatalogProgressRepository(
                WebCatalogProgressScope.STANDALONE,
                object : WebCatalogProgressStore {
                    override fun load() = WebCatalogProgressSnapshot.EMPTY

                    override fun save(snapshot: WebCatalogProgressSnapshot) = Unit
                },
            ).also { it.loadLocal() }
        val day = pastDay.toDailyEpochDay()

        assertTrue(progress.claimDailyArchive(day))
        assertFalse(progress.claimDailyArchive(day))
        assertEquals(setOf(day), progress.rewards.value.unlockedArchiveDays)

        val encoded = WebDailyRewardsCodec.encode(progress.rewards.value)
        assertTrue(encoded.decodeToString().startsWith("LGDR5|"))
        assertEquals(progress.rewards.value, WebDailyRewardsCodec.decode(encoded))
        val v3 = assertNotNull(WebDailyRewardsCodec.decode("LGDR3|20000|1|0|0|19999|2||first|20001".encodeToByteArray()))
        assertEquals(setOf(20_001L), v3.restoredStreakDays)
        assertEquals(emptySet(), v3.unlockedArchiveDays)
        assertEquals(
            setOf(1L, 2L),
            WebDailyRewardsSnapshot(unlockedArchiveDays = setOf(1L))
                .mergedWith(WebDailyRewardsSnapshot(unlockedArchiveDays = setOf(2L)))
                .unlockedArchiveDays,
        )
    }

    private fun record(
        date: DailyDate,
        solved: Int,
        onTime: Int = solved,
        policy: Int = DailyChallengePolicyResolver.NEW_RUN_VERSION.value,
    ): WebDailyDayRecord =
        WebDailyDayRecord(
            date = date,
            policyVersion = DailyPolicyVersion(policy),
            solvedMask = solved,
            onTimeSolvedMask = onTime,
        )

    private fun snapshot(vararg records: WebDailyDayRecord): WebDailySnapshotV1 = WebDailySnapshotV1(days = records.associateBy { it.date })

    private class FakeDailyStore : WebDailyStore {
        var snapshot: WebDailySnapshotV1 = WebDailySnapshotV1.EMPTY

        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
        }
    }

    private class FakeSessionAccess : WebDailySessionAccess {
        override val dailyBinding = MutableStateFlow<WebDailyBinding>(WebDailyBinding.Loading)

        override fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready) = Unit
    }

    private fun repository(): WebDailyRepository =
        WebDailyRepository(WebCatalogProgressScope.STANDALONE, FakeDailyStore()) {
            today
        }.also { it.loadLocal() }

    private fun ready(
        repository: WebDailyRepository,
        token: Long,
    ): WebDailyBinding.Ready =
        WebDailyBinding.Ready(
            token = WebPlayerContextToken(token),
            repository = repository,
            identity = null,
            syncStatus = WebDailyCloudSyncStatus.LOCAL_ONLY,
        )

    private fun session(
        repository: WebDailyRepository,
        token: Long,
    ): FakeSessionAccess = FakeSessionAccess().also { it.dailyBinding.value = ready(repository, token) }
}
