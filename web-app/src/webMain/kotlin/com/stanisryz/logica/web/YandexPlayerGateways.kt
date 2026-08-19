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

    override suspend fun requestAuthorization(): PlayerAuthorizationResult =
        try {
            PlayerAuthorizationResult.Available(bridge.requestPlayerAuthorization().toIdentity())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PlayerAuthorizationResult.Failed(error)
        }

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

internal class YandexCloudSaveGateway(
    private val bridge: YandexGamesBridge,
) : CloudSaveGateway {
    override val availability: CloudSaveAvailability = CloudSaveAvailability.AVAILABLE

    override suspend fun read(): CloudSaveReadResult =
        try {
            ensureAuthorized()
            val encoded = bridge.readPlayerData(CLOUD_STATE_KEY) ?: return CloudSaveReadResult.Missing
            val payload = WebBase64.decode(encoded) ?: error("Yandex cloud save payload is not valid Base64.")
            CloudSaveReadResult.Found(payload)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CloudSaveReadResult.Failed(error)
        }

    override suspend fun write(payload: ByteArray): CloudSaveWriteResult =
        try {
            ensureAuthorized()
            bridge.writePlayerData(
                key = CLOUD_STATE_KEY,
                value = WebBase64.encode(payload),
                flush = true,
            )
            CloudSaveWriteResult.Saved
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CloudSaveWriteResult.Failed(error)
        }

    private suspend fun ensureAuthorized() {
        check(bridge.playerSnapshot().isAuthorized) {
            "Yandex cloud save requires an authorized Player."
        }
    }

    private companion object {
        const val CLOUD_STATE_KEY = "logica_state_v1"
    }
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
            val padding = when {
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
