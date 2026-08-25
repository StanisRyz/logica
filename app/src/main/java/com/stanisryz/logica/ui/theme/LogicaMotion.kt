package com.stanisryz.logica.ui.theme

/** Small shared timings for ordinary UI continuity; gameplay-specific motion keeps its own timing. */
internal object LogicaMotion {
    const val SHORT_MILLIS = 150
    const val SCREEN_MILLIS = 200

    /** Shared timing and offsets for the shell's directional navigation motion. */
    const val NAVIGATION_SLIDE_MILLIS = 220
    const val DESTINATION_OUTGOING_PARALLAX_DIVISOR = 4
}
