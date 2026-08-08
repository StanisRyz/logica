package com.stanisryz.logica.result

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface GameResultDao {
    @Query("SELECT * FROM game_results ORDER BY completed_at_epoch_millis DESC")
    fun observeAll(): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM game_results WHERE result_id = :resultId LIMIT 1")
    suspend fun find(resultId: String): GameResultEntity?
}
