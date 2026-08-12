package com.stanisryz.logica

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stanisryz.logica.store.proceedRuStorePayIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (intent.action != Intent.ACTION_VIEW || intent.data == null) return
        val application = application as LogicaApplication
        if (!application.container.isRuStorePayConfigured) return
        applicationContext.proceedRuStorePayIntent(intent)
    }
}
