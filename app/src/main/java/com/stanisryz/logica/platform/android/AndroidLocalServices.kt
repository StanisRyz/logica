package com.stanisryz.logica.platform.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.stanisryz.logica.platform.AdDisplayHost
import com.stanisryz.logica.platform.CloudSaveAvailability
import com.stanisryz.logica.platform.CloudSaveGateway
import com.stanisryz.logica.platform.CloudSaveReadResult
import com.stanisryz.logica.platform.CloudSaveWriteResult
import com.stanisryz.logica.platform.PlatformLifecycle
import com.stanisryz.logica.platform.PlatformLifecycleState
import com.stanisryz.logica.platform.PlayerAuthorizationResult
import com.stanisryz.logica.platform.PlayerAuthorizationState
import com.stanisryz.logica.platform.PlayerIdentity
import com.stanisryz.logica.platform.PlayerIdentityGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidAdDisplayHost(
    val activity: Activity,
) : AdDisplayHost

/** Android has no application account flow yet, so Profile keeps its current local-only behavior. */
internal object AndroidLocalPlayerIdentityGateway : PlayerIdentityGateway {
    private val localIdentity =
        PlayerIdentity(
            authorizationState = PlayerAuthorizationState.UNSUPPORTED,
            provider = "android-local",
        )

    override suspend fun identity(): PlayerIdentity = localIdentity

    override suspend fun requestAuthorization(): PlayerAuthorizationResult = PlayerAuthorizationResult.Unsupported
}

/** Cloud save is independent from Room/DataStore and intentionally unavailable in this Android stage. */
internal object UnsupportedAndroidCloudSaveGateway : CloudSaveGateway {
    override val availability: CloudSaveAvailability = CloudSaveAvailability.UNSUPPORTED

    override suspend fun read(): CloudSaveReadResult = CloudSaveReadResult.Unsupported

    override suspend fun write(payload: ByteArray): CloudSaveWriteResult = CloudSaveWriteResult.Unsupported
}

/** Process-level foreground activity, expressed without leaking Android lifecycle types to consumers. */
internal class AndroidPlatformLifecycle(
    application: Application,
) : PlatformLifecycle,
    Application.ActivityLifecycleCallbacks {
    private val mutableState = MutableStateFlow(PlatformLifecycleState.INACTIVE)
    override val state: StateFlow<PlatformLifecycleState> = mutableState.asStateFlow()

    private var startedActivities = 0

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        mutableState.value = PlatformLifecycleState.ACTIVE
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) mutableState.value = PlatformLifecycleState.INACTIVE
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
