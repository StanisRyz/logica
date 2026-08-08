package com.stanisryz.logica.session

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stanisryz.logica.daily.DailyChallengeStatus
import com.stanisryz.logica.daily.RoomDailyChallengeRepository
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
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
    fun v2MigrationAndDailyCompletionAreAtomicIdempotentAndScopeIsolated() =
        runBlocking {
            val today = LocalDate.of(2026, 8, 8)
            val definition = DailyChallengePolicyV1.definitionFor(today)
            val entry = definition.entries.single()
            createVersionTwoDatabase(today, entry.seed.value)

            val database = LogicaDatabase.create(context)
            try {
                val sessions =
                    RoomGameSessionRepository(
                        dao = database.gameSessionDao(),
                        completionDao = database.gameCompletionDao(),
                        currentTimeMillis = { COMPLETED_AT },
                    )
                val dailyChallenges = RoomDailyChallengeRepository(database.dailyChallengeDao())

                assertEquals(emptyList<Any>(), database.gameResultDao().observeAll().first())
                assertEquals(
                    DailyChallengeStatus.COMPLETED,
                    dailyChallenges.read(today.minusDays(1), PuzzleType.BALANCE)?.status,
                )

                val completion =
                    GameCompletion(
                        resultId = DAILY_SESSION_ID,
                        puzzleType = entry.puzzleType,
                        difficulty = entry.difficulty,
                        puzzleSeed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        sessionScope = GameSessionScope.DAILY,
                        hintsUsed = DAILY_HINTS,
                        challengeDate = today,
                        dailyPolicyVersion = definition.policyVersion,
                    )
                val firstResult = sessions.complete(completion)
                val repeatedResult = sessions.complete(completion)

                assertEquals(firstResult, repeatedResult)
                assertEquals(COMPLETED_AT, firstResult.completedAt.toEpochMilli())
                val storedResults = database.gameResultDao().observeAll().first()
                assertEquals(1, storedResults.size)
                assertEquals(DAILY_SESSION_ID, storedResults.single().resultId)
                assertEquals(DAILY_HINTS, storedResults.single().hintsUsed)
                assertEquals(
                    DailyChallengeStatus.COMPLETED,
                    dailyChallenges.read(today, PuzzleType.BALANCE)?.status,
                )
                assertNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.DAILY))
                assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))
            } finally {
                database.close()
            }
        }

    private fun createVersionTwoDatabase(
        today: LocalDate,
        dailySeed: Long,
    ) {
        context.deleteDatabase(DATABASE_NAME)
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `game_sessions` (
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
            connection.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, 'f0093435c3e5badd187d9231a5ce44b1')",
            )
            connection.execute("PRAGMA user_version = 2")
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private companion object {
        const val DATABASE_NAME = "logica.db"
        const val DAILY_SESSION_ID = "daily"
        const val DAILY_HINTS = 2
        const val COMPLETED_AT = 1_234L
    }
}
