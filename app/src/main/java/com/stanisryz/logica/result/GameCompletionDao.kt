package com.stanisryz.logica.result

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.stanisryz.logica.daily.DailyChallengeEntity
import com.stanisryz.logica.daily.DailyRunEntity
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.session.GameSessionEntity
import com.stanisryz.logica.session.GameSessionScope

@Dao
internal interface GameCompletionDao {
    @Query("SELECT * FROM game_results WHERE result_id = :resultId LIMIT 1")
    suspend fun findResult(resultId: String): GameResultEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResult(result: GameResultEntity): Long

    @Query("SELECT * FROM game_sessions WHERE puzzle_type = :puzzleType AND session_scope = :sessionScope LIMIT 1")
    suspend fun findSession(
        puzzleType: String,
        sessionScope: String,
    ): GameSessionEntity?

    @Query(
        "DELETE FROM game_sessions " +
            "WHERE puzzle_type = :puzzleType AND session_scope = :sessionScope AND session_id = :sessionId",
    )
    suspend fun deleteSession(
        puzzleType: String,
        sessionScope: String,
        sessionId: String,
    ): Int

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

    @Transaction
    suspend fun complete(result: GameResultEntity): GameResultEntity {
        val existing = findResult(result.resultId)
        if (existing == null) {
            val session =
                requireNotNull(findSession(result.puzzleType, result.sessionScope)) {
                    "The active session to complete was not found."
                }
            require(session.matches(result)) { "The active session does not match the completed result." }
            require(insertResult(result) != -1L) { "The completed result could not be inserted." }
        } else {
            require(existing.matchesImmutableFacts(result)) {
                "The result ID already belongs to a different completion."
            }
        }

        if (result.sessionScope == GameSessionScope.DAILY.name) {
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

        val deleted = deleteSession(result.puzzleType, result.sessionScope, result.resultId)
        require(existing != null || deleted == 1) { "The active session could not be removed." }
        return existing ?: result
    }

    private fun GameSessionEntity.matches(result: GameResultEntity): Boolean =
        sessionId == result.resultId &&
            puzzleType == result.puzzleType &&
            sessionScope == result.sessionScope &&
            difficulty == result.difficulty &&
            puzzleSeed == result.puzzleSeed &&
            generatorVersion == result.generatorVersion &&
            hintsUsed == result.hintsUsed &&
            challengeDate == result.challengeDate &&
            dailyPolicyVersion == result.dailyPolicyVersion

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
            challengeDate == other.challengeDate &&
            dailyPolicyVersion == other.dailyPolicyVersion
}
