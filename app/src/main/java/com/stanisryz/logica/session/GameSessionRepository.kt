package com.stanisryz.logica.session

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

internal enum class GameSessionScope {
    CATALOG,
    DAILY,
}

internal data class DailyGameSessionIdentity(
    val challengeDate: LocalDate,
    val policyVersion: Int,
) {
    init {
        require(policyVersion > 0) { "Daily policy version must be positive." }
    }
}

internal data class SavedGameSession(
    val sessionId: String,
    val puzzleType: PuzzleType,
    val sessionScope: GameSessionScope,
    val difficulty: Difficulty,
    val puzzleSeed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val dailyIdentity: DailyGameSessionIdentity? = null,
    val sessionFormatVersion: Int,
    val gameplayPayload: String,
    val moveHistoryPayload: String,
    val hintsUsed: Int,
    val status: String,
) {
    init {
        require((sessionScope == GameSessionScope.DAILY) == (dailyIdentity != null)) {
            "Only Daily sessions may contain Daily identity."
        }
    }
}

internal interface GameSessionRepository {
    suspend fun readActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
    ): SavedGameSession?

    fun replaceActiveSession(session: SavedGameSession)

    fun updateActiveSession(session: SavedGameSession)

    fun deleteActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
        sessionId: String,
    )

    fun observeHasActiveSession(
        puzzleType: PuzzleType,
        sessionScope: GameSessionScope,
    ): Flow<Boolean>
}
