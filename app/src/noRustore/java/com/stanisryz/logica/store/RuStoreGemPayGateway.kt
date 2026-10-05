package com.stanisryz.logica.store

import android.content.Context
import android.content.Intent

/*
 * The RuStore surface of a build made with `-Plogica.withoutRustore=true`, which leaves the RuStore
 * SDK out entirely (its Maven repository is unreachable from some build machines). The names match
 * `src/rustore`, so the rest of the application compiles unchanged; the store is simply unavailable.
 */

/** Always the unconfigured gateway: this build has no RuStore SDK to pay through. */
internal fun createRuStorePayGateway(
    @Suppress("UNUSED_PARAMETER") consoleApplicationId: String,
    @Suppress("UNUSED_PARAMETER") sdkTheme: () -> Unit,
): RuStorePayGateway = UnconfiguredRuStorePayGateway

/** There is no payment sheet to theme. */
internal fun Context.ruStoreSdkTheme() = Unit

/** There is no Pay SDK to hand a payment return to. */
@Suppress("UNUSED_PARAMETER")
internal fun Context.proceedRuStorePayIntent(intent: Intent) = Unit
