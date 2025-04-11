package com.solana.mwallet

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MobileWalletAdapterIntentHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.data?.let { uri ->
            if (uri.path?.endsWith("/remote") == true) {
                // Route to wallet home screen
                startActivity(Intent(this, MainActivity::class.java).apply {
                    data = uri
                })
            } else {
                // Route to Bottom Sheet Activity
                startActivity(Intent(this, MobileWalletAdapterActivity::class.java).apply {
                    data = uri
                })
            }
        }

        // Finish this activity to remove it from the back stack
        finish()
    }
}