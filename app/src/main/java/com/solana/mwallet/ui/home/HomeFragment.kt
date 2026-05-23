/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mwallet.BarcodeScannerActivity
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.main.NetworkPickerSheet
import com.solana.mwallet.ui.main.ReceiveSheet
import com.solana.mwallet.ui.main.TransactionDetailSheet
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.ui.util.NetworkChip
import com.solana.mwallet.usecase.AirdropUseCase
import com.solana.mwallet.usecase.BalanceUseCase
import com.solana.mwallet.usecase.SignaturesUseCase
import com.solana.mwallet.usecase.UserAuthenticationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val app get() = requireActivity().application as MwalletApplication
    private val state get() = app.walletState

    private lateinit var lockStatus: TextView
    private lateinit var networkChip: View
    private lateinit var networkChipDot: View
    private lateinit var networkChipLabel: TextView
    private lateinit var balanceText: TextView
    private lateinit var balanceSubtitle: TextView
    private lateinit var pubkeyText: TextView
    private lateinit var copyBtn: ImageButton
    private lateinit var explorerBtn: ImageButton
    private lateinit var refreshBtn: ImageButton
    private lateinit var pubkeyRow: View
    private lateinit var quickActions: View
    private lateinit var authCard: View
    private lateinit var noWalletCard: View
    private lateinit var connectExplainer: View
    private lateinit var authBtn: AppCompatButton
    private lateinit var actionConnect: View
    private lateinit var actionReceive: View
    private lateinit var actionAirdrop: View
    private lateinit var actionAirdropIcon: ImageView
    private lateinit var actionAirdropLabel: TextView
    private lateinit var actionExplorer: View
    private lateinit var scanQrBtn: ImageButton

    private lateinit var activityRecycler: RecyclerView
    private lateinit var activityLoading: View
    private lateinit var activityEmpty: View
    private lateinit var activityAdapter: RecentActivityAdapter
    private lateinit var viewAllBtn: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lockStatus = view.findViewById(R.id.text_lock_status)
        networkChip = view.findViewById(R.id.network_chip)
        networkChipDot = view.findViewById(R.id.network_chip_dot)
        networkChipLabel = view.findViewById(R.id.network_chip_label)
        balanceText = view.findViewById(R.id.text_balance)
        balanceSubtitle = view.findViewById(R.id.text_balance_subtitle)
        pubkeyText = view.findViewById(R.id.text_pubkey)
        copyBtn = view.findViewById(R.id.btn_copy)
        explorerBtn = view.findViewById(R.id.btn_explorer)
        refreshBtn = view.findViewById(R.id.btn_refresh)
        pubkeyRow = view.findViewById(R.id.pubkey_row)
        quickActions = view.findViewById(R.id.quick_actions)
        authCard = view.findViewById(R.id.auth_card)
        noWalletCard = view.findViewById(R.id.no_wallet_card)
        connectExplainer = view.findViewById(R.id.connect_explainer)
        authBtn = view.findViewById(R.id.btn_authenticate)
        actionConnect = view.findViewById(R.id.action_connect)
        actionReceive = view.findViewById(R.id.action_receive)
        actionAirdrop = view.findViewById(R.id.action_airdrop)
        actionAirdropIcon = view.findViewById(R.id.action_airdrop_icon)
        actionAirdropLabel = view.findViewById(R.id.action_airdrop_label)
        actionExplorer = view.findViewById(R.id.action_explorer)
        scanQrBtn = view.findViewById(R.id.btn_scan_qr)
        viewAllBtn = view.findViewById(R.id.btn_view_all_activity)

        activityRecycler = view.findViewById(R.id.recent_activity_list)
        activityLoading = view.findViewById(R.id.recent_activity_loading)
        activityEmpty = view.findViewById(R.id.recent_activity_empty)
        activityAdapter = RecentActivityAdapter { item -> openTxSheet(item) }
        activityRecycler.layoutManager = LinearLayoutManager(requireContext())
        activityRecycler.adapter = activityAdapter

        networkChip.setOnClickListener {
            NetworkPickerSheet().show(parentFragmentManager, NetworkPickerSheet.TAG)
        }
        scanQrBtn.setOnClickListener {
            startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }

        copyBtn.setOnClickListener { copyAddress() }
        explorerBtn.setOnClickListener { openExplorerForAddress() }
        refreshBtn.setOnClickListener { refreshAll() }

        actionConnect.setOnClickListener {
            startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }
        actionReceive.setOnClickListener { showReceive() }
        actionAirdrop.setOnClickListener { requestAirdrop() }
        actionExplorer.setOnClickListener { openExplorerForAddress() }
        viewAllBtn.setOnClickListener {
            findNavController().navigate(R.id.nav_history)
        }

        authBtn.setOnClickListener {
            UserAuthenticationUseCase.authenticate(requireActivity()) { result, error ->
                if (result != null) {
                    Toast.makeText(requireContext(), "Authentication succeeded!", Toast.LENGTH_SHORT).show()
                    ensureKeyAndRefresh()
                } else {
                    Toast.makeText(
                        requireContext(),
                        error?.let { "Authentication error: $it" } ?: "Authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            state.publicKey.collectLatest { renderHeader() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            state.network.collectLatest {
                renderHeader()
                refreshAll()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            state.balanceLamports.collectLatest { renderBalance(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            state.balanceLoading.collectLatest {
                if (it) balanceText.text = "—"
            }
        }
    }

    private fun renderHeader() {
        val cluster = state.network.value
        NetworkChip.apply(networkChip, networkChipDot, networkChipLabel, cluster)
        balanceSubtitle.text = "${state.networkLabel()} · ${cluster.shortNetworkBlurb()}"

        val pubkey = state.publicKey.value
        if (pubkey != null) {
            authCard.visibility = View.GONE
            noWalletCard.visibility = View.GONE
            connectExplainer.visibility = View.VISIBLE
            pubkeyRow.visibility = View.VISIBLE
            quickActions.visibility = View.VISIBLE
            pubkeyText.text = Format.shortAddress(pubkey, 6, 6)
            lockStatus.setText(R.string.label_unlocked_for)
            updateAirdropAffordance()
        } else {
            authCard.visibility = View.VISIBLE
            noWalletCard.visibility = View.VISIBLE
            connectExplainer.visibility = View.GONE
            pubkeyRow.visibility = View.INVISIBLE
            quickActions.visibility = View.GONE
            lockStatus.setText(R.string.label_locked)
            balanceText.text = "0.0000"
        }
    }

    private fun renderBalance(lamports: Long?) {
        if (lamports == null) {
            balanceText.text = "0.0000"
        } else {
            balanceText.text = Format.lamportsToSol(lamports)
        }
    }

    /**
     * The Airdrop quick action is only meaningful on Devnet / Testnet — disable it on
     * Mainnet so we don't pretend to do something the RPC will refuse. We dim the icon
     * and leave the click handler to surface a toast explaining why.
     */
    private fun updateAirdropAffordance() {
        val isMainnet = state.network.value == ProtocolContract.CLUSTER_MAINNET_BETA
        actionAirdrop.alpha = if (isMainnet) 0.4f else 1.0f
        actionAirdrop.isEnabled = !isMainnet
        actionAirdropIcon.isEnabled = !isMainnet
        actionAirdropLabel.isEnabled = !isMainnet
    }

    private fun refreshAll() {
        refreshBalance()
        refreshActivity()
    }

    private fun refreshBalance() {
        val address = state.publicKey.value ?: return
        state.markBalanceLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            val lamports = withContext(Dispatchers.IO) {
                BalanceUseCase.getBalance(state.rpcUri(), address)
            }
            state.setBalanceLamports(lamports)
        }
    }

    private fun refreshActivity() {
        val address = state.publicKey.value ?: run {
            activityLoading.visibility = View.GONE
            activityEmpty.visibility = View.VISIBLE
            return
        }
        activityLoading.visibility = View.VISIBLE
        activityEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val sigs = withContext(Dispatchers.IO) {
                SignaturesUseCase.getSignaturesForAddress(state.rpcUri(), address, limit = 5)
            }
            if (sigs.isEmpty()) {
                activityAdapter.submit(emptyList())
                activityLoading.visibility = View.GONE
                activityEmpty.visibility = View.VISIBLE
                return@launch
            }
            val summaries = withContext(Dispatchers.IO) {
                sigs.mapNotNull { info ->
                    SignaturesUseCase.getTransactionSummary(state.rpcUri(), info.signature, address)
                        ?.copy(blockTime = info.blockTime ?: 0L)
                        ?: SignaturesUseCase.TransactionSummary(
                            signature = info.signature,
                            slot = info.slot,
                            blockTime = info.blockTime,
                            fee = 0L,
                            err = info.err,
                            memo = info.memo,
                            direction = if (info.err != null)
                                SignaturesUseCase.TransactionSummary.Direction.FAILED
                            else SignaturesUseCase.TransactionSummary.Direction.UNKNOWN,
                            lamportsDelta = 0L,
                            counterparty = null,
                            programs = emptyList(),
                            rawJson = null
                        )
                }
            }
            activityAdapter.submit(summaries)
            activityLoading.visibility = View.GONE
            activityEmpty.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun copyAddress() {
        val address = state.publicKey.value ?: return
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
            ClipData.newPlainText("Solana address", address)
        )
        Toast.makeText(requireContext(), R.string.label_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openExplorerForAddress() {
        val address = state.publicKey.value ?: run {
            Toast.makeText(requireContext(), R.string.label_explorer_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        val url = Format.explorerUrl(state.explorerCluster(), address, isTx = false)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun showReceive() {
        if (state.publicKey.value.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.label_explorer_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        ReceiveSheet().show(parentFragmentManager, ReceiveSheet.TAG)
    }

    private fun openTxSheet(item: SignaturesUseCase.TransactionSummary) {
        TransactionDetailSheet.newInstance(
            signature = item.signature,
            cluster = state.explorerCluster()
        ).show(parentFragmentManager, TransactionDetailSheet.TAG)
    }

    private fun requestAirdrop() {
        val address = state.publicKey.value ?: return
        if (state.network.value == ProtocolContract.CLUSTER_MAINNET_BETA) {
            Toast.makeText(requireContext(), R.string.action_airdrop_disabled_mainnet, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), R.string.label_airdrop_requesting, Toast.LENGTH_SHORT).show()
        actionAirdrop.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val sig = withContext(Dispatchers.IO) {
                AirdropUseCase.requestAirdrop(state.rpcUri(), address)
            }
            actionAirdrop.isEnabled = true
            if (sig != null) {
                Toast.makeText(requireContext(), R.string.label_airdrop_done, Toast.LENGTH_SHORT).show()
                refreshAll()
            } else {
                Toast.makeText(requireContext(), R.string.label_airdrop_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureKeyAndRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                app.keyRepository.getPublicKeyBase58() ?: run {
                    try {
                        app.keyRepository.generateKeypair()
                        app.keyRepository.getPublicKeyBase58()
                    } catch (_: Exception) { null }
                }
            }
            state.setPublicKey(address)
            refreshAll()
        }
    }

    private fun String.shortNetworkBlurb(): String = when (this) {
        ProtocolContract.CLUSTER_MAINNET_BETA -> "mainnet-beta"
        ProtocolContract.CLUSTER_TESTNET -> "testnet SOL"
        else -> "testnet SOL"
    }
}
