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
    ): Int =
        ((source[offset].toInt() and 0xff) shl 24) or
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

    /**
     * Extra envelope section ids this section resolves together with its own during restore.
     * The owner of an id restores every id it owns as one explicit coupled group, so restore
     * never depends on section ordering inside [WebSaveManager]'s section list.
     */
    val coupledIds: List<String>
        get() = emptyList()

    fun export(): ByteArray?

    fun apply(payload: ByteArray)

    /** Restore entry point receiving every owned section payload present in the cloud envelope. */
    fun applyRestoring(payloads: Map<String, ByteArray>) {
        payloads[id]?.let(::apply)
    }
}

/**
 * One monotonic mutation timeline shared by the Economy and Store repositories of one bound
 * Player context. Every durable mutation consumes the next revision, so purchases stay
 * comparable across domains — the property that keeps coupled wallet/inventory restores safe.
 */
internal class WebPlayerStateRevisions {
    private var current = 0L

    /** Allocates the next revision on the shared Player-state timeline. */
    fun next(): Long = ++current

    /** Keeps the timeline ahead of every revision observed from local or cloud snapshots. */
    fun raiseTo(minimum: Long) {
        if (minimum > current) current = minimum
    }
}

/**
 * Deterministic coupled restore decision for the Economy/Store pair. A purchase changes both
 * domains together, so restore never mixes one side's wallet with the other side's inventory:
 * the whole pair is taken from whichever generation is newer, or local is kept on ties and for
 * partial payloads. This yields only states that actually existed on some device.
 */
internal object WebEconomyStoreCoupledRestore {
    data class Decision(
        val economy: WebEconomySnapshot?,
        val store: WebStoreSnapshot?,
    )

    fun resolve(
        localEconomy: WebEconomySnapshot,
        localStore: WebStoreSnapshot,
        cloudEconomy: WebEconomySnapshot?,
        cloudStore: WebStoreSnapshot?,
    ): Decision {
        // Partial unified payloads are abnormal; fail safe by keeping both local domains.
        if ((cloudEconomy == null) != (cloudStore == null)) return Decision(null, null)
        if (cloudEconomy == null || cloudStore == null) return Decision(null, null)
        val cloudRecency = maxOf(cloudEconomy.revision, cloudStore.revision)
        val localRecency = maxOf(localEconomy.revision, localStore.revision)
        return if (cloudRecency > localRecency) Decision(cloudEconomy, cloudStore) else Decision(null, null)
    }
}

/**
 * Central save coordinator. Pure orchestration over [SaveRepository] and domain sections — it
 * contains no platform-specific code and never interprets or modifies business state itself.
 *
 * Load flow: identity -> repository.load() -> apply present sections. Each section adapter
 * merges through its own domain semantics (monotonic Catalog levels, unioned statistics,
 * policy-safe Daily history, revision-compared whole-pair Economy/Store); a unified cloud
 * payload never blindly overwrites newer valid local Player state.
 *
 * Save flow: collect all exported sections -> validate the envelope against the Player data
 * budget -> repository.save(). Writes are all-or-nothing; oversized payloads fail safely
 * instead of silently dropping history or inventory.
 */
internal class WebSaveManager(
    private val sections: List<WebSaveSection>,
    private val repository: SaveRepository,
    private val maxPayloadBytes: Int = DEFAULT_MAX_UNIFIED_PAYLOAD_BYTES,
) {
    suspend fun restore(): Boolean {
        val data = repository.load() ?: return false
        if (!data.hasContent()) return false
        // Explicit coupled-group resolution: each section id maps to its owning section, and
        // every owner is invoked exactly once with all payloads it owns, regardless of the
        // order in which the section adapters were registered.
        val ownerOf = HashMap<String, WebSaveSection>()
        sections.forEach { section ->
            ownerOf[section.id] = section
            section.coupledIds.forEach { coupledId -> ownerOf[coupledId] = section }
        }
        val processed = HashSet<WebSaveSection>()
        sections.forEach { section ->
            val owner = ownerOf.getValue(section.id)
            if (!processed.add(owner)) return@forEach
            val ownedIds = ownerOf.filterValues { it === owner }.keys
            val payloads =
                ownedIds.mapNotNull { id -> data.section(id)?.let { id to it } }.toMap()
            owner.applyRestoring(payloads)
        }
        return true
    }

    suspend fun persist(): Boolean {
        val sectionsById =
            sections
                .mapNotNull { section -> section.export()?.let { section.id to it } }
                .toMap()
        if (sectionsById.isEmpty()) return false
        val data = SaveData(sections = sectionsById)
        // Payload safety: the Yandex Player data budget is finite and shared by all sections.
        require(WebSaveCodec.encode(data).size <= maxPayloadBytes) {
            "Unified save payload exceeds the supported Player data budget."
        }
        return runCatching { repository.save(data) }.getOrDefault(false)
    }
}

/** Conservative default envelope budget; Yandex Player data storage stays well below this. */
private const val DEFAULT_MAX_UNIFIED_PAYLOAD_BYTES = 100_000

/** Stable unified-save section ids; each maps to exactly one domain codec. */
internal object WebSaveSectionIds {
    const val CATALOG = "catalog"
    const val STATISTICS = "statistics"
    const val DAILY = "daily"
    const val ECONOMY = "economy"
    const val STORE = "store"
    const val PAYMENTS = "payments"
}

/**
 * Section adapters over the currently bound Player repositories. Sections resolve the binding
 * dynamically at export/apply time, so Player switches are naturally honored and no business
 * logic is modified — cloud payloads only round-trip through each domain's existing codec/merge.
 */
internal class WebSaveSections(
    private val playerSession: WebPlayerSessionController,
) {
    /** Economy resolves jointly with Store, so `all()` must keep this exact section order. */
    private var pendingEconomyRestore: WebEconomySnapshot? = null

    fun all(): List<WebSaveSection> =
        listOf(
            catalogSection(),
            statisticsSection(),
            dailySection(),
            economySection(),
            storeSection(),
            paymentsSection(),
        )

    private fun catalogSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.CATALOG

            override fun export(): ByteArray? =
                (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)
                    ?.repository
                    ?.snapshot
                    ?.value
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
                    ?.repository
                    ?.snapshot
                    ?.value
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
                    ?.repository
                    ?.snapshot
                    ?.value
                    ?.let { WebDailyCodec.encode(it) }

            override fun apply(payload: ByteArray) {
                val repository =
                    (playerSession.dailyBinding.value as? WebDailyBinding.Ready)?.repository ?: return
                val cloud = WebDailyCodec.decode(payload) ?: return
                runCatching { repository.mergeCloud(cloud) }
            }
        }

    /**
     * Economy is the owner of the coupled Economy+Store restore group: both sections are
     * decoded explicitly here and resolved/applied as ONE consistent pair, independently of
     * section ordering. Store remains a separate export/persist entry in the envelope.
     */
    private fun economySection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.ECONOMY
            override val coupledIds = listOf(WebSaveSectionIds.STORE)

            override fun export(): ByteArray? = playerSession.economyRepository?.let { WebEconomyCodec.encode(it.currentSnapshot) }

            override fun apply(payload: ByteArray) {
                // Never used: the coupled group always routes through applyRestoring.
                error("Economy/Store sections must be restored through the coupled group.")
            }

            override fun applyRestoring(payloads: Map<String, ByteArray>) {
                val economyRepository = playerSession.economyRepository ?: return
                val storeRepository = playerSession.storeRepository ?: return
                val cloudEconomy = payloads[WebSaveSectionIds.ECONOMY]?.let(WebEconomyCodec::decode)
                val cloudStore = payloads[WebSaveSectionIds.STORE]?.let(WebStoreCodec::decode)
                val decision =
                    WebEconomyStoreCoupledRestore.resolve(
                        localEconomy = economyRepository.currentSnapshot,
                        localStore = storeRepository.snapshot.value,
                        cloudEconomy = cloudEconomy,
                        cloudStore = cloudStore,
                    )
                val targetEconomy = decision.economy ?: return
                val targetStore = decision.store ?: return
                // Pair-consistent application: if either side cannot be persisted durably,
                // the previous local pair stays authoritative and observable.
                WebEconomyStorePairApply.apply(economyRepository, storeRepository, targetEconomy, targetStore)
            }
        }

    private fun storeSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.STORE

            override fun export(): ByteArray? = playerSession.storeRepository?.let { WebStoreCodec.encode(it.snapshot.value) }

            override fun apply(payload: ByteArray) {
                // Never used: restoration of both domains is owned by the economy section.
                error("Economy/Store sections must be restored through the coupled group.")
            }
        }

    /** Fulfilled purchase-token ledger; cloud restore unions both devices' knowledge. */
    private fun paymentsSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.PAYMENTS

            override fun export(): ByteArray? =
                playerSession.paymentsRepository?.let { WebPaymentsCodec.encode(it.snapshot.value) }

            override fun apply(payload: ByteArray) {
                val repository = playerSession.paymentsRepository ?: return
                val cloud = WebPaymentsCodec.decode(payload) ?: return
                repository.mergeCloud(cloud)
            }
        }
}