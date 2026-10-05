package com.stanisryz.logica.session

import androidx.room3.RoomOpenDelegate
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.stanisryz.logica.economy.EconomyRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room migrations on the host JVM, against the exported schemas in `app/schemas`: every step lands
 * exactly on the next exported schema, the whole chain keeps the players' data and fills new columns
 * as documented, and the result is what the current `LogicaDatabase` itself accepts on open.
 */
class LogicaDatabaseMigrationTest {
    private val file = File.createTempFile("logica-migration", ".db").also { it.delete() }
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(file.absolutePath)

    @After
    fun tearDown() {
        connection.close()
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    @Test
    fun theMigrationsCoverEveryVersionInOrder() {
        assertEquals((1 until LATEST).map { it to it + 1 }, MIGRATIONS.map { it.startVersion to it.endVersion })
        assertEquals(LATEST, schema(LATEST).version)
    }

    @Test
    fun eachMigrationLandsOnTheNextExportedSchema() =
        runBlocking {
            for (version in 1 until LATEST) {
                val step = File.createTempFile("logica-step-$version", ".db").also { it.delete() }
                val stepConnection = BundledSQLiteDriver().open(step.absolutePath)
                try {
                    stepConnection.create(schema(version))
                    MIGRATIONS[version - 1].migrate(stepConnection)
                    assertEquals("schema after $version -> ${version + 1}", schema(version + 1).tables, stepConnection.tables())
                } finally {
                    stepConnection.close()
                    step.delete()
                }
            }
        }

    @Test
    fun theWholeChainKeepsThePlayersDataAndFillsNewColumns() =
        runBlocking {
            connection.create(schema(1))
            connection.execSQL(
                "INSERT INTO game_sessions VALUES ('BALANCE', 's1', 'EASY', 11, 1, 1, '{}', '[]', 0, 'IN_PROGRESS', 1, 2)",
            )
            for (migration in MIGRATIONS) {
                migration.migrate(connection)
                // Each table gets its rows as soon as the version that introduces it exists.
                when (migration.endVersion) {
                    2 ->
                        connection.execSQL(
                            "INSERT INTO daily_challenges VALUES " +
                                "('2026-01-01', 'BALANCE', 1, 'MEDIUM', 5, 1, 'COMPLETED', 10, 20)",
                        )
                    3 ->
                        connection.execSQL(
                            "INSERT INTO game_results (result_id, puzzle_type, difficulty, puzzle_seed, generator_version, " +
                                "session_scope, hints_used, completed_at_epoch_millis, challenge_date, daily_policy_version) " +
                                "VALUES ('r1', 'BALANCE', 'MEDIUM', 5, 1, 'DAILY', 2, 30, '2026-01-01', 1)",
                        )
                    6 ->
                        connection.execSQL(
                            "INSERT INTO economy_events (event_id, event_type, source_id, gem_delta, life_delta, " +
                                "created_at_epoch_millis) VALUES ('result:r2', 'PUZZLE_REWARD', 'r2', 1, 0, 40)",
                        )
                    7 ->
                        connection.execSQL(
                            "INSERT INTO catalog_level_progress VALUES ('SUDOKU', 'HARD', 1, 12, 50)",
                        )
                }
            }

            // 1 -> 2 moved the old session into CATALOG scope; 6 -> 7 cleared sessions and 7 -> 8 dropped the table.
            assertTrue("game_sessions" !in connection.tables())
            // 3 -> 4 derived a completed run from the completed Daily entry.
            assertEquals(listOf("2026-01-01|1|COMPLETED|10|20|20"), connection.rows("SELECT * FROM daily_runs"))
            assertEquals(
                listOf("2026-01-01|BALANCE|1|MEDIUM|5|1|COMPLETED|10|20"),
                connection.rows("SELECT * FROM daily_challenges"),
            )
            // 4 -> 5 backfilled SOLVED without an attempt count; 6 -> 7 and 9 -> 10 left level and stars empty.
            assertEquals(
                listOf("r1|BALANCE|MEDIUM|5|1|DAILY|2|30|SOLVED|null|2026-01-01|1|null|null|null"),
                connection.rows(
                    "SELECT result_id, puzzle_type, difficulty, puzzle_seed, generator_version, session_scope, hints_used, " +
                        "completed_at_epoch_millis, outcome, attempts_used, challenge_date, daily_policy_version, " +
                        "catalog_level_number, catalog_level_pack_version, stars FROM game_results",
                ),
            )
            // 5 -> 6 seeded the wallet at 0 gems and 5 lives; 8 -> 9 gave it the starting hints.
            assertEquals(
                listOf("1|0|${EconomyRules.STARTING_LIVES}|null|${EconomyRules.STARTING_HINTS}"),
                connection.rows("SELECT economy_id, gems, lives, next_life_at_epoch_millis, hints FROM player_economy"),
            )
            assertEquals(
                listOf("result:r2|PUZZLE_REWARD|r2|1|0|40|0"),
                connection.rows(
                    "SELECT event_id, event_type, source_id, gem_delta, life_delta, created_at_epoch_millis, hint_delta " +
                        "FROM economy_events",
                ),
            )
            assertEquals(listOf("SUDOKU|HARD|1|12|50"), connection.rows("SELECT * FROM catalog_level_progress"))
            assertEquals(schema(LATEST).tables, connection.tables())
        }

    @Test
    fun theCurrentDatabaseAcceptsAFullyMigratedDatabase() =
        runBlocking {
            connection.create(schema(1))
            MIGRATIONS.forEach { it.migrate(connection) }
            // The same check Room runs when it opens a migrated database: no destructive fallback exists.
            val delegate =
                LogicaDatabase_Impl::class.java
                    .getDeclaredMethod("createOpenDelegate")
                    .apply { isAccessible = true }
                    .invoke(LogicaDatabase_Impl()) as RoomOpenDelegate
            val result = delegate.onValidateSchema(connection)
            assertTrue(result.expectedFoundMsg.orEmpty(), result.isValid)
            assertNull(result.expectedFoundMsg)
        }

    /** One exported schema: its version, creation SQL, and the columns Room expects per table. */
    private class Schema(
        val version: Int,
        val createSql: List<String>,
        val tables: Map<String, List<String>>,
    )

    private fun schema(version: Int): Schema {
        val database = Json.parse(schemaFile(version).readText()).obj("database")
        val entities = database.list("entities").map { it as Map<*, *> }
        val createSql =
            entities.map { (it["createSql"] as String).replace("\${TABLE_NAME}", it["tableName"] as String) } +
                database.list("setupQueries").map { it as String }
        val tables =
            entities.associate { entity ->
                val primaryKey = (entity["primaryKey"] as Map<*, *>).list("columnNames")
                entity["tableName"] as String to
                    entity
                        .list("fields")
                        .map { it as Map<*, *> }
                        .map { field ->
                            val name = field["columnName"] as String
                            column(
                                name = name,
                                type = field["affinity"] as String,
                                notNull = field["notNull"] == true,
                                default = field["defaultValue"] as String?,
                                primaryKeyPosition = primaryKey.indexOf(name) + 1,
                            )
                        }.sorted()
            }
        return Schema(version = (database["version"] as Number).toInt(), createSql = createSql, tables = tables)
    }

    private fun schemaFile(version: Int): File =
        listOf(File("schemas"), File("app/schemas"))
            .map { File(it, "com.stanisryz.logica.session.LogicaDatabase/$version.json") }
            .first { it.isFile }

    private fun SQLiteConnection.create(schema: Schema) {
        schema.createSql.forEach(::execSQL)
        execSQL("PRAGMA user_version = ${schema.version}")
    }

    /** The application tables as SQLite now has them, in the same shape as [Schema.tables]. */
    private fun SQLiteConnection.tables(): Map<String, List<String>> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'")
            .associateWith { table ->
                prepare("PRAGMA table_info(`$table`)").use { statement ->
                    buildList {
                        while (statement.step()) {
                            add(
                                column(
                                    name = statement.getText(1),
                                    type = statement.getText(2).uppercase(),
                                    notNull = statement.getLong(3) != 0L,
                                    default = if (statement.isNull(4)) null else statement.getText(4),
                                    primaryKeyPosition = statement.getLong(5).toInt(),
                                ),
                            )
                        }
                    }.sorted()
                }
            }

    private fun SQLiteConnection.rows(sql: String): List<String> =
        prepare(sql).use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        (0 until statement.getColumnCount()).joinToString("|") { index ->
                            if (statement.isNull(index)) "null" else statement.getText(index)
                        },
                    )
                }
            }
        }

    private fun column(
        name: String,
        type: String,
        notNull: Boolean,
        default: String?,
        primaryKeyPosition: Int,
    ): String = "$name $type notNull=$notNull default=$default pk=$primaryKeyPosition"

    private fun Map<*, *>.obj(key: String): Map<*, *> = this[key] as Map<*, *>

    private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any>()

    /** Just enough JSON for Room's schema files; the unit-test classpath has no JSON library. */
    private object Json {
        fun parse(text: String): Map<*, *> = Reader(text).value() as Map<*, *>

        private class Reader(
            private val text: String,
        ) {
            private var index = 0

            fun value(): Any? {
                skipSpace()
                return when (val char = text[index]) {
                    '{' -> obj()
                    '[' -> array()
                    '"' -> string()
                    't' -> literal("true", true)
                    'f' -> literal("false", false)
                    'n' -> literal("null", null)
                    else -> if (char == '-' || char.isDigit()) number() else error("Unexpected '$char' at $index")
                }
            }

            private fun obj(): Map<String, Any?> {
                val result = linkedMapOf<String, Any?>()
                index++
                skipSpace()
                if (text[index] == '}') return result.also { index++ }
                while (true) {
                    skipSpace()
                    val key = string()
                    skipSpace()
                    check(text[index++] == ':')
                    result[key] = value()
                    skipSpace()
                    if (text[index++] == '}') return result
                }
            }

            private fun array(): List<Any?> {
                val result = mutableListOf<Any?>()
                index++
                skipSpace()
                if (text[index] == ']') return result.also { index++ }
                while (true) {
                    result += value()
                    skipSpace()
                    if (text[index++] == ']') return result
                }
            }

            private fun string(): String {
                check(text[index++] == '"')
                val result = StringBuilder()
                while (true) {
                    when (val char = text[index++]) {
                        '"' -> return result.toString()
                        '\\' ->
                            when (val escaped = text[index++]) {
                                'n' -> result.append('\n')
                                't' -> result.append('\t')
                                'u' -> result.append(text.substring(index, index + 4).toInt(16).toChar()).also { index += 4 }
                                else -> result.append(escaped)
                            }
                        else -> result.append(char)
                    }
                }
            }

            private fun number(): Number {
                val start = index
                while (index < text.length && (text[index] == '-' || text[index] == '.' || text[index].isLetterOrDigit())) index++
                return text.substring(start, index).toDouble()
            }

            private fun literal(
                word: String,
                value: Any?,
            ): Any? {
                check(text.startsWith(word, index))
                index += word.length
                return value
            }

            private fun skipSpace() {
                while (index < text.length && text[index].isWhitespace()) index++
            }
        }
    }

    private companion object {
        const val LATEST = 10

        val MIGRATIONS: List<Migration> =
            with(LogicaDatabase) {
                listOf(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
            }
    }
}
