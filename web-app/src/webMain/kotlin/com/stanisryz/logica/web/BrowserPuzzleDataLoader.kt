@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogContentVariant
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.khronos.webgl.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * Fetches only the specific canonical resource requested by Web gameplay code, each path once:
 * concurrent requests for one path share a single fetch (see [SharedResourceLoads]).
 */
class BrowserPuzzleDataLoader {
    private val loads = SharedResourceLoads(CoroutineScope(SupervisorJob()))

    suspend fun loadWordResources(resourcePaths: List<String>) {
        resourcePaths.forEach { loadWordResource(it) }
    }

    suspend fun loadWordResource(resourcePath: String) =
        loads.load(resourcePath) {
            val text = fetchResponse(resourcePath).text().await().toString()
            WebPuzzleData.installWordLexiconResource(resourcePath, text)
        }

    suspend fun loadCatalogLevelPack(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        variant: CatalogContentVariant? = null,
    ) {
        val resourcePath = CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty, variant)
        loads.load(resourcePath) {
            val bytes = fetchResponse(resourcePath).arrayBuffer().await().toByteArray()
            WebPuzzleData.installCatalogLevelPack(packVersion, puzzleType, difficulty, bytes, variant)
        }
    }

    suspend fun loadSudokuDataset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ) {
        val resourcePath = "sudoku/v${version.value}/${difficulty.name.lowercase()}.sdk"
        loads.load(resourcePath) {
            val bytes = fetchResponse(resourcePath).arrayBuffer().await().toByteArray()
            WebPuzzleData.installSudokuDataset(version, difficulty, bytes)
        }
    }

    private suspend fun fetchResponse(resourcePath: String): BrowserFetchResponse {
        val response =
            try {
                browserFetch(resourcePath.relativeToPage()).await()
            } catch (cancelled: CancellationException) {
                // A cancellation is an IllegalStateException too; it must stay one, not become a load failure.
                throw cancelled
            } catch (failure: IllegalStateException) {
                // A rejected fetch: no connection, or the request never reached the server.
                throw WebPuzzleDataLoadException("Unable to load $resourcePath.", failure)
            }
        if (!response.ok) throw WebPuzzleDataLoadException("Unable to load $resourcePath: HTTP ${response.status}.")
        return response
    }

    // Relative to the page, never to the site root: Yandex Games serves the game from a nested
    // folder, where "/levels/..." would leave it and fail with 404.
    private fun String.relativeToPage(): String = removePrefix("/")
}

private external interface BrowserFetchResponse : JsAny {
    val ok: Boolean
    val status: Int

    fun text(): Promise<JsString>

    fun arrayBuffer(): Promise<ArrayBuffer>
}

@JsName("fetch")
private external fun browserFetch(resourcePath: String): Promise<BrowserFetchResponse>

/** The fetched bytes in one block copy (Wasm) or none at all (JS), never one bridge call per byte. */
internal expect fun ArrayBuffer.toByteArray(): ByteArray
