package com.stanisryz.logica.web

import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class YandexPlayerIdentityGateway(
    private val bridge: YandexGamesBridge,
) : PlayerIdentityGateway {
    override suspend fun identity(): PlayerIdentity =
        try {
            bridge.playerSnapshot().toIdentity()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            anonymousYandexIdentity()
        }

    override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported

    private fun YandexPlayerSnapshot.toIdentity(): PlayerIdentity =
        PlayerIdentity(
            playerId = uniqueId,
            displayName = displayName,
            avatarReference = avatarReference,
            authorizationState =
                if (isAuthorized) {
                    PlayerAuthorizationState.AUTHORIZED
                } else {
                    PlayerAuthorizationState.ANONYMOUS
                },
            provider = YANDEX_PROVIDER,
        )
}

/** The Player data calls of [YandexGamesBridge] that the cloud gateways use. */
internal interface WebPlayerDataBridge {
    suspend fun readPlayerData(key: String): String?

    suspend fun writePlayerData(
        key: String,
        value: String,
        flush: Boolean,
    )
}

/**
 * One Player data key in the Yandex cloud, for the unified save and the legacy keys alike. A
 * `getData` without an answer within [readTimeoutMs] or a `setData` without one within
 * [writeTimeoutMs] is a failed call — the restore stays unresolved and retries, the write retries
 * — and its late answer is ignored.
 */
internal class YandexCloudSaveGateway(
    private val bridge: WebPlayerDataBridge,
    private val dataKey: String = CLOUD_STATE_KEY,
    private val readTimeoutMs: Long = READ_TIMEOUT_MS,
    private val writeTimeoutMs: Long = WRITE_TIMEOUT_MS,
    /** Shared by every key, so all `setData` calls together stay within Yandex's rate limit. */
    private val pacer: WebCloudWritePacer? = null,
    /**
     * False for the legacy keys: they are only read (the migration path). Every `setData` then
     * carries the one unified key, which is safe whether Yandex merges keys or replaces all of a
     * Player's data with the object it is given.
     */
    private val writable: Boolean = true,
) : CloudSaveGateway {
    init {
        require(dataKey.isNotBlank()) { "A Yandex Cloud Save data key is required." }
    }

    override val availability: CloudSaveAvailability = CloudSaveAvailability.AVAILABLE

    override suspend fun read(): CloudSaveReadResult =
        try {
            withTimeoutOrNull(readTimeoutMs) {
                val encoded = bridge.readPlayerData(dataKey) ?: return@withTimeoutOrNull CloudSaveReadResult.Missing
                val payload = WebBase64.decode(encoded) ?: error("Yandex cloud save payload is not valid Base64.")
                CloudSaveReadResult.Found(payload)
            } ?: CloudSaveReadResult.Failed(IllegalStateException("Yandex getData did not answer in time."))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CloudSaveReadResult.Failed(error)
        }

    override suspend fun write(payload: ByteArray): CloudSaveWriteResult =
        when {
            !writable -> CloudSaveWriteResult.Unsupported
            pacer != null -> pacer.write(dataKey) { timedWrite(payload) }
            else -> timedWrite(payload)
        }

    // The timeout covers the SDK call only, never the wait for the pacer's turn.
    private suspend fun timedWrite(payload: ByteArray): CloudSaveWriteResult =
        try {
            withTimeoutOrNull(writeTimeoutMs) {
                bridge.writePlayerData(
                    key = dataKey,
                    value = WebBase64.encode(payload),
                    flush = true,
                )
                CloudSaveWriteResult.Saved
            } ?: CloudSaveWriteResult.Failed(IllegalStateException("Yandex setData did not answer in time."))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CloudSaveWriteResult.Failed(error)
        }

    companion object {
        const val UNIFIED_STATE_KEY = "logica_unified_save_v1"
        const val CLOUD_STATE_KEY = "logica_state_v1"
        const val STATISTICS_STATE_KEY = "logica_statistics_v1"
        const val DAILY_STATE_KEY = "logica_daily_v1"
        const val READ_TIMEOUT_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 15_000L
    }
}

/**
 * The Yandex cloud keys of one game: the unified save, the only key ever written, and the legacy
 * Catalog/Statistics/Daily keys, read-only and kept for migration. `setData` may replace all of a
 * Player's data with the object it is given, so a write of any other key could erase the unified save.
 */
internal class YandexCloudGateways(
    bridge: WebPlayerDataBridge,
    pacer: WebCloudWritePacer? = null,
) {
    val unified = YandexCloudSaveGateway(bridge, dataKey = YandexCloudSaveGateway.UNIFIED_STATE_KEY, pacer = pacer)
    val catalog = YandexCloudSaveGateway(bridge, dataKey = YandexCloudSaveGateway.CLOUD_STATE_KEY, writable = false)
    val statistics = YandexCloudSaveGateway(bridge, dataKey = YandexCloudSaveGateway.STATISTICS_STATE_KEY, writable = false)
    val daily = YandexCloudSaveGateway(bridge, dataKey = YandexCloudSaveGateway.DAILY_STATE_KEY, writable = false)
}

internal object UnsupportedWebPlayerIdentityGateway : PlayerIdentityGateway {
    private val identity =
        PlayerIdentity(
            authorizationState = PlayerAuthorizationState.UNSUPPORTED,
            provider = "web-local",
        )

    override suspend fun identity(): PlayerIdentity = identity

    override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
}

internal object UnsupportedWebCloudSaveGateway : CloudSaveGateway {
    override val availability: CloudSaveAvailability = CloudSaveAvailability.UNSUPPORTED

    override suspend fun read(): CloudSaveReadResult = CloudSaveReadResult.Unsupported

    override suspend fun write(payload: ByteArray): CloudSaveWriteResult = CloudSaveWriteResult.Unsupported
}

private fun anonymousYandexIdentity(): PlayerIdentity =
    PlayerIdentity(
        authorizationState = PlayerAuthorizationState.ANONYMOUS,
        provider = YANDEX_PROVIDER,
    )

private const val YANDEX_PROVIDER = "yandex-games"

/** Small binary-to-text codec used only at Web storage boundaries. */
internal object WebBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val result = StringBuilder(((bytes.size + 2) / 3) * 4)
        var index = 0
        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xff
            val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else 0
            val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else 0
            val bits = (first shl 16) or (second shl 8) or third
            result.append(ALPHABET[(bits ushr 18) and 0x3f])
            result.append(ALPHABET[(bits ushr 12) and 0x3f])
            result.append(if (index + 1 < bytes.size) ALPHABET[(bits ushr 6) and 0x3f] else '=')
            result.append(if (index + 2 < bytes.size) ALPHABET[bits and 0x3f] else '=')
            index += 3
        }
        return result.toString()
    }

    fun decode(text: String): ByteArray? =
        runCatching {
            require(text.length % 4 == 0)
            if (text.isEmpty()) return@runCatching ByteArray(0)
            val padding =
                when {
                    text.endsWith("==") -> 2
                    text.endsWith('=') -> 1
                    else -> 0
                }
            require('=' !in text.dropLast(padding))
            val result = ByteArray((text.length / 4) * 3 - padding)
            var output = 0
            var index = 0
            while (index < text.length) {
                val a = alphabetIndex(text[index])
                val b = alphabetIndex(text[index + 1])
                val c = if (text[index + 2] == '=') 0 else alphabetIndex(text[index + 2])
                val d = if (text[index + 3] == '=') 0 else alphabetIndex(text[index + 3])
                val bits = (a shl 18) or (b shl 12) or (c shl 6) or d
                if (output < result.size) result[output++] = (bits ushr 16).toByte()
                if (output < result.size) result[output++] = (bits ushr 8).toByte()
                if (output < result.size) result[output++] = bits.toByte()
                index += 4
            }
            result
        }.getOrNull()

    private fun alphabetIndex(char: Char): Int {
        val index = ALPHABET.indexOf(char)
        require(index >= 0)
        return index
    }
}
