package com.stanisryz.logica.platform

internal enum class CloudSaveAvailability {
    AVAILABLE,
    UNSUPPORTED,
}

internal sealed interface CloudSaveReadResult {
    data class Found(
        val payload: ByteArray,
    ) : CloudSaveReadResult

    data object Missing : CloudSaveReadResult

    data object Unsupported : CloudSaveReadResult

    data class Failed(
        val cause: Throwable,
    ) : CloudSaveReadResult
}

internal sealed interface CloudSaveWriteResult {
    data object Saved : CloudSaveWriteResult

    data object Unsupported : CloudSaveWriteResult

    data class Failed(
        val cause: Throwable,
    ) : CloudSaveWriteResult
}

/** Optional remote snapshot storage. Room and DataStore remain separate local persistence. */
internal interface CloudSaveGateway {
    val availability: CloudSaveAvailability

    suspend fun read(): CloudSaveReadResult

    suspend fun write(payload: ByteArray): CloudSaveWriteResult
}
