package com.stanisryz.logica.platform.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.stanisryz.logica.ui.components.GameSound
import com.stanisryz.logica.ui.components.GameSoundPlayer
import com.stanisryz.logica.ui.components.readGameSoundBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays the shared generated game sounds through one [SoundPool]. The WAV bytes are copied once
 * into the cache (SoundPool loads files, not byte arrays) off the main thread; a sound that has
 * not loaded yet, or any audio failure, is simply silent. [isEnabled] is the Sound setting.
 */
internal class AndroidGameSoundPlayer(
    context: Context,
    scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
) : GameSoundPlayer {
    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    private val loaded = mutableMapOf<GameSound, Int>()

    init {
        val directory = File(context.cacheDir, "sounds")
        scope.launch {
            GameSound.entries.forEach { sound ->
                runCatching {
                    val file =
                        withContext(Dispatchers.IO) {
                            directory.mkdirs()
                            File(directory, "${sound.fileName}.wav").also { it.writeBytes(readGameSoundBytes(sound)) }
                        }
                    loaded[sound] = soundPool.load(file.path, 1)
                }
            }
        }
    }

    override fun play(sound: GameSound) {
        if (!isEnabled()) return
        val id = loaded[sound] ?: return
        val volume = if (sound == GameSound.TAP) TAP_VOLUME else EVENT_VOLUME
        runCatching { soundPool.play(id, volume, volume, 1, 0, 1f) }
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val MAX_STREAMS = 4
        const val TAP_VOLUME = 0.45f
        const val EVENT_VOLUME = 0.8f
    }
}
