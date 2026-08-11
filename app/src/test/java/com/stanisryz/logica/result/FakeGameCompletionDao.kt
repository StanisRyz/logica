package com.stanisryz.logica.result

import com.stanisryz.logica.daily.DailyChallengeEntity
import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.DailyRunEntity
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.economy.EconomyEventEntity
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.economy.PlayerEconomyEntity
import com.stanisryz.logica.economy.toEntity
import com.stanisryz.logica.economy.toPlayerEconomy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionEntity
import com.stanisryz.logica.session.GameSessionScope

/**
 * An in-memory stand-in for the Room tables the completion transaction touches, so the real
 * [GameCompletionDao.complete] logic — results, Daily lifecycle, sessions, and the wallet — can be
 * exercised without a device.
 */
internal class FakeGameCompletionDao(
    definition: DailyChallengeDefinition,
    startingEconomy: PlayerEconomy = PlayerEconomy(),
) : GameCompletionDao {
    val results = mutableMapOf<String, GameResultEntity>()
    val economyEvents = mutableMapOf<String, EconomyEventEntity>()
    private val sessions = mutableMapOf<Pair<String, String>, GameSessionEntity>()
    private val challenges = mutableMapOf<Pair<String, String>, DailyChallengeEntity>()
    private var economy: PlayerEconomyEntity? = startingEconomy.toEntity(0)
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
        listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.SUDOKU).forEach { puzzleType ->
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

    override suspend fun findEconomy(): PlayerEconomyEntity? = economy

    override suspend fun upsertEconomy(economy: PlayerEconomyEntity) {
        this.economy = economy
    }

    override suspend fun insertEconomyEvent(event: EconomyEventEntity): Long =
        if (economyEvents.putIfAbsent(event.eventId, event) == null) 1 else -1

    fun wallet(nowEpochMillis: Long): PlayerEconomy = economy.toPlayerEconomy(nowEpochMillis)

    /** A retry is a brand-new session for the very same Daily entry identity. */
    fun startRetrySession(
        definition: DailyChallengeDefinition,
        entry: DailyPuzzleEntry,
        sessionId: String,
        hintsUsed: Int,
    ) {
        sessions[entry.puzzleType.name to GameSessionScope.DAILY.name] =
            GameSessionEntity(
                puzzleType = entry.puzzleType.name,
                sessionScope = GameSessionScope.DAILY.name,
                sessionId = sessionId,
                difficulty = entry.difficulty.name,
                puzzleSeed = entry.seed.value,
                generatorVersion = entry.generatorVersion.value,
                challengeDate = definition.challengeDate.toString(),
                dailyPolicyVersion = definition.policyVersion.value,
                sessionFormatVersion = 3,
                gameplayPayload = "payload",
                moveHistoryPayload = "",
                hintsUsed = hintsUsed,
                status = "IN_PROGRESS",
                createdAtEpochMillis = 100,
                updatedAtEpochMillis = 100,
            )
    }

    fun catalogCompletion(
        puzzleType: PuzzleType,
        outcome: GameOutcome = GameOutcome.SOLVED,
    ): GameCompletion =
        GameCompletion(
            resultId = "catalog-$puzzleType",
            puzzleType = puzzleType,
            difficulty = Difficulty.EASY,
            puzzleSeed = PuzzleSeed(4242),
            generatorVersion = GeneratorVersion(1),
            sessionScope = GameSessionScope.CATALOG,
            hintsUsed = 0,
            outcome = outcome,
        )

    fun challenge(result: GameResultEntity): DailyChallengeEntity =
        requireNotNull(challenges[requireNotNull(result.challengeDate) to result.puzzleType])
}
