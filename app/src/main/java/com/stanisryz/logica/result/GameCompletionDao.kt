package com.stanisryz.logica.result

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.stanisryz.logica.daily.DailyChallengeEntity
import com.stanisryz.logica.daily.DailyRunEntity
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.economy.EconomyEffect
import com.stanisryz.logica.economy.EconomyEventEntity
import com.stanisryz.logica.economy.PlayerEconomyEntity
import com.stanisryz.logica.economy.failedPenalty
import com.stanisryz.logica.economy.solvedReward
import com.stanisryz.logica.economy.toEntity
import com.stanisryz.logica.economy.toPlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.session.GameSessionScope

@Dao
internal interface GameCompletionDao {
    @Query("SELECT * FROM game_results WHERE result_id = :resultId LIMIT 1")
    suspend fun findResult(resultId: String): GameResultEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResult(result: GameResultEntity): Long

    @Query("SELECT * FROM daily_challenges WHERE challenge_date = :challengeDate AND puzzle_type = :puzzleType LIMIT 1")
    suspend fun findDailyChallenge(
        challengeDate: String,
        puzzleType: String,
    ): DailyChallengeEntity?

    @Query(
        "UPDATE daily_challenges SET status = 'COMPLETED', updated_at_epoch_millis = :completedAt " +
            "WHERE challenge_date = :challengeDate AND puzzle_type = :puzzleType " +
            "AND daily_policy_version = :policyVersion AND difficulty = :difficulty " +
            "AND puzzle_seed = :puzzleSeed AND generator_version = :generatorVersion",
    )
    suspend fun completeDailyChallenge(
        challengeDate: String,
        puzzleType: String,
        policyVersion: Int,
        difficulty: String,
        puzzleSeed: Long,
        generatorVersion: Int,
        completedAt: Long,
    ): Int

    @Query("SELECT * FROM daily_runs WHERE challenge_date = :challengeDate LIMIT 1")
    suspend fun findDailyRun(challengeDate: String): DailyRunEntity?

    @Query(
        "SELECT COUNT(*) FROM daily_challenges WHERE challenge_date = :challengeDate " +
            "AND daily_policy_version = :policyVersion AND status != 'COMPLETED'",
    )
    suspend fun countIncompleteDailyChallenges(
        challengeDate: String,
        policyVersion: Int,
    ): Int

    @Query(
        "UPDATE daily_runs SET status = 'COMPLETED', updated_at_epoch_millis = :completedAt, " +
            "completed_at_epoch_millis = :completedAt WHERE challenge_date = :challengeDate " +
            "AND daily_policy_version = :policyVersion AND status = 'IN_PROGRESS'",
    )
    suspend fun completeDailyRun(
        challengeDate: String,
        policyVersion: Int,
        completedAt: Long,
    ): Int

    // The wallet is a singleton row; `PlayerEconomyEntity.SINGLETON_ID` is that ID.
    @Query("SELECT * FROM player_economy WHERE economy_id = 1 LIMIT 1")
    suspend fun findEconomy(): PlayerEconomyEntity?

    @Upsert
    suspend fun upsertEconomy(economy: PlayerEconomyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEconomyEvent(event: EconomyEventEntity): Long

    @Query(
        "INSERT OR IGNORE INTO catalog_level_progress " +
            "(puzzle_type, difficulty, level_pack_version, current_level, updated_at_epoch_millis) " +
            "VALUES (:puzzleType, :difficulty, :packVersion, :nextLevel, :updatedAt)",
    )
    suspend fun insertCatalogProgressIfAbsent(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
        nextLevel: Int,
        updatedAt: Long,
    )

    @Query(
        "UPDATE catalog_level_progress SET current_level = :nextLevel, updated_at_epoch_millis = :updatedAt " +
            "WHERE puzzle_type = :puzzleType AND difficulty = :difficulty " +
            "AND level_pack_version = :packVersion AND current_level < :nextLevel",
    )
    suspend fun advanceCatalogProgress(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
        nextLevel: Int,
        updatedAt: Long,
    ): Int

    /**
     * The one atomic terminal transaction. A solved Catalog level records its durable result, pays
     * its gem reward, and advances that game/difficulty's progression together; a failed attempt
     * records its result and its life penalty and leaves the progression on the same level. Every
     * step is keyed so a repeated callback or a persistence retry changes nothing a second time.
     *
     * It no longer needs a persisted active session: Catalog and Daily attempts are transient.
     */
    @Transaction
    suspend fun complete(result: GameResultEntity): GameResultEntity {
        val existing = findResult(result.resultId)
        if (existing == null) {
            require(insertResult(result) != -1L) { "The completed result could not be inserted." }
        } else {
            require(existing.matchesImmutableFacts(result)) {
                "The result ID already belongs to a different completion."
            }
        }

        // The wallet moves in the very same transaction as the result, keyed by that result, so a
        // crash, a retried save, or a repeated callback can never pay or charge the attempt twice.
        applyResultEconomy(result)

        // Progression is part of the same transaction and is monotonic per bucket: advancing to
        // level+1 only when the stored level is still behind makes a repeated completion a no-op.
        if (
            result.sessionScope == GameSessionScope.CATALOG.name &&
            result.outcome == GameOutcome.SOLVED.name &&
            result.catalogLevelNumber != null &&
            result.catalogLevelPackVersion != null
        ) {
            val nextLevel = result.catalogLevelNumber + 1
            insertCatalogProgressIfAbsent(
                puzzleType = result.puzzleType,
                difficulty = result.difficulty,
                packVersion = result.catalogLevelPackVersion,
                nextLevel = nextLevel,
                updatedAt = result.completedAtEpochMillis,
            )
            advanceCatalogProgress(
                puzzleType = result.puzzleType,
                difficulty = result.difficulty,
                packVersion = result.catalogLevelPackVersion,
                nextLevel = nextLevel,
                updatedAt = result.completedAtEpochMillis,
            )
        }

        // Daily lifecycle now tracks success, not participation: a FAILED attempt still produces a
        // durable result and leaves the entry open for a retry, but leaves the run untouched.
        if (result.sessionScope == GameSessionScope.DAILY.name && result.outcome == GameOutcome.SOLVED.name) {
            val challengeDate = requireNotNull(result.challengeDate)
            val policyVersion = requireNotNull(result.dailyPolicyVersion)
            val run =
                requireNotNull(findDailyRun(challengeDate)) {
                    "The matching Daily run was not found."
                }
            require(run.dailyPolicyVersion == policyVersion) {
                "The Daily run policy version does not match the result."
            }
            val daily =
                requireNotNull(findDailyChallenge(challengeDate, result.puzzleType)) {
                    "The matching Daily lifecycle record was not found."
                }
            require(daily.matches(result)) { "The Daily lifecycle identity does not match the result." }
            require(
                completeDailyChallenge(
                    challengeDate = challengeDate,
                    puzzleType = result.puzzleType,
                    policyVersion = policyVersion,
                    difficulty = result.difficulty,
                    puzzleSeed = result.puzzleSeed,
                    generatorVersion = result.generatorVersion,
                    completedAt = result.completedAtEpochMillis,
                ) == 1,
            ) { "The Daily lifecycle record could not be completed." }
            if (countIncompleteDailyChallenges(challengeDate, policyVersion) == 0) {
                completeDailyRun(challengeDate, policyVersion, result.completedAtEpochMillis)
                require(findDailyRun(challengeDate)?.status == DailyRunStatus.COMPLETED.name) {
                    "The completed Daily run could not be persisted."
                }
            }
        }
        return existing ?: result
    }

    /**
     * One result produces exactly zero or one economy effect. Regeneration that is already due is
     * applied first, so the reward or the penalty always lands on an up-to-date wallet. The gem
     * reward comes from the difficulty the result already carries; the life penalty is flat.
     */
    private suspend fun applyResultEconomy(result: GameResultEntity) {
        val now = result.completedAtEpochMillis
        val current = findEconomy().toPlayerEconomy(now).regenerated(now)
        val effect: EconomyEffect =
            when (result.outcome) {
                GameOutcome.SOLVED.name ->
                    current.solvedReward(result.resultId, Difficulty.valueOf(result.difficulty))
                GameOutcome.FAILED.name -> current.failedPenalty(result.resultId, now)
                else -> return
            }
        // Losing the insert means this result was already processed: it stays a no-op.
        if (insertEconomyEvent(effect.event.toEntity(now)) == -1L) return
        upsertEconomy(effect.economy.toEntity(now))
    }

    private fun DailyChallengeEntity.matches(result: GameResultEntity): Boolean =
        challengeDate == result.challengeDate &&
            puzzleType == result.puzzleType &&
            dailyPolicyVersion == result.dailyPolicyVersion &&
            difficulty == result.difficulty &&
            puzzleSeed == result.puzzleSeed &&
            generatorVersion == result.generatorVersion

    private fun GameResultEntity.matchesImmutableFacts(other: GameResultEntity): Boolean =
        resultId == other.resultId &&
            puzzleType == other.puzzleType &&
            difficulty == other.difficulty &&
            puzzleSeed == other.puzzleSeed &&
            generatorVersion == other.generatorVersion &&
            sessionScope == other.sessionScope &&
            hintsUsed == other.hintsUsed &&
            outcome == other.outcome &&
            attemptsUsed == other.attemptsUsed &&
            catalogLevelNumber == other.catalogLevelNumber &&
            catalogLevelPackVersion == other.catalogLevelPackVersion &&
            challengeDate == other.challengeDate &&
            dailyPolicyVersion == other.dailyPolicyVersion
}
