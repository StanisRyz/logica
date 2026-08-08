package com.stanisryz.logica.session

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface GameSessionDao {
    @Query("SELECT * FROM game_sessions WHERE puzzle_type = :puzzleType LIMIT 1")
    suspend fun find(puzzleType: String): GameSessionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM game_sessions WHERE puzzle_type = :puzzleType)")
    fun observeExists(puzzleType: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: GameSessionEntity)

    @Query("DELETE FROM game_sessions WHERE puzzle_type = :puzzleType AND session_id = :sessionId")
    suspend fun deleteIfCurrent(
        puzzleType: String,
        sessionId: String,
    ): Int

    @Query("DELETE FROM game_sessions WHERE puzzle_type = :puzzleType")
    suspend fun delete(puzzleType: String)

    @Transaction
    suspend fun updateIfCurrent(
        entity: GameSessionEntity,
        updatedAtEpochMillis: Long,
    ): Boolean {
        val current = find(entity.puzzleType)
        if (current?.sessionId != entity.sessionId) return false
        upsert(
            entity.copy(
                createdAtEpochMillis = current.createdAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
        return true
    }
}
