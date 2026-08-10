package com.stanisryz.logica.economy

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** The singleton wallet row: there is exactly one local player. */
@Entity(tableName = "player_economy")
internal data class PlayerEconomyEntity(
    @PrimaryKey
    @ColumnInfo(name = "economy_id")
    val economyId: Int = SINGLETON_ID,
    val gems: Int,
    val lives: Int,
    @ColumnInfo(name = "next_life_at_epoch_millis")
    val nextLifeAtEpochMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
