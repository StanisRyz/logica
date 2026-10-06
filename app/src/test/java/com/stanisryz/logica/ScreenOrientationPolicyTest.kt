package com.stanisryz.logica

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phones are held in portrait; tablets and unfolded foldables keep turning freely. */
class ScreenOrientationPolicyTest {
    @Test
    fun phonesArePortraitAndLargerScreensTurnFreely() {
        listOf(320, 360, 411, 599).forEach {
            assertEquals("$it dp", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientationFor(it))
        }
        listOf(600, 673, 800, 1280).forEach {
            assertEquals("$it dp", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientationFor(it))
        }
        // An undefined width (0) never locks the screen.
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientationFor(0))
    }
}
