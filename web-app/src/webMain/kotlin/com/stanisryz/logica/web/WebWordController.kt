package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordGameStatus
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
        val levelNumber: CatalogLevelNumber? = null,
    ) : WebWordState

    data class Playing(
        val attempt: WebCatalogAttempt,
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
        val levelNumber: CatalogLevelNumber?,
        val detail: String,
        val progressionUnavailable: Boolean = false,
    ) : WebWordState
}

/** Lightweight Web adapter over authoritative frozen Word Catalog levels and the common runtime. */
internal class WebWordController(
    private val loadPack: suspend (Difficulty) -> Unit,
    private val loadRuntimeResources: suspend (List<String>) -> Unit,
    private val progression: WebCatalogProgressAccess,
    private val levelPack: CatalogLevelPack = BinaryCatalogLevelPack(WebPuzzleData),
    private val runtimeResolver: (GeneratorVersion) -> WordRuntime = WordRuntimeResolver::resolve,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var operation: Job? = null
    private var engine: WordGameEngine? = null
    private val completion = WebCatalogCompletionController(progression)

    var state by mutableStateOf<WebWordState>(WebWordState.DifficultySelection)
        private set

    val completionState: WebCatalogCompletionState
        get() = completion.state

    fun selectDifficulty(difficulty: Difficulty) {
        operation?.cancel()
        state = WebWordState.Loading(difficulty)
        operation =
            scope.launch {
                var levelNumber: CatalogLevelNumber? = null
                try {
                    val attempt =
                        when (
                            val resolved =
                                progression.resolveCurrentLevel(
                                    PuzzleType.WORD,
                                    difficulty,
                                    CatalogLevelPackVersion.V1,
                                )
                        ) {
                            is WebCatalogLevelResolution.Resolved -> resolved.attempt
                            is WebCatalogLevelResolution.Unavailable -> {
                                state = WebWordState.Error(difficulty, null, resolved.detail, progressionUnavailable = true)
                                return@launch
                            }
                        }
                    levelNumber = attempt.levelId.levelNumber
                    state = WebWordState.Loading(difficulty, levelNumber)
                    loadPack(difficulty)
                    if (!progression.isCurrent(attempt)) return@launch
                    val definition = resolveLevel(attempt.levelId)
                    val runtime = runtimeResolver(definition.generatorVersion)
                    require(runtime.generator.version == definition.generatorVersion) {
                        "Word level ${attempt.levelId.levelNumber.value} requires generator ${definition.generatorVersion.value}."
                    }
                    loadRuntimeResources(runtime.requiredResourcePaths)
                    if (!progression.isCurrent(attempt)) return@launch
                    val puzzle = runtime.generator.generate(definition.seed, difficulty)
                    require(puzzle.id.generatorVersion == definition.generatorVersion)
                    runtime.allowedGuesses.size
                    val nextEngine = WordGameEngine(puzzle, runtime.allowedGuesses)
                    engine = nextEngine
                    completion.startAttempt(attempt)
                    state = WebWordState.Playing(attempt, definition, puzzle, nextEngine.start())
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    engine = null
                    completion.reset()
                    state = WebWordState.Error(difficulty, levelNumber, exception.message ?: "Word level is unavailable.")
                }
            }
    }

    fun retryLoading() {
        val error = state as? WebWordState.Error ?: return
        if (error.progressionUnavailable) progression.retryContextBinding()
        selectDifficulty(error.difficulty)
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
                playing
                    .copy(
                        game = result.state,
                        rejection = null,
                        acceptedAttemptRevision = playing.acceptedAttemptRevision + 1,
                    ).also { updated ->
                        state = updated
                        if (playing.game.status != WordGameStatus.SOLVED && updated.game.status == WordGameStatus.SOLVED) {
                            completion.saveSolved(playing.attempt)
                        }
                    }
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
        if (playing.game.status != WordGameStatus.FAILED) return
        val activeEngine = engine ?: return
        operation?.cancel()
        completion.startAttempt(playing.attempt)
        state =
            playing.copy(
                game = activeEngine.start(),
                rejection = null,
                rejectionRevision = 0,
                acceptedAttemptRevision = 0,
                revealedAttemptRevision = 0,
            )
    }

    fun nextLevel() {
        val playing = state as? WebWordState.Playing ?: return
        if (completion.state !is WebCatalogCompletionState.Saved) return
        selectDifficulty(playing.attempt.levelId.difficulty)
    }

    fun retrySave() {
        val playing = state as? WebWordState.Playing ?: return
        if (playing.game.status != WordGameStatus.SOLVED) return
        completion.saveSolved(playing.attempt)
    }

    fun showDifficultySelector() {
        operation?.cancel()
        engine = null
        completion.reset()
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

    private fun resolveLevel(levelId: CatalogLevelId): CatalogLevelDefinition =
        when (val resolved = levelPack.resolve(levelId)) {
            is CatalogLevelPackResult.Success -> resolved.value
            is CatalogLevelPackResult.Failure -> error(resolved.detail)
        }

    companion object {
        fun create(
            loader: BrowserPuzzleDataLoader,
            progression: WebCatalogProgressAccess,
        ): WebWordController =
            WebWordController(
                loadPack = { difficulty ->
                    loader.loadCatalogLevelPack(
                        packVersion = CatalogLevelPackVersion.V1,
                        puzzleType = PuzzleType.WORD,
                        difficulty = difficulty,
                    )
                },
                loadRuntimeResources = loader::loadWordResources,
                progression = progression,
            )
    }
}
