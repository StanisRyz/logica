package com.stanisryz.logica.result

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "game_results")
internal data class GameResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "result_id")
    val resultId: String,
    @ColumnInfo(name = "puzzle_type")
    val puzzleType: String,
    val difficulty: String,
    @ColumnInfo(name = "puzzle_seed")
    val puzzleSeed: Long,
    @ColumnInfo(name = "generator_version")
    val generatorVersion: Int,
    @ColumnInfo(name = "session_scope")
    val resultScope: String,
    @ColumnInfo(name = "hints_used")
    val hintsUsed: Int,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long,
    @ColumnInfo(name = "outcome", defaultValue = "SOLVED")
    val outcome: String,
    @ColumnInfo(name = "attempts_used")
    val attemptsUsed: Int?,
    @ColumnInfo(name = "challenge_date")
    val challengeDate: String?,
    @ColumnInfo(name = "daily_policy_version")
    val dailyPolicyVersion: Int?,
    /**
     * The public Catalog level this result belongs to. Both columns are nullable: Daily results never
     * carry them, and historical results recorded before the frozen level system have no level.
     */
    @ColumnInfo(name = "catalog_level_number")
    val catalogLevelNumber: Int? = null,
    @ColumnInfo(name = "catalog_level_pack_version")
    val catalogLevelPackVersion: Int? = null,
)
