package com.stanisryz.logica.result

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.stanisryz.logica.daily.DailyChallengeEntity
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
