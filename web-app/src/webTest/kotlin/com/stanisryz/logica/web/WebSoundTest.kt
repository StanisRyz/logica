package com.stanisryz.logica.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Game sounds may play, and a tap may wake the audio context, only while the setting and the host's audio conditions allow it. */
class WebSoundTest {
    @Test
    fun soundIsAudibleOnlyWithTheSettingOnAndTheAudioConditionsMet() {
        listOf(true, false).forEach { conditions ->
            assertEquals(conditions, webSoundAudible(soundEnabled = true, audioConditionsMet = conditions))
            assertFalse(webSoundAudible(soundEnabled = false, audioConditionsMet = conditions))
        }
    }

    @Test
    fun theAudioRuleStopsOnABlurButNotWithoutFocusFromTheStart() {
        assertTrue(audible())
        assertFalse(audible(started = false))
        assertFalse(audible(yandexPaused = true))
        assertFalse(audible(fullscreenAdActive = true))
        assertFalse(audible(browserVisible = false))
        // A blur since the last focus, tap, or key press silences it (requirement 1.3)...
        assertFalse(audible(blurredSinceInteraction = true))
        // ...while a game that never had focus still sounds, where GameplayAPI still needs focus.
        assertFalse(
            WebEffectiveLifecycle.isActive(
                started = true,
                yandexPaused = false,
                fullscreenAdActive = false,
                browserVisible = true,
                browserFocused = false,
            ),
        )
    }

    private fun audible(
        started: Boolean = true,
        yandexPaused: Boolean = false,
        fullscreenAdActive: Boolean = false,
        browserVisible: Boolean = true,
        blurredSinceInteraction: Boolean = false,
    ) = WebEffectiveLifecycle.isAudible(started, yandexPaused, fullscreenAdActive, browserVisible, blurredSinceInteraction)
}
