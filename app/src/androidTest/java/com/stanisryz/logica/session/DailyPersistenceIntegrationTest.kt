package com.stanisryz.logica.session

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.DailyRunStatus
import com.stanisryz.logica.daily.RoomDailyChallengeRepository
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
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
    fun v3MigrationBackfillsV1RunsAndPreservesAtomicCompletion() =
        runBlocking {
            val today = LocalDate.of(2026, 8, 8)
            val definition = DailyChallengePolicyV1.definitionFor(today)
            val entry = definition.entries.single()
            createVersionThreeDatabase(today, entry.seed.value)

            val database = LogicaDatabase.create(context)
            try {
                val sessions = sessionRepository(database)
                val daily = dailyRepository(database)
                val historicalRun = requireNotNull(daily.readRun(today.minusDays(1)))
                val currentRun = requireNotNull(daily.readRun(today))

                assertEquals(DailyRunStatus.COMPLETED, historicalRun.status)
                assertEquals(DailyChallengePolicyV1.VERSION, historicalRun.policyVersion)
                assertEquals(Instant.ofEpochMilli(200), historicalRun.completedAt)
                assertEquals(DailyRunStatus.IN_PROGRESS, currentRun.status)
                assertEquals(DailyChallengePolicyV1.VERSION, currentRun.policyVersion)
                assertNull(currentRun.completedAt)
                assertEquals(
                    1,
                    database
                        .gameResultDao()
                        .observeAll()
                        .first()
                        .size,
                )

                val completion =
                    completion(
                        resultId = DAILY_SESSION_ID,
                        date = today,
                        policyVersion = definition.policyVersion,
                        entry = definition.entries.single(),
                        hintsUsed = DAILY_HINTS,
                    )
                val firstResult = sessions.complete(completion)
                val repeatedResult = sessions.complete(completion)

                assertEquals(firstResult, repeatedResult)
                assertEquals(
                    2,
                    database
                        .gameResultDao()
                        .observeAll()
                        .first()
                        .size,
                )
                assertEquals(DailyChallengeStatus.COMPLETED, daily.read(today, PuzzleType.BALANCE)?.status)
                assertEquals(DailyRunStatus.COMPLETED, daily.readRun(today)?.status)
                assertEquals(Instant.ofEpochMilli(COMPLETED_AT), daily.readRun(today)?.completedAt)
                assertNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.DAILY))
                assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))
            } finally {
                database.close()
            }
        }

    private fun dailyRepository(database: LogicaDatabase): RoomDailyChallengeRepository =
        RoomDailyChallengeRepository(
            dao = database.dailyChallengeDao(),
            runDao = database.dailyRunDao(),
            currentTimeMillis = { CREATED_AT },
        )

    private fun sessionRepository(database: LogicaDatabase): RoomGameSessionRepository =
        RoomGameSessionRepository(
            dao = database.gameSessionDao(),
            completionDao = database.gameCompletionDao(),
            currentTimeMillis = { COMPLETED_AT },
        )

    private fun completion(
        resultId: String,
        date: LocalDate,
        policyVersion: DailyPolicyVersion,
        entry: DailyPuzzleEntry,
        hintsUsed: Int,
    ): GameCompletion =
        GameCompletion(
            resultId = resultId,
            puzzleType = entry.puzzleType,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = hintsUsed,
            challengeDate = date,
            dailyPolicyVersion = policyVersion,
        )

    private fun createVersionThreeDatabase(
        today: LocalDate,
        dailySeed: Long,
    ) {
        context.deleteDatabase(DATABASE_NAME)
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
            connection.execute(CREATE_GAME_SESSIONS)
            connection.execute(CREATE_DAILY_CHALLENGES)
            connection.execute(CREATE_GAME_RESULTS)
            connection.execute(
                """
                INSERT INTO `game_sessions` VALUES
                    ('BALANCE', 'CATALOG', 'catalog', 'EASY', 7, 1, NULL, NULL, 1, 'payload', '', 1,
                        'IN_PROGRESS', 100, 200),
                    ('BALANCE', 'DAILY', '$DAILY_SESSION_ID', 'MEDIUM', $dailySeed, 1, '$today', 1, 1,
                        'payload', '', $DAILY_HINTS, 'IN_PROGRESS', 300, 400)
                """.trimIndent(),
            )
            connection.execute(
                """
                INSERT INTO `daily_challenges` VALUES
                    ('${today.minusDays(1)}', 'BALANCE', 1, 'MEDIUM', 99, 1, 'COMPLETED', 100, 200),
                    ('$today', 'BALANCE', 1, 'MEDIUM', $dailySeed, 1, 'IN_PROGRESS', 300, 400)
                """.trimIndent(),
            )
            connection.execute(
                "INSERT INTO `game_results` VALUES " +
                    "('historical', 'BALANCE', 'EASY', 7, 1, 'CATALOG', 0, 250, NULL, NULL)",
            )
            connection.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '5e69aca102a14d593e4f127ff14f3878')",
            )
            connection.execute("PRAGMA user_version = 3")
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private companion object {
        const val DATABASE_NAME = "logica.db"
        const val DAILY_SESSION_ID = "daily"
        const val DAILY_HINTS = 2
        const val CREATED_AT = 1_000L
        const val COMPLETED_AT = 1_234L

        val CREATE_GAME_SESSIONS =
            """
            CREATE TABLE IF NOT EXISTS `game_sessions` (
                `puzzle_type` TEXT NOT NULL, `session_scope` TEXT NOT NULL, `session_id` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL, `puzzle_seed` INTEGER NOT NULL, `generator_version` INTEGER NOT NULL,
                `challenge_date` TEXT, `daily_policy_version` INTEGER, `session_format_version` INTEGER NOT NULL,
                `gameplay_payload` TEXT NOT NULL, `move_history_payload` TEXT NOT NULL, `hints_used` INTEGER NOT NULL,
                `status` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL,
                `updated_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`puzzle_type`, `session_scope`)
            )
            """.trimIndent()

        val CREATE_DAILY_CHALLENGES =
            """
            CREATE TABLE IF NOT EXISTS `daily_challenges` (
                `challenge_date` TEXT NOT NULL, `puzzle_type` TEXT NOT NULL,
                `daily_policy_version` INTEGER NOT NULL, `difficulty` TEXT NOT NULL,
                `puzzle_seed` INTEGER NOT NULL, `generator_version` INTEGER NOT NULL, `status` TEXT NOT NULL,
                `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL,
                PRIMARY KEY(`challenge_date`, `puzzle_type`)
            )
            """.trimIndent()

        val CREATE_GAME_RESULTS =
            """
            CREATE TABLE IF NOT EXISTS `game_results` (
                `result_id` TEXT NOT NULL, `puzzle_type` TEXT NOT NULL, `difficulty` TEXT NOT NULL,
                `puzzle_seed` INTEGER NOT NULL, `generator_version` INTEGER NOT NULL,
                `session_scope` TEXT NOT NULL, `hints_used` INTEGER NOT NULL,
                `completed_at_epoch_millis` INTEGER NOT NULL, `challenge_date` TEXT,
                `daily_policy_version` INTEGER, PRIMARY KEY(`result_id`)
            )
            """.trimIndent()
    }
}
