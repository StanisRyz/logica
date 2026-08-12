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
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionScope

/**
 * An in-memory stand-in for the Room tables the completion transaction touches, so the real
 * [GameCompletionDao.complete] logic — results, Daily lifecycle, Catalog level progression, and the
 * wallet — can be exercised without a device. Attempts are transient now, so there is no session
 * table here at all.
 */
internal class FakeGameCompletionDao(
    definition: DailyChallengeDefinition,
    startingEconomy: PlayerEconomy = PlayerEconomy(),
) : GameCompletionDao {
    val results = mutableMapOf<String, GameResultEntity>()
    val economyEvents = mutableMapOf<String, EconomyEventEntity>()
    private val challenges = mutableMapOf<Pair<String, String>, DailyChallengeEntity>()
    private val progress = mutableMapOf<Triple<String, String, Int>, Int>()
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
        definition.entries.forEach { entry ->
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

    override suspend fun insertCatalogProgressIfAbsent(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
        nextLevel: Int,
        updatedAt: Long,
    ) {
        progress.putIfAbsent(Triple(puzzleType, difficulty, packVersion), nextLevel)
    }

    override suspend fun advanceCatalogProgress(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
        nextLevel: Int,
        updatedAt: Long,
    ): Int {
        val key = Triple(puzzleType, difficulty, packVersion)
        val current = progress[key] ?: return 0
        if (current >= nextLevel) return 0
        progress[key] = nextLevel
        return 1
    }

    fun wallet(nowEpochMillis: Long): PlayerEconomy = economy.toPlayerEconomy(nowEpochMillis)

    /** The stored next level to play, or null while the bucket has never been completed. */
    fun currentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty = Difficulty.EASY,
        packVersion: CatalogLevelPackVersion = CatalogLevelPackVersion.V1,
    ): Int? = progress[Triple(puzzleType.name, difficulty.name, packVersion.value)]

    /** One Catalog attempt of [puzzleType] at one public level. */
    fun catalogCompletion(
        puzzleType: PuzzleType,
        outcome: GameOutcome = GameOutcome.SOLVED,
        difficulty: Difficulty = Difficulty.EASY,
        levelNumber: Int = 1,
        attemptId: String = "attempt",
    ): GameCompletion =
        GameCompletion(
            resultId = "catalog:1:$puzzleType:$difficulty:$levelNumber:$attemptId",
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = PuzzleSeed(4242),
            generatorVersion = GeneratorVersion(1),
            sessionScope = GameSessionScope.CATALOG,
            hintsUsed = 0,
            outcome = outcome,
            attemptsUsed = if (puzzleType == PuzzleType.WORD) WORD_ATTEMPTS else null,
            catalogLevel =
                CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(levelNumber), CatalogLevelPackVersion.V1),
        )

    fun challenge(result: GameResultEntity): DailyChallengeEntity =
        requireNotNull(challenges[requireNotNull(result.challengeDate) to result.puzzleType])

    private companion object {
        /** Word is the only type that records attempts; the exact count is irrelevant to economy. */
        const val WORD_ATTEMPTS = 3
    }
}
