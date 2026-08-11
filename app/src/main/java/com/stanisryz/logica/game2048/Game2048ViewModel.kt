package com.stanisryz.logica.game2048

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stanisryz.logica.puzzle.core.game2048.EncodedGame2048Session
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048SessionCodecV1
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class Game2048UiState(
    val game: Game2048State,
)

/** Standalone Stage 36 presentation state: no repositories, economy, results, or navigation. */
internal class Game2048ViewModel(
    puzzleId: Game2048PuzzleId,
    restoredSession: EncodedGame2048Session? = null,
) : ViewModel() {
    private val engine = Game2048Engine(puzzleId)
    private val mutableUiState =
        MutableStateFlow(
            Game2048UiState(
                restoredSession
                    ?.let(Game2048SessionCodecV1::decode)
                    ?.also { require(it.puzzleId == puzzleId) { "Restored 2048 puzzle identity does not match." } }
                    ?: engine.start(),
            ),
        )
    val uiState: StateFlow<Game2048UiState> = mutableUiState.asStateFlow()

    fun move(direction: Game2048Direction) {
        val current = mutableUiState.value
        val moved = engine.move(current.game, direction)
        if (moved != current.game) mutableUiState.value = Game2048UiState(moved)
    }

    fun retry() {
        val current = mutableUiState.value
        if (current.game.status.isTerminal) {
            mutableUiState.value = Game2048UiState(engine.retry(current.game))
        }
    }

    /** A local fixture for previews/host experiments; Stage 37 will own durable persistence. */
    fun encodeSession(): EncodedGame2048Session = Game2048SessionCodecV1.encode(mutableUiState.value.game)
}

internal class Game2048ViewModelFactory(
    private val puzzleId: Game2048PuzzleId,
    private val restoredSession: EncodedGame2048Session? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(Game2048ViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return Game2048ViewModel(puzzleId, restoredSession) as T
    }
}
