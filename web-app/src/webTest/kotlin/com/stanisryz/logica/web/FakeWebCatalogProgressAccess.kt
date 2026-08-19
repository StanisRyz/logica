package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

internal class FakeWebCatalogProgressAccess(
    initialLevel: Int = 1,
) : WebCatalogProgressAccess {
    private val levels = mutableMapOf<Triple<PuzzleType, Difficulty, CatalogLevelPackVersion>, CatalogLevelNumber>()
    private val defaultLevel = CatalogLevelNumber(initialLevel)
    private var token = WebPlayerContextToken(1L)

    var advanceCalls: Int = 0
        private set

    override val isReady: Boolean = true

    override suspend fun resolveCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        packVersion: CatalogLevelPackVersion,
    ): WebCatalogLevelResolution {
        val key = Triple(puzzleType, difficulty, packVersion)
        return WebCatalogLevelResolution.Resolved(
            WebCatalogAttempt(
                CatalogLevelId(puzzleType, difficulty, levels[key] ?: defaultLevel, packVersion),
                token,
            ),
        )
    }

    override fun isCurrent(attempt: WebCatalogAttempt): Boolean = attempt.playerContextToken == token

    override fun advanceSolved(attempt: WebCatalogAttempt): WebCatalogCompletionResult {
        if (!isCurrent(attempt)) return WebCatalogCompletionResult.ContextChanged
        val id = attempt.levelId
        val key = Triple(id.puzzleType, id.difficulty, id.packVersion)
        val current = levels[key] ?: defaultLevel
        return when {
            current.value > id.levelNumber.value ->
                WebCatalogCompletionResult.Saved(id.copy(levelNumber = current))
            current != id.levelNumber -> WebCatalogCompletionResult.Rejected
            else -> {
                advanceCalls += 1
                val next = current.next
                levels[key] = next
                WebCatalogCompletionResult.Saved(id.copy(levelNumber = next))
            }
        }
    }

    override fun retryContextBinding() = Unit
}
