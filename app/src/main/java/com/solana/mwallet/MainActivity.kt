/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mwallet.data.SeedPhraseRepository
import com.solana.mwallet.databinding.ActivityMainBinding
import com.solana.mwallet.usecase.UserAuthenticationUseCase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val seedPhraseRepository: SeedPhraseRepository
        get() = (application as MwalletApplication).seedPhraseRepository

    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.titleText.text = "Name: ${BuildConfig.WALLET_NAME}"
        renderSeedPhraseWallets()

        viewBinding.button.setOnClickListener {
            UserAuthenticationUseCase.authenticate(this) { result , error ->
                Toast.makeText(applicationContext,
                    result?.let { "Authentication succeeded!" }
                        ?: error?.let { "Authentication error: $it" }
                        ?: "Authentication failed", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        viewBinding.deriveNextButton.setOnClickListener {
            seedPhraseRepository.deriveNextWallet()
            renderSeedPhraseWallets()
        }

        viewBinding.importSeedPhraseButton.setOnClickListener {
            importSeedPhrase()
        }

        viewBinding.showSeedPhraseButton.setOnClickListener {
            showSeedPhrase()
        }

        viewBinding.resetStorageButton.setOnClickListener {
            showResetStorageConfirmation()
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

    private fun copySeedPhrase(seedPhrase: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Seed phrase", seedPhrase))
        Toast.makeText(applicationContext, "Seed phrase copied", Toast.LENGTH_SHORT).show()
    }

    private fun getDerivedWalletsText(): String {
        val wallets = seedPhraseRepository.getDerivedWallets()
        if (wallets.isEmpty()) {
            return "Derived wallets: none"
        }

        return wallets.joinToString("\n\n") { wallet ->
            "${wallet.name}\n${wallet.derivationPath}\n${wallet.address}"
        }
    }

    private fun getPublicKeyText(): String =
        runCatching { seedPhraseRepository.getPrimaryWallet()?.address }.getOrNull()
            ?.let { "Address: $it" }
            ?: "Address not configured"

    private fun importSeedPhrase() {
        seedPhraseRepository.importAvailableSeedPhraseIfEmpty()
        renderSeedPhraseWallets()
        val message = if (seedPhraseRepository.getSeedPhrase() == null) {
            "Seed phrase import failed"
        } else {
            "Seed phrase imported"
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun renderSeedPhraseWallets() {
        val seedPhrase = seedPhraseRepository.getSeedPhrase()
        val hasSeedPhrase = seedPhrase != null
        viewBinding.publicKeyText.text = getPublicKeyText()
        viewBinding.derivedWalletsText.text = getDerivedWalletsText()
        viewBinding.deriveNextButton.isEnabled = hasSeedPhrase
        viewBinding.importSeedPhraseButton.isEnabled = !hasSeedPhrase
        viewBinding.importSeedPhraseButton.visibility =
            if (hasSeedPhrase) View.GONE else View.VISIBLE
        viewBinding.showSeedPhraseButton.isEnabled = hasSeedPhrase
    }

    private fun resetStorage() {
        lifecycleScope.launch {
            seedPhraseRepository.clear()
            (application as MwalletApplication).keyRepository.clearKeypairs()
            renderSeedPhraseWallets()
            Toast.makeText(applicationContext, "Storage reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetStorageConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset storage")
            .setMessage("Clear stored seed phrase, derived wallet names, and keypairs?")
            .setPositiveButton("Reset") { _, _ -> resetStorage() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSeedPhrase() {
        val seedPhrase = seedPhraseRepository.getSeedPhrase() ?: return
        val padding = (24 * resources.displayMetrics.density).toInt()
        val seedPhraseText = TextView(this).apply {
            text = seedPhrase
            setPadding(padding, padding, padding, 0)
            setTextIsSelectable(true)
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("Seed phrase")
            .setView(seedPhraseText)
            .setPositiveButton("Copy") { _, _ -> copySeedPhrase(seedPhrase) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
