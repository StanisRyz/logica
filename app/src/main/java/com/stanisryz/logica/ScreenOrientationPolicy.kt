package com.stanisryz.logica

import android.content.pm.ActivityInfo

/** Below this smallest width a device is a phone; tablets and unfolded foldables start here. */
internal const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * Phones play in portrait only; tablets and unfolded foldables turn freely as before. Plain portrait
 * rather than USER_PORTRAIT: a phone lying flat or held over the head never flips the board upside
 * down, and many phones do not offer reverse portrait anyway. The activity is recreated when its size
 * changes (folding or unfolding), so the choice is made again for the new smallest width.
 */
internal fun requestedOrientationFor(smallestScreenWidthDp: Int): Int =
    if (smallestScreenWidthDp in 1 until TABLET_SMALLEST_WIDTH_DP) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
