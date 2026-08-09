package com.stanisryz.logica.daily

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "daily_runs")
internal data class DailyRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "challenge_date")
    val challengeDate: String,
    @ColumnInfo(name = "daily_policy_version")
    val dailyPolicyVersion: Int,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long?,
)
