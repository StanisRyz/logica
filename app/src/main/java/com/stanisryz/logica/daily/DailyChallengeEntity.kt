package com.stanisryz.logica.daily

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "daily_challenges",
    primaryKeys = ["challenge_date", "puzzle_type"],
)
internal data class DailyChallengeEntity(
    @ColumnInfo(name = "challenge_date")
    val challengeDate: String,
    @ColumnInfo(name = "puzzle_type")
    val puzzleType: String,
    @ColumnInfo(name = "daily_policy_version")
    val dailyPolicyVersion: Int,
    val difficulty: String,
    @ColumnInfo(name = "puzzle_seed")
    val puzzleSeed: Long,
    @ColumnInfo(name = "generator_version")
    val generatorVersion: Int,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
