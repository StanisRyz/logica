package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebWordControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun solvedLevelIsDurableBeforeImmediateExitCanResetCompletion() =
        runTest {
            val progression = FakeWebCatalogProgressAccess(initialLevel = 7)
            val controller =
                WebWordController(
                    loadPack = {},
                    loadRuntimeResources = {},
                    progression = progression,
                    levelPack = fixedEasyLevels,
                    runtimeResolver = { testRuntime },
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.EASY)
            advanceUntilIdle()
            TEST_ANSWER.forEachIndexed(controller::setLetter)
            controller.submit()
            controller.showDifficultySelector()

            val current =
                assertIs<WebCatalogLevelResolution.Resolved>(
                    progression.resolveCurrentLevel(PuzzleType.WORD, Difficulty.EASY),
                )
            assertEquals(8, current.attempt.levelId.levelNumber.value)
            assertEquals(1, progression.advanceCalls)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun authoritativeLevelAdvancesOnceAndNextReloadsTheDurableLevelAfterReveal() =
        runTest {
            var loadedDifficulty: Difficulty? = null
            var loadedResources: List<String>? = null
            val progression = FakeWebCatalogProgressAccess(initialLevel = 7)
            val controller =
                WebWordController(
                    loadPack = { loadedDifficulty = it },
                    loadRuntimeResources = { loadedResources = it },
                    progression = progression,
                    levelPack = fixedEasyLevels,
                    runtimeResolver = { testRuntime },
                    scope = this,
                )

            controller.selectDifficulty(Difficulty.EASY)
            advanceUntilIdle()

            val playing = assertIs<WebWordState.Playing>(controller.state)
            assertEquals(Difficulty.EASY, loadedDifficulty)
            assertEquals(testRuntime.requiredResourcePaths, loadedResources)
            assertEquals(PuzzleSeed(17), playing.definition.seed)
            assertEquals(7, playing.definition.levelNumber.value)
            TEST_ANSWER.forEachIndexed(controller::setLetter)
            controller.submit()

            val solved = assertIs<WebWordState.Playing>(controller.state)
            assertEquals(WordGameStatus.SOLVED, solved.game.status)
            assertEquals(1, solved.acceptedAttemptRevision)
            assertFalse(solved.isTerminalRevealReady)
            advanceUntilIdle()
            val saved = assertIs<WebCatalogCompletionState.Saved>(controller.completionState)
            assertEquals(8, saved.nextLevel.levelNumber.value)
            controller.submit()
            advanceUntilIdle()
            assertEquals(1, progression.advanceCalls)
            controller.onAcceptedAttemptRevealed(solved.acceptedAttemptRevision)
            assertTrue(assertIs<WebWordState.Playing>(controller.state).isTerminalRevealReady)

            controller.nextLevel()
            advanceUntilIdle()
            assertEquals(8, assertIs<WebWordState.Playing>(controller.state).definition.levelNumber.value)
        }

    private val testRuntime =
        WordRuntime(
            generator =
                object : PuzzleGenerator<WordPuzzle> {
                    override val type = PuzzleType.WORD
                    override val version = GeneratorVersion(2)

                    override fun generate(
                        seed: PuzzleSeed,
                        difficulty: Difficulty,
                    ): WordPuzzle = WordPuzzle(PuzzleId(type, difficulty, seed, version), TEST_ANSWER)
                },
            allowedGuesses =
                object : WordAllowedGuesses {
                    override val size = 1

                    override fun contains(normalizedWord: String): Boolean = normalizedWord == TEST_ANSWER

                    override fun all(): List<String> = listOf(TEST_ANSWER)
                },
            requiredResourcePaths = listOf("/word/v2/test_answers.txt", "/word/v2/test_guesses.txt"),
        )

    private val fixedEasyLevels =
        object : CatalogLevelPack {
            override fun resolve(levelId: CatalogLevelId): CatalogLevelPackResult<CatalogLevelDefinition> {
                assertEquals(PuzzleType.WORD, levelId.puzzleType)
                assertEquals(Difficulty.EASY, levelId.difficulty)
                assertTrue(levelId.levelNumber.value == 7 || levelId.levelNumber.value == 8)
                assertEquals(CatalogLevelPackVersion.V1, levelId.packVersion)
                return CatalogLevelPackResult.Success(
                    CatalogLevelDefinition(
                        levelId = levelId,
                        seed = PuzzleSeed(17),
                        generatorVersion = GeneratorVersion(2),
                    ),
                )
            }
        }

    private companion object {
        const val TEST_ANSWER = "мама"
    }
}
