package com.stanisryz.logica.session

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.stanisryz.logica.catalog.CatalogLevelProgressDao
import com.stanisryz.logica.catalog.CatalogLevelProgressEntity
import com.stanisryz.logica.daily.DailyChallengeDao
import com.stanisryz.logica.daily.DailyChallengeEntity
import com.stanisryz.logica.daily.DailyRunDao
import com.stanisryz.logica.daily.DailyRunEntity
import com.stanisryz.logica.economy.EconomyDao
import com.stanisryz.logica.economy.EconomyEventEntity
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.PlayerEconomyEntity
import com.stanisryz.logica.result.GameCompletionDao
import com.stanisryz.logica.result.GameResultDao
import com.stanisryz.logica.result.GameResultEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        GameSessionEntity::class,
        DailyChallengeEntity::class,
        DailyRunEntity::class,
        GameResultEntity::class,
        PlayerEconomyEntity::class,
        EconomyEventEntity::class,
        CatalogLevelProgressEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
internal abstract class LogicaDatabase : RoomDatabase() {
    /**
     * Legacy active-gameplay storage. Nothing writes or reads it any more — Catalog and Daily
     * attempts are transient — and Stage 42.1 removes it after real-device validation.
     */
    abstract fun gameSessionDao(): GameSessionDao

    abstract fun dailyChallengeDao(): DailyChallengeDao

    abstract fun dailyRunDao(): DailyRunDao

    abstract fun gameResultDao(): GameResultDao

    abstract fun gameCompletionDao(): GameCompletionDao

    abstract fun economyDao(): EconomyDao

    abstract fun catalogLevelProgressDao(): CatalogLevelProgressDao

    companion object {
        private const val DATABASE_NAME = "logica.db"

        fun create(context: Context): LogicaDatabase {
            val applicationContext = context.applicationContext
            return Room
                .databaseBuilder<LogicaDatabase>(
                    context = applicationContext,
                    name = applicationContext.getDatabasePath(DATABASE_NAME).absolutePath,
                ).setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
        }

        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `game_sessions_new` (
                            `puzzle_type` TEXT NOT NULL,
                            `session_scope` TEXT NOT NULL,
                            `session_id` TEXT NOT NULL,
                            `difficulty` TEXT NOT NULL,
                            `puzzle_seed` INTEGER NOT NULL,
                            `generator_version` INTEGER NOT NULL,
                            `challenge_date` TEXT,
                            `daily_policy_version` INTEGER,
                            `session_format_version` INTEGER NOT NULL,
                            `gameplay_payload` TEXT NOT NULL,
                            `move_history_payload` TEXT NOT NULL,
                            `hints_used` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `created_at_epoch_millis` INTEGER NOT NULL,
                            `updated_at_epoch_millis` INTEGER NOT NULL,
                            PRIMARY KEY(`puzzle_type`, `session_scope`)
                        )
                        """.trimIndent(),
                    )
                    connection.execute(
                        """
                        INSERT INTO `game_sessions_new` (
                            `puzzle_type`, `session_scope`, `session_id`, `difficulty`, `puzzle_seed`,
                            `generator_version`, `challenge_date`, `daily_policy_version`,
                            `session_format_version`, `gameplay_payload`, `move_history_payload`,
                            `hints_used`, `status`, `created_at_epoch_millis`, `updated_at_epoch_millis`
                        )
                        SELECT
                            `puzzle_type`, 'CATALOG', `session_id`, `difficulty`, `puzzle_seed`,
                            `generator_version`, NULL, NULL, `session_format_version`, `gameplay_payload`,
                            `move_history_payload`, `hints_used`, `status`, `created_at_epoch_millis`,
                            `updated_at_epoch_millis`
                        FROM `game_sessions`
                        """.trimIndent(),
                    )
                    connection.execute("DROP TABLE `game_sessions`")
                    connection.execute("ALTER TABLE `game_sessions_new` RENAME TO `game_sessions`")
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `daily_challenges` (
                            `challenge_date` TEXT NOT NULL,
                            `puzzle_type` TEXT NOT NULL,
                            `daily_policy_version` INTEGER NOT NULL,
                            `difficulty` TEXT NOT NULL,
                            `puzzle_seed` INTEGER NOT NULL,
                            `generator_version` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `created_at_epoch_millis` INTEGER NOT NULL,
                            `updated_at_epoch_millis` INTEGER NOT NULL,
                            PRIMARY KEY(`challenge_date`, `puzzle_type`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `game_results` (
                            `result_id` TEXT NOT NULL,
                            `puzzle_type` TEXT NOT NULL,
                            `difficulty` TEXT NOT NULL,
                            `puzzle_seed` INTEGER NOT NULL,
                            `generator_version` INTEGER NOT NULL,
                            `session_scope` TEXT NOT NULL,
                            `hints_used` INTEGER NOT NULL,
                            `completed_at_epoch_millis` INTEGER NOT NULL,
                            `challenge_date` TEXT,
                            `daily_policy_version` INTEGER,
                            PRIMARY KEY(`result_id`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `daily_runs` (
                            `challenge_date` TEXT NOT NULL,
                            `daily_policy_version` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `created_at_epoch_millis` INTEGER NOT NULL,
                            `updated_at_epoch_millis` INTEGER NOT NULL,
                            `completed_at_epoch_millis` INTEGER,
                            PRIMARY KEY(`challenge_date`)
                        )
                        """.trimIndent(),
                    )
                    connection.execute(
                        """
                        INSERT INTO `daily_runs` (
                            `challenge_date`, `daily_policy_version`, `status`,
                            `created_at_epoch_millis`, `updated_at_epoch_millis`,
                            `completed_at_epoch_millis`
                        )
                        SELECT
                            `challenge_date`, `daily_policy_version`,
                            CASE
                                WHEN SUM(CASE WHEN `status` != 'COMPLETED' THEN 1 ELSE 0 END) = 0
                                THEN 'COMPLETED'
                                ELSE 'IN_PROGRESS'
                            END,
                            MIN(`created_at_epoch_millis`), MAX(`updated_at_epoch_millis`),
                            CASE
                                WHEN SUM(CASE WHEN `status` != 'COMPLETED' THEN 1 ELSE 0 END) = 0
                                THEN MAX(`updated_at_epoch_millis`)
                                ELSE NULL
                            END
                        FROM `daily_challenges`
                        GROUP BY `challenge_date`, `daily_policy_version`
                        """.trimIndent(),
                    )
                }
            }

        /**
         * Adds typed terminal-outcome metadata to results. Every historical Balance/Crowns result was
         * only ever recorded on a solve, so it backfills to SOLVED with no attempt count.
         */
        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        "ALTER TABLE `game_results` ADD COLUMN `outcome` TEXT NOT NULL DEFAULT 'SOLVED'",
                    )
                    connection.execute("ALTER TABLE `game_results` ADD COLUMN `attempts_used` INTEGER")
                }
            }

        /**
         * Adds the player wallet and its economy ledger. Every existing table and record is left
         * untouched, and the wallet starts at the configured beginning of the economy: historical
         * SOLVED and FAILED results are never replayed, so the economy only reacts to attempts
         * finished after this migration.
         */
        internal val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `player_economy` (
                            `economy_id` INTEGER NOT NULL,
                            `gems` INTEGER NOT NULL,
                            `lives` INTEGER NOT NULL,
                            `next_life_at_epoch_millis` INTEGER,
                            `updated_at_epoch_millis` INTEGER NOT NULL,
                            PRIMARY KEY(`economy_id`)
                        )
                        """.trimIndent(),
                    )
                    connection.execute(
                        """
                        INSERT OR IGNORE INTO `player_economy` (
                            `economy_id`, `gems`, `lives`, `next_life_at_epoch_millis`, `updated_at_epoch_millis`
                        ) VALUES (
                            ${PlayerEconomyEntity.SINGLETON_ID}, ${EconomyRules.STARTING_GEMS},
                            ${EconomyRules.STARTING_LIVES}, NULL,
                            CAST(strftime('%s', 'now') AS INTEGER) * 1000
                        )
                        """.trimIndent(),
                    )
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `economy_events` (
                            `event_id` TEXT NOT NULL,
                            `event_type` TEXT NOT NULL,
                            `source_id` TEXT,
                            `gem_delta` INTEGER NOT NULL,
                            `life_delta` INTEGER NOT NULL,
                            `created_at_epoch_millis` INTEGER NOT NULL,
                            PRIMARY KEY(`event_id`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * Adds Catalog level progression and the nullable level metadata durable results carry.
         * Economy, purchases, settings, Daily history, and historical results are preserved untouched;
         * old results simply have no Catalog level. Unfinished saved sessions are cleared because a
         * randomly seeded save cannot be mapped honestly onto a public frozen level number, and
         * gameplay attempts are transient from this version on.
         */
        internal val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execute(
                        """
                        CREATE TABLE IF NOT EXISTS `catalog_level_progress` (
                            `puzzle_type` TEXT NOT NULL,
                            `difficulty` TEXT NOT NULL,
                            `level_pack_version` INTEGER NOT NULL,
                            `current_level` INTEGER NOT NULL,
                            `updated_at_epoch_millis` INTEGER NOT NULL,
                            PRIMARY KEY(`puzzle_type`, `difficulty`, `level_pack_version`)
                        )
                        """.trimIndent(),
                    )
                    connection.execute("ALTER TABLE `game_results` ADD COLUMN `catalog_level_number` INTEGER")
                    connection.execute("ALTER TABLE `game_results` ADD COLUMN `catalog_level_pack_version` INTEGER")
                    connection.execute("DELETE FROM `game_sessions`")
                }
            }

        private fun SQLiteConnection.execute(sql: String) {
            prepare(sql).use { statement -> statement.step() }
        }
    }
}
