@file:OptIn(ExperimentalWasmJsInterop::class)

package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerProvider
import com.stanisryz.logica.platform.SaveData
import com.stanisryz.logica.platform.SaveLoadResult
import com.stanisryz.logica.platform.SaveRepository
import kotlinx.coroutines.CancellationException
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

    /**
     * Decodes a stored envelope into a load result: a corrupt envelope, or one written in a
     * newer envelope version this build does not know, is [SaveLoadResult.Undecodable].
     */
    fun decodeForLoad(payload: ByteArray): SaveLoadResult {
        val data = decode(payload) ?: return SaveLoadResult.Undecodable("Corrupt unified save envelope.")
        if (data.version > SaveData.CURRENT_VERSION) {
            return SaveLoadResult.Undecodable("Unknown unified save envelope version ${data.version}.")
        }
        return SaveLoadResult.Found(data)
    }

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
    override suspend fun load(): SaveLoadResult =
        when (val result = gateway.read()) {
            is CloudSaveReadResult.Found -> WebSaveCodec.decodeForLoad(result.payload)
            CloudSaveReadResult.Missing -> SaveLoadResult.Missing
            CloudSaveReadResult.Unsupported -> SaveLoadResult.Unavailable
            is CloudSaveReadResult.Failed -> SaveLoadResult.Failed(result.cause)
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

    override suspend fun load(): SaveLoadResult {
        current?.let { return SaveLoadResult.Found(it) }
        val encoded =
            try {
                loadRaw(storageKey)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return SaveLoadResult.Failed(error)
            } ?: return SaveLoadResult.Missing
        val payload = WebBase64.decode(encoded) ?: return SaveLoadResult.Undecodable("Local save is not valid Base64.")
        return WebSaveCodec.decodeForLoad(payload).also { if (it is SaveLoadResult.Found) current = it.data }
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

    /**
     * Merges one cloud payload through the domain's own semantics. Returns false when the payload
     * cannot be decoded by this section's codec or its merge did not land durably in local
     * state, so restore never mistakes an unmerged section for a restored one; an unbound domain
     * simply skips a decodable payload (the manager carries its bytes forward).
     */
    fun apply(payload: ByteArray): Boolean

    /**
     * Restore entry point receiving every owned section payload present in the cloud envelope;
     * false when any of them cannot be decoded.
     */
    fun applyRestoring(payloads: Map<String, ByteArray>): Boolean = payloads[id]?.let(::apply) ?: true
}

/** What [WebSaveManager.restore] learned about the stored unified save. */
internal enum class WebSaveRestoreOutcome {
    /** A save was read and every section in it was decoded and merged. */
    RESTORED,

    /** The storage definitely holds no save (or an envelope with no content). */
    EMPTY,

    /**
     * The read failed, the envelope is undecodable, or a section in it is unreadable or unknown:
     * whatever decoded was merged, but the stored save must not be overwritten.
     */
    UNRESOLVED,

    /** This environment has no such storage; nothing is read from or written to it. */
    UNAVAILABLE,
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
 * instead of silently dropping history or inventory. A known section its domain does not
 * export right now (an unbound domain, an empty value) is carried forward byte for byte from
 * the envelope last restored or written for this context, so it never vanishes from the cloud.
 */
internal class WebSaveManager(
    private val sections: List<WebSaveSection>,
    private val repository: SaveRepository,
    private val maxPayloadBytes: Int = DEFAULT_MAX_UNIFIED_PAYLOAD_BYTES,
) {
    /** Raw section bytes of the envelope last restored (RESTORED only) or written; per context. */
    private var carriedSections: Map<String, ByteArray> = emptyMap()

    /** Bumped by every restore, so a write that finishes after a newer restore never refills the cache. */
    private var restoreGeneration = 0

    suspend fun restore(): WebSaveRestoreOutcome {
        // A new restore starts a new context: Player B never inherits Player A's sections.
        restoreGeneration += 1
        carriedSections = emptyMap()
        val data =
            when (val result = repository.load()) {
                is SaveLoadResult.Found -> result.data
                SaveLoadResult.Missing -> return WebSaveRestoreOutcome.EMPTY
                SaveLoadResult.Unavailable -> return WebSaveRestoreOutcome.UNAVAILABLE
                is SaveLoadResult.Failed, is SaveLoadResult.Undecodable -> return WebSaveRestoreOutcome.UNRESOLVED
            }
        if (!data.hasContent()) return WebSaveRestoreOutcome.EMPTY
        // Explicit coupled-group resolution: each section id maps to its owning section, and
        // every owner is invoked exactly once with all payloads it owns, regardless of the
        // order in which the section adapters were registered.
        val ownerOf = HashMap<String, WebSaveSection>()
        sections.forEach { section -> ownerOf[section.id] = section }
        // Coupled claims win over a section's own id (Store belongs to the Economy group), so the
        // order in which the adapters were registered can never hand Store to its own adapter.
        sections.forEach { section -> section.coupledIds.forEach { coupledId -> ownerOf[coupledId] = section } }
        // A section id this build does not know (written by a newer build) cannot be carried
        // into a rewritten envelope, so its save stays unresolved rather than being dropped.
        var resolved = data.sections.keys.all { it in ownerOf }
        val processed = HashSet<WebSaveSection>()
        sections.forEach { section ->
            val owner = ownerOf.getValue(section.id)
            if (!processed.add(owner)) return@forEach
            val ownedIds = ownerOf.filterValues { it === owner }.keys
            val payloads =
                ownedIds.mapNotNull { id -> data.section(id)?.let { id to it } }.toMap()
            // Decodable sections still merge (every merge is monotonic and safe to repeat).
            if (!runCatching { owner.applyRestoring(payloads) }.getOrDefault(false)) resolved = false
        }
        if (!resolved) return WebSaveRestoreOutcome.UNRESOLVED
        carriedSections = data.sections
        return WebSaveRestoreOutcome.RESTORED
    }

    suspend fun persist(): Boolean {
        val generation = restoreGeneration
        val carried = carriedSections
        val sectionsById =
            sections
                .mapNotNull { section -> (section.export() ?: carried[section.id])?.let { section.id to it } }
                .toMap()
        if (sectionsById.isEmpty()) return false
        val data = SaveData(sections = sectionsById)
        // Payload safety: the Yandex Player data budget is finite and shared by all sections,
        // carried-forward ones included.
        require(WebSaveCodec.encode(data).size <= maxPayloadBytes) {
            "Unified save payload exceeds the supported Player data budget."
        }
        val saved = runCatching { repository.save(data) }.getOrDefault(false)
        // The cache follows what this context last wrote, unless a newer restore began meanwhile.
        if (saved && restoreGeneration == generation) carriedSections = sectionsById
        return saved
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
    const val STARS = "stars"
    const val BEST_2048 = "best2048"
    const val REWARDS = "rewards"
}

/**
 * Section adapters over the currently bound Player repositories. Sections resolve the binding
 * dynamically at export/apply time, so Player switches are naturally honored and no business
 * logic is modified — cloud payloads only round-trip through each domain's existing codec/merge.
 */
internal class WebSaveSections(
    private val playerSession: WebPlayerSessionController,
) {
    private var pendingEconomyRestore: WebEconomySnapshot? = null

    fun all(): List<WebSaveSection> =
        listOf(
            catalogSection(),
            statisticsSection(),
            dailySection(),
            economySection(),
            storeSection(),
            paymentsSection(),
            starsSection(),
            best2048Section(),
            rewardsSection(),
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

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebCatalogProgressCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository ?: return true
                return repository.mergeCloud(cloud) is WebCatalogMergeResult.Merged
            }
        }

    /** Best stars per Catalog level, beside progress in the same binding; merges by maximum. */
    private fun starsSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.STARS

            override fun export(): ByteArray? =
                (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)
                    ?.repository
                    ?.stars
                    ?.value
                    ?.takeIf { it.levels.isNotEmpty() }
                    ?.let { WebCatalogStarsCodec.encode(it) }

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebCatalogStarsCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository ?: return true
                return repository.mergeCloudStars(cloud) is WebCloudValueMergeResult.Merged
            }
        }

    /** The best 2048 score, beside progress in the same binding; merges by maximum. */
    private fun best2048Section(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.BEST_2048

            override fun export(): ByteArray? =
                (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)
                    ?.repository
                    ?.best2048
                    ?.value
                    ?.takeIf { it > 0L }
                    ?.let(WebBestScoreCodec::encode)

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebBestScoreCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository ?: return true
                return repository.mergeCloudBest2048(cloud) is WebCloudValueMergeResult.Merged
            }
        }

    /** Daily quests and the login gift, beside progress in the same binding; merges by day. */
    private fun rewardsSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.REWARDS

            override fun export(): ByteArray? =
                (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)
                    ?.repository
                    ?.rewards
                    ?.value
                    ?.takeIf { it != WebDailyRewardsSnapshot.EMPTY }
                    ?.let(WebDailyRewardsCodec::encode)

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebDailyRewardsCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.progressBinding.value as? WebCatalogProgressBinding.Ready)?.repository ?: return true
                return repository.mergeCloudRewards(cloud) is WebCloudValueMergeResult.Merged
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

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebStatisticsCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.statisticsBinding.value as? WebStatisticsBinding.Ready)?.repository ?: return true
                // Invalid (the merge itself threw) and PersistenceFailed both leave the cloud unmerged.
                return repository.mergeCloud(cloud) is WebStatisticsMergeResult.Merged
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

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebDailyCodec.decode(payload) ?: return false
                val repository =
                    (playerSession.dailyBinding.value as? WebDailyBinding.Ready)?.repository ?: return true
                return when (repository.mergeCloud(cloud)) {
                    is WebDailyMergeResult.Merged -> true
                    // A same-date policy conflict is the domain's deliberate refusal, not a failure.
                    is WebDailyMergeResult.PolicyConflict -> true
                    is WebDailyMergeResult.PersistenceFailed -> false
                }
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

            override fun apply(payload: ByteArray): Boolean {
                // Never used: the coupled group always routes through applyRestoring.
                error("Economy/Store sections must be restored through the coupled group.")
            }

            override fun applyRestoring(payloads: Map<String, ByteArray>): Boolean {
                val economyPayload = payloads[WebSaveSectionIds.ECONOMY]
                val storePayload = payloads[WebSaveSectionIds.STORE]
                val cloudEconomy = economyPayload?.let(WebEconomyCodec::decode)
                val cloudStore = storePayload?.let(WebStoreCodec::decode)
                if ((economyPayload != null && cloudEconomy == null) || (storePayload != null && cloudStore == null)) return false
                val economyRepository = playerSession.economyRepository ?: return true
                val storeRepository = playerSession.storeRepository ?: return true
                val decision =
                    WebEconomyStoreCoupledRestore.resolve(
                        localEconomy = economyRepository.currentSnapshot,
                        localStore = storeRepository.snapshot.value,
                        cloudEconomy = cloudEconomy,
                        cloudStore = cloudStore,
                    )
                val targetEconomy = decision.economy ?: return true
                val targetStore = decision.store ?: return true
                // Pair-consistent application: if either side cannot be persisted durably,
                // the previous local pair stays authoritative and observable, and the older local
                // pair must not overwrite the newer cloud pair, so the group is unresolved.
                return WebEconomyStorePairApply.apply(economyRepository, storeRepository, targetEconomy, targetStore)
            }
        }

    private fun storeSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.STORE

            override fun export(): ByteArray? = playerSession.storeRepository?.let { WebStoreCodec.encode(it.snapshot.value) }

            override fun apply(payload: ByteArray): Boolean {
                // Never used: restoration of both domains is owned by the economy section.
                error("Economy/Store sections must be restored through the coupled group.")
            }
        }

    /** Fulfilled purchase-token ledger; cloud restore unions both devices' knowledge. */
    private fun paymentsSection(): WebSaveSection =
        object : WebSaveSection {
            override val id = WebSaveSectionIds.PAYMENTS

            override fun export(): ByteArray? = playerSession.paymentsRepository?.let { WebPaymentsCodec.encode(it.snapshot.value) }

            override fun apply(payload: ByteArray): Boolean {
                val cloud = WebPaymentsCodec.decode(payload) ?: return false
                val repository = playerSession.paymentsRepository ?: return true
                return when (repository.mergeCloud(cloud)) {
                    WebExternalRestoreResult.Applied, WebExternalRestoreResult.NoChange -> true
                    is WebExternalRestoreResult.PersistenceFailed, WebExternalRestoreResult.Rejected -> false
                }
            }
        }
}
