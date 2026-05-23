/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.solana.mwallet.BarcodeScannerActivity
import com.solana.mwallet.BuildConfig
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.main.NetworkPickerSheet
import com.solana.mwallet.ui.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val app get() = requireActivity().application as MwalletApplication
    private val state get() = app.walletState

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val networkValue = view.findViewById<TextView>(R.id.text_network_value)
        val rpcValue = view.findViewById<TextView>(R.id.text_rpc_value)
        val importedTitle = view.findViewById<TextView>(R.id.text_imported_title)
        val importedSubtitle = view.findViewById<TextView>(R.id.text_imported_subtitle)
        val versionView = view.findViewById<TextView>(R.id.text_version)

        view.findViewById<View>(R.id.row_network).setOnClickListener {
            NetworkPickerSheet().show(parentFragmentManager, NetworkPickerSheet.TAG)
        }
        // The RPC URL row is informational. Tapping copies the full endpoint to the
        // clipboard so a tester can paste it into `solana config set --url …` or a
        // curl request — opening the JSON-RPC endpoint in a browser (the previous
        // behaviour) just rendered a 405 page, which was confusing.
        view.findViewById<View>(R.id.row_rpc).setOnClickListener {
            val url = state.rpcUri().toString()
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("Solana RPC endpoint", url))
            Toast.makeText(requireContext(), R.string.settings_rpc_copied, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.row_faucet).setOnClickListener {
            val addr = state.publicKey.value
            val baseUrl = "https://faucet.solana.com"
            val url = if (!addr.isNullOrBlank()) "$baseUrl/?address=$addr" else baseUrl
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        view.findViewById<View>(R.id.row_scan_qr).setOnClickListener {
            startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }
        view.findViewById<View>(R.id.row_explorer).setOnClickListener {
            val addr = state.publicKey.value ?: run {
                Toast.makeText(requireContext(), R.string.label_explorer_disabled, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = Format.explorerUrl(state.explorerCluster(), addr, isTx = false)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        view.findViewById<View>(R.id.row_regenerate).setOnClickListener { regenerateKey() }
        view.findViewById<View>(R.id.row_repo).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/solana-mobile/mock-mwa-wallet")))
        }
        view.findViewById<View>(R.id.row_docs).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.solanamobile.com/mobile-wallet-adapter/overview")))
        }

        val privateKey: String? = BuildConfig.PRIVATE_KEY
        if (!privateKey.isNullOrBlank()) {
            importedTitle.setText(R.string.settings_imported_key)
            importedSubtitle.setText(R.string.settings_imported_key_subtitle)
        } else {
            importedTitle.setText(R.string.settings_no_imported)
            importedSubtitle.setText(R.string.settings_no_imported_subtitle)
        }
        versionView.text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        viewLifecycleOwner.lifecycleScope.launch {
            state.network.collectLatest {
                networkValue.text = "${state.networkLabel()} · ${state.rpcUri().host}"
                rpcValue.text = state.rpcUri().host
            }
        }
    }

    private fun regenerateKey() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { app.keyRepository.generateKeypair() }
            }
            val newAddr = withContext(Dispatchers.IO) { app.keyRepository.getPublicKeyBase58() }
            state.setPublicKey(newAddr)
            Toast.makeText(requireContext(), "New keypair generated", Toast.LENGTH_SHORT).show()
        }
    }
}
