package com.stanisryz.logica.platform

import kotlinx.coroutines.flow.StateFlow

/** Only host activity relevant to platform SDKs; gameplay lifecycle remains independent. */
enum class PlatformLifecycleState {
    ACTIVE,
    INACTIVE,
}

interface PlatformLifecycle {
    val state: StateFlow<PlatformLifecycleState>
}
