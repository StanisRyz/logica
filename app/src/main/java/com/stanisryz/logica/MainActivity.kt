package com.stanisryz.logica

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.update(this)
        enableEdgeToEdge()
        // A payment that finished while the application was gone comes back as a cold start.
        proceedPaymentDeeplink(intent)
        setContent {
            LogicaApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        proceedPaymentDeeplink(intent)
    }

    /**
     * RuStore returns from an external payment application through a deeplink into this activity, and
     * the Pay SDK only learns that the payment ended if the intent is handed back to it. Nothing else
     * happens here: an ordinary launch is not a payment return and never touches the SDK, an
     * unconfigured build never touches it either, and a hand-off that fails leaves the purchase to
     * reconciliation on the next store open.
     */
    private fun proceedPaymentDeeplink(intent: Intent) {
        val application = application as LogicaApplication
        application.container.platform.proceedPaymentReturn(intent)
    }
}

/**
 * Status and navigation bar icons follow the app's resolved theme rather than the system's: a light
 * theme on a dark system would otherwise draw white icons on the cream background. The bars stay
 * transparent; only the icon colour changes.
 */
@Composable
internal fun SystemBarsFollowTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, darkTheme) {
        val style =
            if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
