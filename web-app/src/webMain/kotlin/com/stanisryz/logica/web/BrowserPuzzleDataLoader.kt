@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.web.WebPuzzleData
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.asJsException

/** Fetches only the specific canonical resource requested by future Web gameplay code. */
class BrowserPuzzleDataLoader {
    private val loadedResources = mutableSetOf<String>()

    suspend fun loadWordResources(resourcePaths: List<String>) {
        resourcePaths.forEach { loadWordResource(it) }
    }

    suspend fun loadWordResource(resourcePath: String) {
        if (resourcePath in loadedResources) return
        val text = fetchResponse(resourcePath).text().await().toString()
        WebPuzzleData.installWordLexiconResource(resourcePath, text)
        loadedResources += resourcePath
    }

    suspend fun loadCatalogLevelPack(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ) {
        val resourcePath = CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)
        if (resourcePath in loadedResources) return
        val bytes = fetchResponse(resourcePath.asRootPath()).arrayBuffer().await().toByteArray()
        WebPuzzleData.installCatalogLevelPack(packVersion, puzzleType, difficulty, bytes)
        loadedResources += resourcePath
    }

    suspend fun loadSudokuDataset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ) {
        val resourcePath = "sudoku/v${version.value}/${difficulty.name.lowercase()}.sdk"
        if (resourcePath in loadedResources) return
        val bytes = fetchResponse(resourcePath.asRootPath()).arrayBuffer().await().toByteArray()
        WebPuzzleData.installSudokuDataset(version, difficulty, bytes)
        loadedResources += resourcePath
    }

    private suspend fun fetchResponse(resourcePath: String): BrowserFetchResponse {
        val response = browserFetch(resourcePath.asRootPath()).await()
        check(response.ok) {
            "Unable to load $resourcePath: HTTP ${response.status}."
        }
        return response
    }

    private fun String.asRootPath(): String = if (startsWith('/')) this else "/$this"
}

private external interface BrowserFetchResponse : JsAny {
    val ok: Boolean
    val status: Int

    fun text(): Promise<JsString>

    fun arrayBuffer(): Promise<ArrayBuffer>
}

@JsName("fetch")
private external fun browserFetch(resourcePath: String): Promise<BrowserFetchResponse>

private suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCoroutine { continuation ->
        then(
            onFulfilled = { value ->
                continuation.resume(value)
                null
            },
            onRejected = { reason ->
                continuation.resumeWithException(reason.asJsException())
                null
            },
        )
    }

private fun ArrayBuffer.toByteArray(): ByteArray {
    val source = Uint8Array(this)
    return ByteArray(source.length) { index -> uint8ArrayByteAt(source, index).toByte() }
}

private fun uint8ArrayByteAt(
    source: Uint8Array,
    index: Int,
): Int = js("source[index]")
