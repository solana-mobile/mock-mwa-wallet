/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mwallet.databinding.ActivityMainBinding
import com.solana.mwallet.usecase.BalanceUseCase
import com.solana.mwallet.usecase.UserAuthenticationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val networkOptions = listOf(
        ProtocolContract.CLUSTER_DEVNET,
        ProtocolContract.CLUSTER_MAINNET_BETA,
        ProtocolContract.CLUSTER_TESTNET
    )
    private val networkLabels get() = listOf(
        getString(R.string.network_devnet),
        getString(R.string.network_mainnet),
        getString(R.string.network_testnet)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.button.setOnClickListener {
            UserAuthenticationUseCase.authenticate(this) { result, error ->
                if (result != null) {
                    Toast.makeText(applicationContext, "Authentication succeeded!", Toast.LENGTH_SHORT).show()
                    ensureKeyAndShowPublicKey()
                } else {
                    Toast.makeText(
                        applicationContext,
                        error?.let { "Authentication error: $it" } ?: "Authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        intent.data?.let { uri ->
            runCatching {
                val remoteAssociationUri = RemoteAssociationUri(uri)
                startActivity(
                    Intent(applicationContext, MobileWalletAdapterActivity::class.java)
                        .setData(remoteAssociationUri.uri)
                )
            }
        }

        loadAndShowPublicKey()
        setupNetworkSpinner()
    }

    private fun setupNetworkSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, networkLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding.networkSpinner.adapter = adapter
        val saved = prefs.getString(KEY_NETWORK, ProtocolContract.CLUSTER_DEVNET) ?: ProtocolContract.CLUSTER_DEVNET
        val index = networkOptions.indexOf(saved).coerceAtLeast(0)
        viewBinding.networkSpinner.setSelection(index)
        viewBinding.networkSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cluster = networkOptions[position]
                prefs.edit().putString(KEY_NETWORK, cluster).apply()
                refreshBalance()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun rpcUriForCluster(cluster: String): Uri = when (cluster) {
        ProtocolContract.CLUSTER_MAINNET_BETA -> Uri.parse("https://api.mainnet-beta.solana.com")
        ProtocolContract.CLUSTER_DEVNET -> Uri.parse("https://api.devnet.solana.com")
        ProtocolContract.CLUSTER_TESTNET -> Uri.parse("https://api.testnet.solana.com")
        else -> Uri.parse("https://api.devnet.solana.com")
    }

    private fun refreshBalance() {
        val address = viewBinding.pubkeyText.text?.toString() ?: return
        if (address.isBlank()) return
        viewBinding.balanceText.text = getString(R.string.label_balance_loading)
        scope.launch {
            val cluster = prefs.getString(KEY_NETWORK, ProtocolContract.CLUSTER_DEVNET) ?: ProtocolContract.CLUSTER_DEVNET
            val rpcUri = rpcUriForCluster(cluster)
            val lamports = withContext(Dispatchers.IO) {
                BalanceUseCase.getBalance(rpcUri, address)
            }
            viewBinding.balanceText.text = if (lamports != null) {
                "%.4f SOL".format(lamports / 1_000_000_000.0)
            } else {
                getString(R.string.label_balance_error)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "mwallet_main"
        private const val KEY_NETWORK = "selected_network"
    }

    override fun onResume() {
        super.onResume()
        loadAndShowPublicKey()
    }

    private fun loadAndShowPublicKey() {
        scope.launch {
            val address = withContext(Dispatchers.IO) {
                (application as? MwalletApplication)?.keyRepository?.getPublicKeyBase58()
            }
            updatePubkeyUi(address)
        }
    }

    /** After auth: ensure a key exists (create one if none), then show pubkey. */
    private fun ensureKeyAndShowPublicKey() {
        scope.launch {
            val repo = (application as? MwalletApplication)?.keyRepository ?: return@launch
            val address = withContext(Dispatchers.IO) {
                repo.getPublicKeyBase58() ?: run {
                    try {
                        repo.generateKeypair()
                        repo.getPublicKeyBase58()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            updatePubkeyUi(address)
        }
    }

    private fun updatePubkeyUi(address: String?) {
        if (address != null) {
            viewBinding.pubkeyLabel.visibility = View.VISIBLE
            viewBinding.pubkeyText.visibility = View.VISIBLE
            viewBinding.pubkeyText.text = address
            viewBinding.copyButton.visibility = View.VISIBLE
            viewBinding.copyButton.setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
                    ClipData.newPlainText("Solana address", address)
                )
                Toast.makeText(this, R.string.label_copied, Toast.LENGTH_SHORT).show()
            }
            viewBinding.networkLabel.visibility = View.VISIBLE
            viewBinding.networkSpinner.visibility = View.VISIBLE
            viewBinding.balanceLabel.visibility = View.VISIBLE
            viewBinding.balanceText.visibility = View.VISIBLE
            viewBinding.noKeyHint.visibility = View.GONE
            refreshBalance()
        } else {
            viewBinding.pubkeyLabel.visibility = View.GONE
            viewBinding.pubkeyText.visibility = View.GONE
            viewBinding.copyButton.visibility = View.GONE
            viewBinding.networkLabel.visibility = View.GONE
            viewBinding.networkSpinner.visibility = View.GONE
            viewBinding.balanceLabel.visibility = View.GONE
            viewBinding.balanceText.visibility = View.GONE
            viewBinding.noKeyHint.visibility = View.VISIBLE
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