@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerProvider
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveRepository
import kotlin.js.ExperimentalWasmJsInterop

/** Provides the current Yandex Player identity through the existing SDK bridge; no login UI. */
internal class YandexPlayerProvider(
    private val gateway: com.stanisryz.logica.platform.PlayerIdentityGateway,
) : PlayerProvider {
    override suspend fun currentPlayer(): PlayerIdentity? = gateway.identity()

    override fun isIdentityAvailable(): Boolean = gateway is com.stanisryz.logica.platform.PlayerIdentityGateway
}

/** Serializes the versioned [SaveData] envelope into a compact deterministic binary payload. */
internal object WebSaveCodec {
    private val magic = byteArrayOf('L'.code.toByte(), 'G'.code.toByte(), 'S'.code.toByte(), 'A'.code.toByte())
    private const val MAX_SECTION_ID_LENGTH = 32

    fun encode(data: SaveData): ByteArray {
        val sections = data.sections.entries.sortedBy { it.key }
        require(sections.all { it.key.length in 1..MAX_SECTION_ID_LENGTH })

        var size = 4 + 4 + 2
        sections.forEach { size += 1 + it.key.encodeToByteArray().size + 4 + it.value.size }

        val result = ByteArray(size)
        magic.copyInto(result)
        writeInt(result, 4, data.version)
        result[8] = ((sections.size ushr 8) and 0xff).toByte()
        result[9] = (sections.size and 0xff).toByte()
        var offset = 10
        sections.forEach { (id, payload) ->
            val idBytes = id.encodeToByteArray()
            result[offset] = idBytes.size.toByte()
            idBytes.copyInto(result, offset + 1)
            offset += 1 + idBytes.size
            writeInt(result, offset, payload.size)
            offset += 4
            payload.copyInto(result, offset)
            offset += payload.size
        }
        return result
    }

    fun decode(payload: ByteArray): SaveData? =
        runCatching {
            require(payload.size >= 10)
            require(magic.indices.all { payload[it] == magic[it] })
            val version = readInt(payload, 4)
            require(version > 0)
            val sectionCount = ((payload[8].toInt() and 0xff) shl 8) or (payload[9].toInt() and 0xff)

            var offset = 10
            val sections = linkedMapOf<String, ByteArray>()
            repeat(sectionCount) {
                require(offset + 1 <= payload.size)
                val idLength = payload[offset].toInt() and 0xff
                require(idLength in 1..MAX_SECTION_ID_LENGTH)
                require(offset + 1 + idLength + 4 <= payload.size)
                val id = payload.copyOfRange(offset + 1, offset + 1 + idLength).decodeToString()
                offset += 1 + idLength
                val length = readInt(payload, offset)
                offset += 4
                require(length >= 0 && offset + length <= payload.size) { "Corrupt SaveData section payload." }
                sections[id] = payload.copyOfRange(offset, offset + length)
                offset += length
            }
            SaveData(version = version, sections = sections)
        }.getOrNull()

    private fun writeInt(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        destination[offset] = (value ushr 24).toByte()
        destination[offset + 1] = (value ushr 16).toByte()
        destination[offset + 2] = (value ushr 8).toByte()
        destination[offset + 3] = value.toByte()
    }

    private fun readInt(
        source: ByteArray,
        offset: Int,
    ): Int = ((source[offset].toInt() and 0xff) shl 24) or
        ((source[offset + 1].toInt() and 0xff) shl 16) or
        ((source[offset + 2].toInt() and 0xff) shl 8) or
        (source[offset + 3].toInt() and 0xff)
}

/** Yandex Player-scoped cloud repository over the existing [CloudSaveGateway]. */
internal class YandexCloudSaveRepository(
    private val gateway: CloudSaveGateway,
) : SaveRepository {
    override suspend fun load(): SaveData? =
        when (val result = gateway.read()) {
            is CloudSaveReadResult.Found -> WebSaveCodec.decode(result.payload)
            CloudSaveReadResult.Missing, CloudSaveReadResult.Unsupported -> null
            is CloudSaveReadResult.Failed -> null
        }

    override suspend fun save(data: SaveData): Boolean =
        when (gateway.write(WebSaveCodec.encode(data))) {
            CloudSaveWriteResult.Saved -> true
            else -> false
        }
}

/**
 * Local fallback repository for standalone/development and unsupported environments. Storage
 * access is injected so tests stay deterministic without a browser.
 */
internal class LocalSaveRepository(
    private val storageKey: String,
    private val loadRaw: (String) -> String?,
    private val saveRaw: (String, String) -> Unit,
) : SaveRepository {
    private var current: SaveData? = null

    override suspend fun load(): SaveData? {
        current?.let { return it }
        val encoded = loadRaw(storageKey) ?: return null
        val payload = WebBase64.decode(encoded) ?: return null
        return WebSaveCodec.decode(payload)?.also { current = it }
    }

    override suspend fun save(data: SaveData): Boolean {
        saveRaw(storageKey, WebBase64.encode(WebSaveCodec.encode(data)))
        current = data
        return true
    }
}

/** One domain's participation in the unified save. Sections export/apply opaque payloads only. */
internal interface WebSaveSection {
    val id: String

    fun export(): ByteArray?

    fun apply(payload: ByteArray)
}

/**
 * Central save coordinator. Pure orchestration over [SaveRepository] and domain sections — it
 * contains no platform-specific code and never interprets or modifies business state itself.
 *
 * Load flow: identity -> repository.load() -> apply present sections (cloud is authoritative).
 * Save flow: collect all exported sections -> repository.save().
 */
internal class WebSaveManager(
    private val sections: List<WebSaveSection>,
    private val repository: SaveRepository,
) {
    suspend fun restore(): Boolean {
        val data = repository.load() ?: return false
        if (!data.hasContent()) return false
        sections.forEach { section ->
            data.section(section.id)?.let(section::apply)
        }
        return true
    }

    suspend fun persist(): Boolean {
        val sectionsById =
            sections
                .mapNotNull { section -> section.export()?.let { section.id to it } }
                .toMap()
        if (sectionsById.isEmpty()) return false
        return repository.save(SaveData(sections = sectionsById))
    }
}

/** Stable unified-save section ids; each maps to exactly one domain codec. */
internal object WebSaveSectionIds {
    const val CATALOG = "catalog"
    const val STATISTICS = "statistics"
    const val DAILY = "daily"
    const val ECONOMY = "economy"
    const val STORE = "store"
}

/**
 * Section adapters over the currently bound Player repositories. Sections resolve the binding
 * dynamically at export/apply time, so Player switches are naturally honored and no business
 * logic is modified — cloud payloads only round-trip through each domain's existing codec/merge.
 */
internal class WebSaveSections(
    private val playerSession: WebPlayerSessionController,
) {
    fun all(): List<WebSaveSection> =
        listOf(
            catalogSection(),
            statisticsSection(),
            dailySection(),
            economySection(),
            storeSection(),
        )

    private fun catalogSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.CATALOG

            override fun export(): ByteArray? =
                (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)
                    ?.repository?.snapshot?.value
                    ?.let { WebCatalogProgressCodec.encode(it) }

            override fun apply(payload: ByteArray) {
                val repository =
                    (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository ?: return
                val cloud = WebCatalogProgressCodec.decode(payload) ?: return
                repository.mergeCloud(cloud)
            }
        }

    private fun statisticsSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.STATISTICS

            override fun export(): ByteArray? =
                (playerSession.statisticsBinding.value as? WebStatisticsBinding.Ready)
                    ?.repository?.snapshot?.value
                    ?.let { WebStatisticsCodec.encode(it) }

            override fun apply(payload: ByteArray) {
                val repository =
                    (playerSession.statisticsBinding.value as? WebStatisticsBinding.Ready)?.repository ?: return
                val cloud = WebStatisticsCodec.decode(payload) ?: return
                runCatching { repository.mergeCloud(cloud) }
            }
        }

    private fun dailySection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.DAILY

            override fun export(): ByteArray? =
                (playerSession.dailyBinding.value as? WebDailyBinding.Ready)
                    ?.repository?.snapshot?.value
                    ?.let { WebDailyCodec.encode(it) }

            override fun apply(payload: ByteArray) {
                val repository =
                    (playerSession.dailyBinding.value as? WebDailyBinding.Ready)?.repository ?: return
                val cloud = WebDailyCodec.decode(payload) ?: return
                runCatching { repository.mergeCloud(cloud) }
            }
        }

    private fun economySection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.ECONOMY

            override fun export(): ByteArray? =
                playerSession.economyRepository?.let {
                    WebEconomyCodec.encode(it.state.value.toWebEconomySnapshot())
                }

            override fun apply(payload: ByteArray) {
                val snapshot = WebEconomyCodec.decode(payload) ?: return
                playerSession.economyRepository?.applyExternal(snapshot)
            }
        }

    private fun storeSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.STORE

            override fun export(): ByteArray? =
                playerSession.storeRepository?.let { WebStoreCodec.encode(it.snapshot.value) }

            override fun apply(payload: ByteArray) {
                val snapshot = WebStoreCodec.decode(payload) ?: return
                playerSession.storeRepository?.applyExternal(snapshot)
            }
        }
}



