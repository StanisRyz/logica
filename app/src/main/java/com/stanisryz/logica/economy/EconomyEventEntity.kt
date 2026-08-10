package com.stanisryz.logica.economy

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The append-only economy ledger. The primary key is the derived event ID, so inserting the same
 * event twice conflicts instead of paying twice.
 */
@Entity(tableName = "economy_events")
internal data class EconomyEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String?,
    @ColumnInfo(name = "gem_delta")
    val gemDelta: Int,
    @ColumnInfo(name = "life_delta")
    val lifeDelta: Int,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)
