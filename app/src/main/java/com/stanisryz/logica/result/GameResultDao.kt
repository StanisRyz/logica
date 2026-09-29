package com.stanisryz.logica.result

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface GameResultDao {
    @Query("SELECT * FROM game_results ORDER BY completed_at_epoch_millis DESC")
    fun observeAll(): Flow<List<GameResultEntity>>

    /** Results completed in `[fromEpochMillis, untilEpochMillis)`, such as one local day's. */
    @Query(
        "SELECT * FROM game_results WHERE completed_at_epoch_millis >= :fromEpochMillis " +
            "AND completed_at_epoch_millis < :untilEpochMillis",
    )
    fun observeCompletedBetween(
        fromEpochMillis: Long,
        untilEpochMillis: Long,
    ): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM game_results WHERE result_id = :resultId LIMIT 1")
    suspend fun find(resultId: String): GameResultEntity?

    @Query(
        "SELECT * FROM game_results WHERE session_scope = 'DAILY' " +
            "AND challenge_date = :challengeDate AND daily_policy_version = :dailyPolicyVersion",
    )
    suspend fun findDailyResults(
        challengeDate: String,
        dailyPolicyVersion: Int,
    ): List<GameResultEntity>
}
