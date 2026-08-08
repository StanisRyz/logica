package com.stanisryz.logica.daily

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DailyChallengeDao {
    @Query("SELECT challenge_date FROM daily_challenges WHERE status = 'COMPLETED'")
    fun observeCompletedDates(): Flow<List<String>>

    @Query("SELECT * FROM daily_challenges WHERE challenge_date = :challengeDate AND puzzle_type = :puzzleType LIMIT 1")
    suspend fun find(
        challengeDate: String,
        puzzleType: String,
    ): DailyChallengeEntity?

    @Upsert
    suspend fun upsert(entity: DailyChallengeEntity)

    @Transaction
    suspend fun upsertKeepingCreated(entity: DailyChallengeEntity) {
        val current = find(entity.challengeDate, entity.puzzleType)
        upsert(entity.copy(createdAtEpochMillis = current?.createdAtEpochMillis ?: entity.createdAtEpochMillis))
    }
}
