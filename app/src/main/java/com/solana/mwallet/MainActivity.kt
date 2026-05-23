/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mwallet.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNavigation.setupWithNavController(navHost.navController)

        // Handle desktop QR / remote MWA association URIs by routing into the existing
        // MobileWalletAdapterActivity, just like before.
        intent.data?.let { uri ->
            runCatching {
                val remote = RemoteAssociationUri(uri)
                startActivity(
                    Intent(applicationContext, MobileWalletAdapterActivity::class.java)
                        .setData(remote.uri)
                )
            }
        }

        // Best-effort load the current pubkey into the shared WalletState so each tab can render
        // its first frame without needing a separate fetch.
        refreshPublicKeyAsync()
    }

    override fun onResume() {
        super.onResume()
        refreshPublicKeyAsync()
    }

    private fun refreshPublicKeyAsync() {
        val app = application as? MwalletApplication ?: return
        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) { app.keyRepository.getPublicKeyBase58() }
            app.walletState.setPublicKey(address)
        }
    }
}
