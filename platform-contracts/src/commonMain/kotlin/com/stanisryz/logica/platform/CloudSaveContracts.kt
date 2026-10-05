package com.stanisryz.logica.platform

/**
 * The versioned unified save payload. Sections are opaque domain-owned byte payloads keyed by
 * stable section ids (catalog, statistics, daily, economy, store, ...), so every module keeps its
 * own codec and business logic while the save envelope stays platform-neutral.
 */
data class SaveData(
    val version: Int = CURRENT_VERSION,
    val sections: Map<String, ByteArray>,
) {
    init {
        require(version > 0) { "SaveData version must be positive." }
    }

    fun section(id: String): ByteArray? = sections[id]

    fun hasContent(): Boolean = sections.values.any { it.isNotEmpty() }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * The explicit outcome of reading a save. Only [Found] and [Missing] are definite answers;
 * a [Failed] read or an [Undecodable] envelope says nothing about what the save holds, so a
 * caller must never treat either as "no save" and overwrite it.
 */
sealed interface SaveLoadResult {
    data class Found(
        val data: SaveData,
    ) : SaveLoadResult

    /** The storage answered and holds no save. */
    data object Missing : SaveLoadResult

    /** This environment has no such storage at all; nothing may be written to it either. */
    data object Unavailable : SaveLoadResult

    /** The storage could not be read this time. */
    data class Failed(
        val cause: Throwable,
    ) : SaveLoadResult

    /** A save exists but is corrupt or was written in an unknown (newer) envelope version. */
    data class Undecodable(
        val reason: String,
    ) : SaveLoadResult
}

/** Loads and saves [SaveData]. */
interface SaveRepository {
    suspend fun load(): SaveLoadResult

    suspend fun save(data: SaveData): Boolean
}

/** Where saves live. Cloud implementations are Player-scoped; local fallbacks stay isolated too. */
interface CloudSaveProvider {
    val available: Boolean

    fun repository(playerId: String?): SaveRepository
}
