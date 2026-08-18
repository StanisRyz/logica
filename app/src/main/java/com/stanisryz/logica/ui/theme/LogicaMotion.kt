package com.stanisryz.logica.ui.theme

/** Small shared timings for ordinary UI continuity; gameplay-specific motion keeps its own timing. */
internal object LogicaMotion {
    const val SHORT_MILLIS = 150
    const val SCREEN_MILLIS = 200

    /** Shared fade-through timing for destination and primary-tab changes. */
    const val NAVIGATION_EXIT_MILLIS = 90
    const val NAVIGATION_ENTER_DELAY_MILLIS = NAVIGATION_EXIT_MILLIS
    const val NAVIGATION_ENTER_MILLIS = 120
    const val NAVIGATION_OFFSET_DIVISOR = 28
}
