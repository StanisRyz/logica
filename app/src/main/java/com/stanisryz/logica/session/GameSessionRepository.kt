package com.stanisryz.logica.session

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.Flow

internal data class SavedGameSession(
    val sessionId: String,
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val puzzleSeed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val sessionFormatVersion: Int,
    val gameplayPayload: String,
    val moveHistoryPayload: String,
    val hintsUsed: Int,
    val status: String,
)

internal interface GameSessionRepository {
    suspend fun readActiveSession(puzzleType: PuzzleType): SavedGameSession?

    fun replaceActiveSession(session: SavedGameSession)

    fun updateActiveSession(session: SavedGameSession)

    fun deleteActiveSession(
        puzzleType: PuzzleType,
        sessionId: String,
    )

    fun observeHasActiveSession(puzzleType: PuzzleType): Flow<Boolean>
}
