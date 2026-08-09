package com.stanisryz.logica.daily

import androidx.room3.Dao
import androidx.room3.Query

@Dao
internal interface DailyChallengeDao {
    @Query("SELECT * FROM daily_challenges WHERE challenge_date = :challengeDate AND puzzle_type = :puzzleType LIMIT 1")
    suspend fun find(
        challengeDate: String,
        puzzleType: String,
    ): DailyChallengeEntity?
}
