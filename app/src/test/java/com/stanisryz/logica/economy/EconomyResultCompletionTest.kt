package com.stanisryz.logica.economy

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResultScope
import com.stanisryz.logica.result.RoomGameCompletionRepository
import com.stanisryz.logica.result.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * One terminal result must move the wallet exactly zero or one time. Recomposition, a retried save,
 * or a repeated callback all run the very same completion transaction again, so the ledger event —
 * not the caller — is what makes the reward and the penalty happen only once.
 *
 * The reward itself is difficulty-based and puzzle-agnostic; the penalty is flat.
 */
class EconomyResultCompletionTest {
    private val definition = DailyChallengePolicyV2.definitionFor(LocalDate.of(2026, 8, 9))

    @Test
    fun anExpertFirstSolveWithThreeStarsPaysOneGemOnceHoweverOftenItIsPersisted() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            // Each solve and the gems it is worth under the one rule.
            val solves =
                listOf(
                    dao.catalogCompletion(PuzzleType.BALANCE, difficulty = Difficulty.EXPERT, stars = 3) to 1,
                    dao.catalogCompletion(PuzzleType.CROWNS, difficulty = Difficulty.EXPERT, stars = 2) to 0,
                    dao.catalogCompletion(PuzzleType.WORD, difficulty = Difficulty.EXPERT, stars = 3) to 1,
                    dao.catalogCompletion(PuzzleType.SUDOKU, difficulty = Difficulty.HARD, stars = 3) to 0,
                    // 2048 has no stars: its first cleared Expert level counts as three.
                    dao.catalogCompletion(PuzzleType.GAME_2048, difficulty = Difficulty.EXPERT) to 1,
                )

            solves.forEach { (completion, gems) ->
                val solved = completion.toEntity(NOW)
                dao.complete(solved)
                dao.complete(solved)
                dao.complete(solved)
                val event = dao.economyEvents.getValue(EconomyEvent.resultEventId(solved.resultId))
                assertEquals(EconomyEventType.SOLVED_REWARD.name, event.eventType)
                assertEquals(completion.puzzleType.name, gems, event.gemDelta)
                assertEquals(0, event.lifeDelta)
            }

            // Repeating each completion three times changed nothing.
            assertEquals(EconomyRules.STARTING_GEMS + 3, dao.wallet(NOW).gems)
            assertEquals(EconomyRules.STARTING_LIVES, dao.wallet(NOW).lives)
            assertEquals(solves.size, dao.results.size)
            assertEquals(solves.size, dao.economyEvents.size)
        }

    @Test
    fun anExpertLevelPaysOnlyWhenItsBestFirstReachesThreeStarsAndTheCardSeesExactlyThat() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val repository = RoomGameCompletionRepository(dao) { NOW }

            suspend fun solve(
                level: Int,
                attempt: String,
                stars: Int,
            ): Int =
                repository
                    .complete(
                        dao.catalogCompletion(
                            PuzzleType.SUDOKU,
                            difficulty = Difficulty.EXPERT,
                            levelNumber = level,
                            attemptId = attempt,
                            stars = stars,
                        ),
                    ).gemsEarned

            assertEquals(0, solve(level = 1, attempt = "first", stars = 2))
            // A replay that raises the level to three stars pays the gem.
            assertEquals(1, solve(level = 1, attempt = "replay-3", stars = 3))
            // Already three: no second gem, and a lower replay pays nothing either.
            assertEquals(0, solve(level = 1, attempt = "replay-3-again", stars = 3))
            assertEquals(0, solve(level = 1, attempt = "replay-2", stars = 2))
            // A first solve with three stars pays at once.
            assertEquals(1, solve(level = 2, attempt = "first", stars = 3))

            assertEquals(EconomyRules.STARTING_GEMS + 2, dao.wallet(NOW).gems)
            assertEquals(3, dao.currentLevel(PuzzleType.SUDOKU, Difficulty.EXPERT))
        }

    /** The reward never depends on the scope: a Daily entry pays what the same Catalog solve pays. */
    @Test
    fun theDailyScopeUsesTheSameDifficultyRewardAsTheCatalog() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val entry = definition.entries[0]
            val daily =
                GameCompletion(
                    resultId = "daily-0",
                    puzzleType = entry.puzzleType,
                    difficulty = entry.difficulty,
                    puzzleSeed = entry.seed,
                    generatorVersion = entry.generatorVersion,
                    resultScope = GameResultScope.DAILY,
                    hintsUsed = 1,
                    challengeDate = definition.challengeDate,
                    dailyPolicyVersion = definition.policyVersion,
                ).toEntity(NOW)
            val catalog =
                dao
                    .catalogCompletion(entry.puzzleType, difficulty = entry.difficulty)
                    .toEntity(NOW)

            dao.complete(daily)
            dao.complete(catalog)

            val expected = EconomyRules.solvedGemReward(entry.puzzleType, entry.difficulty, stars = null, previousBestStars = null)
            assertEquals(expected, dao.economyEvents.getValue(EconomyEvent.resultEventId("daily-0")).gemDelta)
            assertEquals(
                expected,
                dao.economyEvents.getValue(EconomyEvent.resultEventId(catalog.resultId)).gemDelta,
            )
            assertEquals(EconomyRules.STARTING_GEMS + expected * 2, dao.wallet(NOW).gems)
        }

    /** Failure is flat: the hardest attempt costs exactly the same single life as the easiest. */
    @Test
    fun aFailedResultSpendsOneLifeAtEveryDifficultyExactlyOnce() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val failed =
                dao
                    .catalogCompletion(PuzzleType.GAME_2048, GameOutcome.FAILED, Difficulty.EXPERT)
                    .toEntity(NOW)

            dao.complete(failed)
            dao.complete(failed)

            val wallet = dao.wallet(NOW)
            assertEquals(EconomyRules.STARTING_LIVES - 1, wallet.lives)
            assertEquals(EconomyRules.STARTING_GEMS, wallet.gems)
            assertEquals(NOW + EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS, wallet.nextLifeAtEpochMillis)
            assertEquals(1, dao.economyEvents.size)
            val event = dao.economyEvents.getValue(EconomyEvent.resultEventId(failed.resultId))
            assertEquals(EconomyEventType.FAILED_PENALTY.name, event.eventType)
            assertEquals(-1, event.lifeDelta)
            assertEquals(0, event.gemDelta)
        }

    /** The last life: the result stays durable and the wallet stops at zero rather than going negative. */
    @Test
    fun failingOnTheLastLifeLeavesZeroLivesAndADurableResult() =
        runBlocking {
            val dao =
                FakeGameCompletionDao(
                    definition,
                    PlayerEconomy(gems = 2, lives = 1, nextLifeAtEpochMillis = NOW + 1_000),
                )
            val failed = dao.catalogCompletion(PuzzleType.CROWNS, GameOutcome.FAILED).toEntity(NOW)

            dao.complete(failed)

            val wallet = dao.wallet(NOW)
            assertEquals(0, wallet.lives)
            assertEquals(2, wallet.gems)
            // The countdown that was already running is kept rather than restarted.
            assertEquals(NOW + 1_000, wallet.nextLifeAtEpochMillis)
            assertFalse(wallet.isGameplayAllowed)
            assertEquals(1, dao.results.size)
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
