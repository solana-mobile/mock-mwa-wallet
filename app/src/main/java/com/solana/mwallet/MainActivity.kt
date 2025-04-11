/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mwallet.databinding.ActivityMainBinding
import com.solana.mwallet.usecase.UserAuthenticationUseCase

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.button.setOnClickListener {
            UserAuthenticationUseCase.authenticate(this) { result , error ->
                Toast.makeText(applicationContext,
                    result?.let { "Authentication succeeded!" }
                        ?: error?.let { "Authentication error: $it" }
                        ?: "Authentication failed", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        intent.data?.let { uri ->
            runCatching {
                val remoteAssociationUri = RemoteAssociationUri(uri)
                startActivity(
                    Intent(applicationContext, MobileWalletAdapterActivity::class.java)
                        .setData(remoteAssociationUri.uri))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_qr_scanner -> {
                // Open ML Kit Barcode Scanner
                openBarcodeScanner()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openBarcodeScanner() {
        startActivity(Intent(applicationContext, BarcodeScannerActivity::class.java))
    }
}