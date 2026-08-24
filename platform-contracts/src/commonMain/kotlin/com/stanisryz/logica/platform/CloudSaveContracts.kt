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

/** Loads and saves [SaveData]; implementations return null when nothing is available. */
interface SaveRepository {
    suspend fun load(): SaveData?

    suspend fun save(data: SaveData): Boolean
}

/** Where saves live. Cloud implementations are Player-scoped; local fallbacks stay isolated too. */
interface CloudSaveProvider {
    val available: Boolean

    fun repository(playerId: String?): SaveRepository
}
