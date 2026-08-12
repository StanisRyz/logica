package com.stanisryz.logica.catalog

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * Where the player currently stands in one game/difficulty of one frozen level pack. Progression is
 * compact on purpose: one row per bucket holding the next level to play, never a row per completed
 * level. A missing row means the bucket has never been played and starts at level 1.
 */
@Entity(
    tableName = "catalog_level_progress",
    primaryKeys = ["puzzle_type", "difficulty", "level_pack_version"],
)
internal data class CatalogLevelProgressEntity(
    @ColumnInfo(name = "puzzle_type")
    val puzzleType: String,
    val difficulty: String,
    @ColumnInfo(name = "level_pack_version")
    val levelPackVersion: Int,
    @ColumnInfo(name = "current_level")
    val currentLevel: Int,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Dao
internal interface CatalogLevelProgressDao {
    @Query(
        "SELECT current_level FROM catalog_level_progress " +
            "WHERE puzzle_type = :puzzleType AND difficulty = :difficulty AND level_pack_version = :packVersion",
    )
    fun observeCurrentLevel(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
    ): Flow<List<Int>>

    @Query(
        "SELECT current_level FROM catalog_level_progress " +
            "WHERE puzzle_type = :puzzleType AND difficulty = :difficulty AND level_pack_version = :packVersion " +
            "LIMIT 1",
    )
    suspend fun findCurrentLevel(
        puzzleType: String,
        difficulty: String,
        packVersion: Int,
    ): Int?
}
