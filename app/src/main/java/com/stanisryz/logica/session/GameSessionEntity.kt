package com.stanisryz.logica.session

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "game_sessions",
    primaryKeys = ["puzzle_type", "session_scope"],
)
internal data class GameSessionEntity(
    @ColumnInfo(name = "puzzle_type")
    val puzzleType: String,
    @ColumnInfo(name = "session_scope")
    val sessionScope: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val difficulty: String,
    @ColumnInfo(name = "puzzle_seed")
    val puzzleSeed: Long,
    @ColumnInfo(name = "generator_version")
    val generatorVersion: Int,
    @ColumnInfo(name = "challenge_date")
    val challengeDate: String?,
    @ColumnInfo(name = "daily_policy_version")
    val dailyPolicyVersion: Int?,
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
