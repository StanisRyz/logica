package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPacks
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGuessRejection
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordRuntime
import com.stanisryz.logica.puzzle.core.word.WordRuntimeResolver
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal sealed interface WebWordState {
    data object DifficultySelection : WebWordState

    data class Loading(
        val difficulty: Difficulty,
    ) : WebWordState

    data class Playing(
        val definition: CatalogLevelDefinition,
        val puzzle: WordPuzzle,
        val game: WordGameState,
        val rejection: WordGuessRejection? = null,
        val rejectionRevision: Int = 0,
        val acceptedAttemptRevision: Int = 0,
        val revealedAttemptRevision: Int = 0,
    ) : WebWordState {
        val isTerminalRevealReady: Boolean
            get() =
                game.isFinished &&
                    (acceptedAttemptRevision == 0 || revealedAttemptRevision >= acceptedAttemptRevision)
    }

    data class Error(
        val difficulty: Difficulty,
        val detail: String,
    ) : WebWordState
}

/** Lightweight Web adapter over frozen Word Level 1 and the common Word runtime and engine. */
internal class WebWordController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadRuntimeResources: suspend (List<String>) -> Unit,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = WordRuntimeResolver::resolve,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: WordGameEngine? = null

    var state by mutableStateOf<WebWordState>(WebWordState.DifficultySelection)
        private set

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebWordState.Loading(difficulty)
        operation =
            scope.launch {
                try {
                    loadPack(difficulty)
                    val definition = resolveLevelOne(difficulty)
                    val runtime = runtimeResolver(definition.generatorVersion)
                    require(runtime.generator.version == definition.generatorVersion) {
                        "Word Level 1 requires generator ${definition.generatorVersion.value}."
                    }
                    loadRuntimeResources(runtime.requiredResourcePaths)
                    val puzzle = runtime.generator.generate(definition.seed, difficulty)
                    require(puzzle.id.generatorVersion == definition.generatorVersion)
                    runtime.allowedGuesses.size
                    val nextEngine = WordGameEngine(puzzle, runtime.allowedGuesses)
                    engine = nextEngine
                    state = WebWordState.Playing(definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    state = WebWordState.Error(difficulty, exception.message ?: "Word Level 1 is unavailable.")
                }
            }
    }

    fun setLetter(
        position: Int,
        letter: Char,
    ) {
        updateGame { activeEngine, game -> activeEngine.setLetter(game, position, letter) }
    }

    fun clearLetter(position: Int) {
        updateGame { activeEngine, game -> activeEngine.clearLetter(game, position) }
    }

    fun submit() {
        val activeEngine = engine ?: return
        val playing = state as? WebWordState.Playing ?: return
        when (val result = activeEngine.submit(playing.game)) {
            is WordSubmitResult.Rejected ->
                state =
                    playing.copy(
                        rejection = result.rejection,
                        rejectionRevision = playing.rejectionRevision + 1,
                    )
            is WordSubmitResult.Accepted ->
                state =
                    playing.copy(
                        game = result.state,
                        rejection = null,
                        acceptedAttemptRevision = playing.acceptedAttemptRevision + 1,
                    )
        }
    }

    fun dismissRejection() {
        val playing = state as? WebWordState.Playing ?: return
        if (playing.rejection != null) state = playing.copy(rejection = null)
    }

    fun onAcceptedAttemptRevealed(revision: Int) {
        val playing = state as? WebWordState.Playing ?: return
        if (revision <= playing.revealedAttemptRevision || revision > playing.acceptedAttemptRevision) return
        state = playing.copy(revealedAttemptRevision = revision)
    }

    fun retry() {
        val playing = state as? WebWordState.Playing ?: return
        if (!playing.game.isFinished) return
        val activeEngine = engine ?: return
        operation?.cancel()
        state =
            playing.copy(
                game = activeEngine.start(),
                rejection = null,
                rejectionRevision = 0,
                acceptedAttemptRevision = 0,
                revealedAttemptRevision = 0,
            )
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        state = WebWordState.DifficultySelection
    }

    fun dispose() {
        scope.cancel()
    }

    private fun updateGame(update: (WordGameEngine, WordGameState) -> WordGameState) {
        val activeEngine = engine ?: return
        val playing = state as? WebWordState.Playing ?: return
        if (playing.game.isFinished) return
        val updated = update(activeEngine, playing.game)
        if (updated == playing.game) {
            if (playing.rejection != null) state = playing.copy(rejection = null)
            return
        }
        state = playing.copy(game = updated, rejection = null)
    }

    private fun resolveLevelOne(difficulty: Difficulty): CatalogLevelDefinition {
        val levelId =
            CatalogLevelId(
                puzzleType = PuzzleType.WORD,
                difficulty = difficulty,
                levelNumber = CatalogLevelPacks.FIRST_LEVEL,
                packVersion = CatalogLevelPackVersion.V1,
            )
        return when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }
    }

    companion object {
        fun create(loader: BrowserPuzzleDataLoader): WebWordController =
            WebWordController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.WORD,
                        difficulty = difficulty,
                    )
                },
                loadRuntimeResources = loader::loadWordResources,
            )
    }
}
