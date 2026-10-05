package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDailyChallengeRepositoryTest {
    @Test
    fun aCancellationInsideOneCommandFailsOnlyThatCommand() =
        runTest(UnconfinedTestDispatcher()) {
            val dao = CancellingOnceDao()
            val repository = RoomDailyChallengeRepository(dao, UnusedRunDao, scope = backgroundScope)
            val date = LocalDate.of(2026, 10, 5)

            val failure = runCatching { repository.read(date, PuzzleType.BALANCE) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)

            // The loop still serves the next command.
            assertNull(repository.read(date, PuzzleType.BALANCE))
            assertEquals(2, dao.calls)
        }

    private class CancellingOnceDao : DailyChallengeDao {
        var calls = 0

        override suspend fun find(
            challengeDate: String,
            puzzleType: String,
        ): DailyChallengeEntity? {
            calls += 1
            if (calls == 1) throw CancellationException("some inner work was cancelled")
            return null
        }
    }

    private object UnusedRunDao : DailyRunDao {
        override fun observeCompletedDates(): Flow<List<String>> = emptyFlow()

        override suspend fun find(challengeDate: String): DailyRunEntity? = null

        override suspend fun findEntries(challengeDate: String): List<DailyChallengeEntity> = emptyList()

        override suspend fun insertRun(entity: DailyRunEntity) = Unit

        override suspend fun insertEntries(entities: List<DailyChallengeEntity>) = Unit
    }
}
