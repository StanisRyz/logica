package com.stanisryz.logica.web

import com.stanisryz.logica.platform.PlatformLifecycleState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Game sounds may play, and a tap may wake the audio context, only while both the setting and the lifecycle allow it. */
class WebSoundTest {
    @Test
    fun soundIsAudibleOnlyWithTheSettingOnAndAnActiveLifecycle() {
        PlatformLifecycleState.entries.forEach { lifecycle ->
            assertEquals(lifecycle == PlatformLifecycleState.ACTIVE, webSoundAudible(soundEnabled = true, lifecycle), "$lifecycle")
            assertEquals(false, webSoundAudible(soundEnabled = false, lifecycle), "$lifecycle")
        }
    }
}
