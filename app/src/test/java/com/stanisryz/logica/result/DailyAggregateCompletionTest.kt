package com.stanisryz.logica.result

import com.stanisryz.logica.daily.DailyChallengeEntity
import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.DailyRunEntity
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionEntity
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyAggregateCompletionTest {
    @Test
    fun firstV2EntryKeepsRunInProgressAndFinalEntryCompletesIdempotently() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))
            val dao = FakeGameCompletionDao(definition)
            val first = definition.entries[0].completion(definition, "daily-0", hintsUsed = 1).toEntity(1_000)
            val final = definition.entries[1].completion(definition, "daily-1", hintsUsed = 2).toEntity(2_000)

            dao.complete(first)

            assertEquals(DailyChallengeStatus.COMPLETED.name, dao.challenge(first).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertNull(dao.run.completedAtEpochMillis)

            val finalResult = dao.complete(final)
            val repeatedResult = dao.complete(final)

            assertEquals(finalResult, repeatedResult)
            assertEquals(DailyRunStatus.COMPLETED.name, dao.run.status)
            assertEquals(2_000L, dao.run.completedAtEpochMillis)
            assertEquals(2, dao.results.size)
            assertNull(dao.findSession(first.puzzleType, first.sessionScope))
            assertNull(dao.findSession(final.puzzleType, final.sessionScope))
        }

    @Test
    fun completingOneSessionLeavesTheOtherThreePuzzleScopeSessionsIntact() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))
            val dao = FakeGameCompletionDao(definition)
            val catalogCrowns = dao.catalogCompletion(PuzzleType.CROWNS).toEntity(1_000)
            val dailyBalance = definition.entries[0].completion(definition, "daily-0", hintsUsed = 1).toEntity(2_000)

            dao.complete(catalogCrowns)

            assertNull(dao.findSession(PuzzleType.CROWNS.name, GameSessionScope.CATALOG.name))
            assertNotNull(dao.findSession(PuzzleType.BALANCE.name, GameSessionScope.CATALOG.name))
            assertNotNull(dao.findSession(PuzzleType.BALANCE.name, GameSessionScope.DAILY.name))
            assertNotNull(dao.findSession(PuzzleType.CROWNS.name, GameSessionScope.DAILY.name))
            // A Catalog completion must not touch any Daily lifecycle state.
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(DailyChallengeStatus.IN_PROGRESS.name, dao.challenge(dailyBalance).status)

            dao.complete(dailyBalance)

            assertNull(dao.findSession(PuzzleType.BALANCE.name, GameSessionScope.DAILY.name))
            assertNotNull(dao.findSession(PuzzleType.BALANCE.name, GameSessionScope.CATALOG.name))
            assertNotNull(dao.findSession(PuzzleType.CROWNS.name, GameSessionScope.DAILY.name))
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(2, dao.results.size)
        }

    private fun DailyPuzzleEntry.completion(
        definition: DailyChallengeDefinition,
        resultId: String,
        hintsUsed: Int,
    ): GameCompletion =
        GameCompletion(
            resultId = resultId,
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = seed,
            generatorVersion = generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = hintsUsed,
            challengeDate = definition.challengeDate,
            dailyPolicyVersion = definition.policyVersion,
        )
}

private class FakeGameCompletionDao(
    definition: DailyChallengeDefinition,
) : GameCompletionDao {
    val results = mutableMapOf<String, GameResultEntity>()
    private val sessions = mutableMapOf<Pair<String, String>, GameSessionEntity>()
    private val challenges = mutableMapOf<Pair<String, String>, DailyChallengeEntity>()
    var run =
        DailyRunEntity(
            challengeDate = definition.challengeDate.toString(),
            dailyPolicyVersion = definition.policyVersion.value,
            status = DailyRunStatus.IN_PROGRESS.name,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 100,
            completedAtEpochMillis = null,
        )
        private set

    init {
        listOf(PuzzleType.BALANCE, PuzzleType.CROWNS).forEach { puzzleType ->
            sessions[puzzleType.name to GameSessionScope.CATALOG.name] =
                GameSessionEntity(
                    puzzleType = puzzleType.name,
                    sessionScope = GameSessionScope.CATALOG.name,
                    sessionId = "catalog-$puzzleType",
                    difficulty = Difficulty.EASY.name,
                    puzzleSeed = 4242,
                    generatorVersion = 1,
                    challengeDate = null,
                    dailyPolicyVersion = null,
                    sessionFormatVersion = 1,
                    gameplayPayload = "payload",
                    moveHistoryPayload = "",
                    hintsUsed = 0,
                    status = "IN_PROGRESS",
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                )
        }
        definition.entries.forEachIndexed { index, entry ->
            val resultId = "daily-$index"
            val key = entry.puzzleType.name to GameSessionScope.DAILY.name
            sessions[key] =
                GameSessionEntity(
                    puzzleType = entry.puzzleType.name,
                    sessionScope = GameSessionScope.DAILY.name,
                    sessionId = resultId,
                    difficulty = entry.difficulty.name,
                    puzzleSeed = entry.seed.value,
                    generatorVersion = entry.generatorVersion.value,
                    challengeDate = definition.challengeDate.toString(),
                    dailyPolicyVersion = definition.policyVersion.value,
                    sessionFormatVersion = 1,
                    gameplayPayload = "payload",
                    moveHistoryPayload = "",
                    hintsUsed = index + 1,
                    status = "IN_PROGRESS",
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                )
            challenges[definition.challengeDate.toString() to entry.puzzleType.name] =
                DailyChallengeEntity(
                    challengeDate = definition.challengeDate.toString(),
                    puzzleType = entry.puzzleType.name,
                    dailyPolicyVersion = definition.policyVersion.value,
                    difficulty = entry.difficulty.name,
                    puzzleSeed = entry.seed.value,
                    generatorVersion = entry.generatorVersion.value,
                    status = DailyChallengeStatus.IN_PROGRESS.name,
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                )
        }
    }

    override suspend fun findResult(resultId: String): GameResultEntity? = results[resultId]

    override suspend fun insertResult(result: GameResultEntity): Long = if (results.putIfAbsent(result.resultId, result) == null) 1 else -1

    override suspend fun findSession(
        puzzleType: String,
        sessionScope: String,
    ): GameSessionEntity? = sessions[puzzleType to sessionScope]

    override suspend fun deleteSession(
        puzzleType: String,
        sessionScope: String,
        sessionId: String,
    ): Int {
        val key = puzzleType to sessionScope
        if (sessions[key]?.sessionId != sessionId) return 0
        sessions.remove(key)
        return 1
    }

    override suspend fun findDailyChallenge(
        challengeDate: String,
        puzzleType: String,
    ): DailyChallengeEntity? = challenges[challengeDate to puzzleType]

    override suspend fun completeDailyChallenge(
        challengeDate: String,
        puzzleType: String,
        policyVersion: Int,
        difficulty: String,
        puzzleSeed: Long,
        generatorVersion: Int,
        completedAt: Long,
    ): Int {
        val key = challengeDate to puzzleType
        val entry = challenges[key] ?: return 0
        if (
            entry.dailyPolicyVersion != policyVersion ||
            entry.difficulty != difficulty ||
            entry.puzzleSeed != puzzleSeed ||
            entry.generatorVersion != generatorVersion
        ) {
            return 0
        }
        challenges[key] = entry.copy(status = DailyChallengeStatus.COMPLETED.name, updatedAtEpochMillis = completedAt)
        return 1
    }

    override suspend fun findDailyRun(challengeDate: String): DailyRunEntity? = run.takeIf { it.challengeDate == challengeDate }

    override suspend fun countIncompleteDailyChallenges(
        challengeDate: String,
        policyVersion: Int,
    ): Int =
        challenges.values.count { entry ->
            entry.challengeDate == challengeDate &&
                entry.dailyPolicyVersion == policyVersion &&
                entry.status != DailyChallengeStatus.COMPLETED.name
        }

    override suspend fun completeDailyRun(
        challengeDate: String,
        policyVersion: Int,
        completedAt: Long,
    ): Int {
        if (
            run.challengeDate != challengeDate ||
            run.dailyPolicyVersion != policyVersion ||
            run.status != DailyRunStatus.IN_PROGRESS.name
        ) {
            return 0
        }
        run =
            run.copy(
                status = DailyRunStatus.COMPLETED.name,
                updatedAtEpochMillis = completedAt,
                completedAtEpochMillis = completedAt,
            )
        return 1
    }

    fun catalogCompletion(puzzleType: PuzzleType): GameCompletion =
        GameCompletion(
            resultId = "catalog-$puzzleType",
            puzzleType = puzzleType,
            difficulty = Difficulty.EASY,
            puzzleSeed = PuzzleSeed(4242),
            generatorVersion = GeneratorVersion(1),
            sessionScope = GameSessionScope.CATALOG,
            hintsUsed = 0,
        )

    fun challenge(result: GameResultEntity): DailyChallengeEntity =
        requireNotNull(challenges[requireNotNull(result.challengeDate) to result.puzzleType])
}
