package com.stanisryz.logica.session

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "game_sessions")
internal data class GameSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "puzzle_type")
    val puzzleType: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val difficulty: String,
    @ColumnInfo(name = "puzzle_seed")
    val puzzleSeed: Long,
    @ColumnInfo(name = "generator_version")
    val generatorVersion: Int,
    @ColumnInfo(name = "session_format_version")
    val sessionFormatVersion: Int,
    @ColumnInfo(name = "gameplay_payload")
    val gameplayPayload: String,
    @ColumnInfo(name = "move_history_payload")
    val moveHistoryPayload: String,
    @ColumnInfo(name = "hints_used")
    val hintsUsed: Int,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
