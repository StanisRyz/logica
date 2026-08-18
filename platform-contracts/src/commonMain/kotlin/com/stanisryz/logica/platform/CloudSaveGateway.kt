package com.stanisryz.logica.platform

enum class CloudSaveAvailability {
    AVAILABLE,
    UNSUPPORTED,
}

sealed interface CloudSaveReadResult {
    data class Found(
        val payload: ByteArray,
    ) : CloudSaveReadResult

    data object Missing : CloudSaveReadResult

    data object Unsupported : CloudSaveReadResult

    data class Failed(
        val cause: Throwable,
    ) : CloudSaveReadResult
}

sealed interface CloudSaveWriteResult {
    data object Saved : CloudSaveWriteResult

    data object Unsupported : CloudSaveWriteResult

    data class Failed(
        val cause: Throwable,
    ) : CloudSaveWriteResult
}

/** Optional remote snapshot storage. Room and DataStore remain separate local persistence. */
interface CloudSaveGateway {
    val availability: CloudSaveAvailability

    suspend fun read(): CloudSaveReadResult

    suspend fun write(payload: ByteArray): CloudSaveWriteResult
}
