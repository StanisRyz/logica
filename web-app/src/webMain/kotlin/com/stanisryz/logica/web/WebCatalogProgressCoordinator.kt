package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.first
import kotlin.jvm.JvmInline

@JvmInline
internal value class WebPlayerContextToken(
    val value: Long,
)

/** Immutable identity captured before a frozen Catalog level is loaded. */
internal data class WebCatalogAttempt(
    val levelId: CatalogLevelId,
    val playerContextToken: WebPlayerContextToken,
)

internal sealed interface WebCatalogLevelResolution {
    data class Resolved(
        val attempt: WebCatalogAttempt,
    ) : WebCatalogLevelResolution

    data class Unavailable(
        val detail: String,
    ) : WebCatalogLevelResolution
}

internal sealed interface WebCatalogCompletionResult {
    data class Saved(
        val nextLevel: CatalogLevelId,
    ) : WebCatalogCompletionResult

    data class PersistenceFailed(
        val detail: String,
    ) : WebCatalogCompletionResult

    data object Rejected : WebCatalogCompletionResult

    data object ContextChanged : WebCatalogCompletionResult
}

/** The only Player/session-facing surface used by Web gameplay controllers. */
internal interface WebCatalogProgressAccess {
    val isReady: Boolean

    suspend fun resolveCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        packVersion: CatalogLevelPackVersion = CatalogLevelPackVersion.V1,
    ): WebCatalogLevelResolution

    fun isCurrent(attempt: WebCatalogAttempt): Boolean

    /** Advances a solved level and keeps its [stars] when they beat the level's best. */
    fun advanceSolved(
        attempt: WebCatalogAttempt,
        stars: Int? = null,
    ): WebCatalogCompletionResult

    fun retryContextBinding()

    /** Keeps a 2048 score as the bound Player's best when it beats it; local only until published. */
    fun recordBest2048(score: Long) {}

    /** Publishes the best 2048 score once a game ends or is left, so the rating and cloud see it. */
    fun publishBest2048() {}
}

/** Dynamically delegates every operation to the repository bound to the current Player context. */
internal class WebCatalogProgressCoordinator(
    private val playerSession: WebPlayerSessionController,
) : WebCatalogProgressAccess {
    override fun recordBest2048(score: Long) {
        (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository?.recordBest2048(score)
    }

    override fun publishBest2048() {
        (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository?.publishBest2048()
    }

    override val isReady: Boolean
        get() = playerSession.progressBinding.value is WebCatalogProgressBinding.Ready

    override suspend fun resolveCurrentLevel(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        packVersion: CatalogLevelPackVersion,
    ): WebCatalogLevelResolution =
        when (
            val binding =
                playerSession.progressBinding.first {
                    it !is WebCatalogProgressBinding.Loading
                }
        ) {
            is WebCatalogProgressBinding.Ready -> {
                val bucket = WebCatalogProgressBucket(puzzleType, difficulty, packVersion)
                WebCatalogLevelResolution.Resolved(
                    WebCatalogAttempt(
                        levelId =
                            CatalogLevelId(
                                puzzleType = puzzleType,
                                difficulty = difficulty,
                                levelNumber = binding.repository.currentLevel(bucket),
                                packVersion = packVersion,
                            ),
                        playerContextToken = binding.token,
                    ),
                )
            }
            is WebCatalogProgressBinding.Unavailable -> WebCatalogLevelResolution.Unavailable(binding.detail)
            WebCatalogProgressBinding.Loading -> error("A loading binding cannot complete level resolution.")
        }

    override fun isCurrent(attempt: WebCatalogAttempt): Boolean =
        (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.token ==
            attempt.playerContextToken

    override fun advanceSolved(
        attempt: WebCatalogAttempt,
        stars: Int?,
    ): WebCatalogCompletionResult {
        val binding =
            playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready
                ?: return WebCatalogCompletionResult.ContextChanged
        if (binding.token != attempt.playerContextToken) return WebCatalogCompletionResult.ContextChanged

        val result = binding.repository.advanceSolved(attempt.levelId)
        // A first solve or a replay of a solved level may both earn better stars.
        if (result is WebCatalogAdvanceResult.Advanced || result == WebCatalogAdvanceResult.Idempotent) {
            stars?.let { binding.repository.recordStars(attempt.levelId, it) }
        }
        return when (result) {
            is WebCatalogAdvanceResult.Advanced -> {
                val nextLevel = attempt.levelId.copy(levelNumber = result.currentLevel)
                playerSession.requestCloudSynchronization(binding)
                WebCatalogCompletionResult.Saved(nextLevel)
            }
            WebCatalogAdvanceResult.Idempotent -> {
                val bucket =
                    WebCatalogProgressBucket(
                        attempt.levelId.puzzleType,
                        attempt.levelId.difficulty,
                        attempt.levelId.packVersion,
                    )
                val nextLevel = attempt.levelId.copy(levelNumber = binding.repository.currentLevel(bucket))
                playerSession.requestCloudSynchronization(binding)
                WebCatalogCompletionResult.Saved(nextLevel)
            }
            is WebCatalogAdvanceResult.PersistenceFailed ->
                WebCatalogCompletionResult.PersistenceFailed(
                    result.cause.message ?: "Browser storage rejected the Catalog progress update.",
                )
            WebCatalogAdvanceResult.Rejected -> WebCatalogCompletionResult.Rejected
        }
    }

    override fun retryContextBinding() {
        playerSession.retryCurrentContext()
    }
}

/** Web-only durable Catalog completion presentation state shared by all five controllers. */
internal sealed interface WebCatalogCompletionState {
    data object Idle : WebCatalogCompletionState

    data object Saving : WebCatalogCompletionState

    data class Saved(
        val nextLevel: CatalogLevelId,
    ) : WebCatalogCompletionState

    data class SaveError(
        val detail: String,
    ) : WebCatalogCompletionState
}

/** Owns the common synchronous local Saving/Saved/Error transition for one attempt. */
internal class WebCatalogCompletionController(
    private val progression: WebCatalogProgressAccess,
) {
    private var attempt: WebCatalogAttempt? = null

    var state by mutableStateOf<WebCatalogCompletionState>(WebCatalogCompletionState.Idle)
        private set

    fun startAttempt(attempt: WebCatalogAttempt) {
        this.attempt = attempt
        state = WebCatalogCompletionState.Idle
    }

    fun saveSolved(
        attempt: WebCatalogAttempt,
        stars: Int? = null,
    ) {
        if (this.attempt != attempt) return
        if (state != WebCatalogCompletionState.Idle && state !is WebCatalogCompletionState.SaveError) return
        state = WebCatalogCompletionState.Saving
        state =
            when (val result = progression.advanceSolved(attempt, stars)) {
                is WebCatalogCompletionResult.Saved -> WebCatalogCompletionState.Saved(result.nextLevel)
                is WebCatalogCompletionResult.PersistenceFailed ->
                    WebCatalogCompletionState.SaveError(result.detail)
                WebCatalogCompletionResult.Rejected ->
                    WebCatalogCompletionState.SaveError("The authoritative Catalog level no longer matches this attempt.")
                WebCatalogCompletionResult.ContextChanged ->
                    WebCatalogCompletionState.SaveError("The Player context changed before progress could be saved.")
            }
    }

    fun reset() {
        attempt = null
        state = WebCatalogCompletionState.Idle
    }
}
