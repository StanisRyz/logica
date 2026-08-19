package com.stanisryz.logica.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    fun advanceSolved(attempt: WebCatalogAttempt): WebCatalogCompletionResult

    fun retryContextBinding()
}

/** Dynamically delegates every operation to the repository bound to the current Player context. */
internal class WebCatalogProgressCoordinator(
    private val playerSession: WebPlayerSessionController,
) : WebCatalogProgressAccess {
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

    override fun advanceSolved(attempt: WebCatalogAttempt): WebCatalogCompletionResult {
        val binding =
            playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready
                ?: return WebCatalogCompletionResult.ContextChanged
        if (binding.token != attempt.playerContextToken) return WebCatalogCompletionResult.ContextChanged

        val result = binding.repository.advanceSolved(attempt.levelId)
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

/** Owns the common Saving/Saved/Error transition and guarantees one live save job per attempt. */
internal class WebCatalogCompletionController(
    private val progression: WebCatalogProgressAccess,
    private val scope: CoroutineScope,
) {
    private var attempt: WebCatalogAttempt? = null
    private var operation: Job? = null

    var state by mutableStateOf<WebCatalogCompletionState>(WebCatalogCompletionState.Idle)
        private set

    fun startAttempt(attempt: WebCatalogAttempt) {
        operation?.cancel()
        operation = null
        this.attempt = attempt
        state = WebCatalogCompletionState.Idle
    }

    fun saveSolved(attempt: WebCatalogAttempt) {
        if (this.attempt != attempt) return
        if (state != WebCatalogCompletionState.Idle && state !is WebCatalogCompletionState.SaveError) return
        state = WebCatalogCompletionState.Saving
        operation =
            scope.launch {
                val result = progression.advanceSolved(attempt)
                if (this@WebCatalogCompletionController.attempt != attempt) return@launch
                state =
                    when (result) {
                        is WebCatalogCompletionResult.Saved -> WebCatalogCompletionState.Saved(result.nextLevel)
                        is WebCatalogCompletionResult.PersistenceFailed ->
                            WebCatalogCompletionState.SaveError(result.detail)
                        WebCatalogCompletionResult.Rejected ->
                            WebCatalogCompletionState.SaveError("The authoritative Catalog level no longer matches this attempt.")
                        WebCatalogCompletionResult.ContextChanged ->
                            WebCatalogCompletionState.SaveError("The Player context changed before progress could be saved.")
                    }
            }
    }

    fun reset() {
        operation?.cancel()
        operation = null
        attempt = null
        state = WebCatalogCompletionState.Idle
    }
}
