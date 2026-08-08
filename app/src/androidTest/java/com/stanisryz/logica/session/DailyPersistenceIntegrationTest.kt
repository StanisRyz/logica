package com.stanisryz.logica.session

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.RoomDailyChallengeRepository
import com.stanisryz.logica.daily.SavedDailyChallenge
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DailyPersistenceIntegrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationMakesOldSaveCatalogAndAllowsDailyAlongsideIt() =
        runBlocking {
            createVersionOneDatabase()
            val database = LogicaDatabase.create(context)
            try {
                val dao = database.gameSessionDao()
                val migrated = dao.find(PuzzleType.BALANCE.name, GameSessionScope.CATALOG.name)

                assertNotNull(migrated)
                assertEquals("old-catalog", migrated?.sessionId)
                assertNull(migrated?.challengeDate)
                assertNull(migrated?.dailyPolicyVersion)

                dao.upsert(dailyEntity())

                assertEquals("old-catalog", dao.find("BALANCE", "CATALOG")?.sessionId)
                assertEquals("today-daily", dao.find("BALANCE", "DAILY")?.sessionId)
            } finally {
                database.close()
            }
        }

    @Test
    fun dailyLifecycleCompletesWithoutTouchingCatalogSession() =
        runBlocking {
            val database = LogicaDatabase.create(context)
            try {
                val gameSessions = RoomGameSessionRepository(database.gameSessionDao())
                val dailyChallenges = RoomDailyChallengeRepository(database.dailyChallengeDao())
                val definition = DailyChallengePolicyV1.definitionFor(LocalDate.of(2026, 8, 8))
                val entry = definition.entries.single()
                val catalog = savedSession("catalog", GameSessionScope.CATALOG)
                val daily =
                    savedSession(
                        sessionId = "daily",
                        scope = GameSessionScope.DAILY,
                        dailyIdentity =
                            DailyGameSessionIdentity(
                                definition.challengeDate,
                                definition.policyVersion.value,
                            ),
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                    )

                assertNull(dailyChallenges.read(definition.challengeDate, entry.puzzleType))
                gameSessions.replaceActiveSession(catalog)
                dailyChallenges.save(entry.lifecycle(definition.challengeDate, DailyChallengeStatus.IN_PROGRESS))
                gameSessions.replaceActiveSession(daily)

                assertEquals(
                    DailyChallengeStatus.IN_PROGRESS,
                    dailyChallenges.read(definition.challengeDate, entry.puzzleType)?.status,
                )
                assertNotNull(gameSessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.DAILY))

                gameSessions.deleteActiveSession(PuzzleType.BALANCE, GameSessionScope.DAILY, daily.sessionId)
                dailyChallenges.save(entry.lifecycle(definition.challengeDate, DailyChallengeStatus.COMPLETED))

                assertNull(gameSessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.DAILY))
                assertEquals(
                    DailyChallengeStatus.COMPLETED,
                    dailyChallenges.read(definition.challengeDate, entry.puzzleType)?.status,
                )
                assertEquals(
                    catalog,
                    gameSessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG),
                )
            } finally {
                database.close()
            }
        }

    private fun createVersionOneDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `game_sessions` (
                    `puzzle_type` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `difficulty` TEXT NOT NULL,
                    `puzzle_seed` INTEGER NOT NULL,
                    `generator_version` INTEGER NOT NULL,
                    `session_format_version` INTEGER NOT NULL,
                    `gameplay_payload` TEXT NOT NULL,
                    `move_history_payload` TEXT NOT NULL,
                    `hints_used` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`puzzle_type`)
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                INSERT INTO `game_sessions` VALUES (
                    'BALANCE', 'old-catalog', 'EASY', 7, 1, 1, 'payload', '', 0,
                    'IN_PROGRESS', 100, 200
                )
                """.trimIndent(),
            )
            connection.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, 'e5b0c0fe58b4b35a79984775121fd8e5')",
            )
            connection.execute("PRAGMA user_version = 1")
        }
    }

    private fun dailyEntity(): GameSessionEntity =
        GameSessionEntity(
            puzzleType = "BALANCE",
            sessionScope = "DAILY",
            sessionId = "today-daily",
            difficulty = "MEDIUM",
            puzzleSeed = 8,
            generatorVersion = 1,
            challengeDate = "2026-08-08",
            dailyPolicyVersion = 1,
            sessionFormatVersion = 1,
            gameplayPayload = "payload",
            moveHistoryPayload = "",
            hintsUsed = 0,
            status = "IN_PROGRESS",
            createdAtEpochMillis = 300,
            updatedAtEpochMillis = 300,
        )

    private fun savedSession(
        sessionId: String,
        scope: GameSessionScope,
        dailyIdentity: DailyGameSessionIdentity? = null,
        difficulty: Difficulty = Difficulty.EASY,
        seed: PuzzleSeed = PuzzleSeed(7),
    ): SavedGameSession =
        SavedGameSession(
            sessionId = sessionId,
            puzzleType = PuzzleType.BALANCE,
            sessionScope = scope,
            difficulty = difficulty,
            puzzleSeed = seed,
            generatorVersion = GeneratorVersion(1),
            dailyIdentity = dailyIdentity,
            sessionFormatVersion = 1,
            gameplayPayload = "payload",
            moveHistoryPayload = "",
            hintsUsed = 0,
            status = "IN_PROGRESS",
        )

    private fun com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry.lifecycle(
        date: LocalDate,
        status: DailyChallengeStatus,
    ): SavedDailyChallenge =
        SavedDailyChallenge(
            challengeDate = date,
            puzzleType = puzzleType,
            policyVersion = DailyChallengePolicyV1.VERSION,
            difficulty = difficulty,
            seed = seed,
            generatorVersion = generatorVersion,
            status = status,
        )

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private companion object {
        const val DATABASE_NAME = "logica.db"
    }
}
