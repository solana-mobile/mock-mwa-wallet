/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.tokens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.main.NetworkPickerSheet
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.ui.util.NetworkChip
import com.solana.mwallet.usecase.BalanceUseCase
import com.solana.mwallet.usecase.TokenAccountsUseCase
import com.solana.mwallet.usecase.TokenMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TokensFragment : Fragment(R.layout.fragment_tokens) {

    private val app get() = requireActivity().application as MwalletApplication
    private val state get() = app.walletState

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var recycler: RecyclerView
    private lateinit var loading: View
    private lateinit var empty: View
    private lateinit var total: TextView
    private lateinit var subtitle: TextView
    private lateinit var networkChip: View
    private lateinit var networkChipDot: View
    private lateinit var networkChipLabel: TextView
    private lateinit var adapter: TokensAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.tokens_swipe)
        recycler = view.findViewById(R.id.tokens_list)
        loading = view.findViewById(R.id.tokens_loading)
        empty = view.findViewById(R.id.tokens_empty)
        total = view.findViewById(R.id.text_token_total_sol)
        subtitle = view.findViewById(R.id.text_token_subtitle)
        networkChip = view.findViewById(R.id.tokens_network_chip)
        networkChipDot = view.findViewById(R.id.tokens_network_chip_dot)
        networkChipLabel = view.findViewById(R.id.tokens_network_chip_label)

        adapter = TokensAdapter { row ->
            // Tapping a token opens Solana Explorer on the right cluster — for the SOL
            // pseudo-mint we use the wallet address (the explorer's "tokens" sub-page),
            // for SPL mints we use the mint itself.
            val cluster = state.explorerCluster()
            val target = if (row.isSol) state.publicKey.value ?: return@TokensAdapter else row.mint
            val url = Format.explorerUrl(cluster, target, isTx = false)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        networkChip.setOnClickListener {
            NetworkPickerSheet().show(parentFragmentManager, NetworkPickerSheet.TAG)
        }
        swipe.setOnRefreshListener { refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            state.network.collectLatest { renderHeader(); refresh() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            state.publicKey.collectLatest { renderHeader(); refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        renderHeader()
        refresh()
    }

    private fun renderHeader() {
        NetworkChip.apply(networkChip, networkChipDot, networkChipLabel, state.network.value)
        val cached = state.balanceLamports.value
        total.text = if (cached != null) "${Format.lamportsToSol(cached)} SOL" else "—"
        subtitle.text = "${state.networkLabel()} · refreshed just now"
    }

    private fun refresh() {
        val address = state.publicKey.value
        if (address == null) {
            adapter.submit(emptyList())
            swipe.isRefreshing = false
            loading.visibility = View.GONE
            empty.visibility = View.VISIBLE
            total.text = "—"
            return
        }
        loading.visibility = if (state.balanceLamports.value == null) View.VISIBLE else View.GONE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val rpc = state.rpcUri()
            val (lamports, tokens) = withContext(Dispatchers.IO) {
                val l = BalanceUseCase.getBalance(rpc, address)
                val t = TokenAccountsUseCase.getTokenAccounts(rpc, address)
                l to t
            }
            state.setBalanceLamports(lamports)
            renderHeader()

            val rows = mutableListOf<TokensAdapter.TokenRow>()
            if (lamports != null && lamports > 0L) {
                rows.add(TokensAdapter.TokenRow(
                    mint = TokenMetadata.SOL_PSEUDO_MINT,
                    uiAmount = lamports / 1_000_000_000.0,
                    decimals = 9,
                    isSol = true
                ))
            }
            tokens.forEach { acct ->
                rows.add(TokensAdapter.TokenRow(
                    mint = acct.mint,
                    uiAmount = acct.uiAmount,
                    decimals = acct.decimals
                ))
            }
            adapter.submit(rows)
            swipe.isRefreshing = false
            loading.visibility = View.GONE
            empty.visibility = if (rows.size <= 1 && tokens.isEmpty()) {
                if (rows.isEmpty()) View.VISIBLE else View.GONE
            } else View.GONE

            total.text = buildString {
                append(if (lamports != null) "${Format.lamportsToSol(lamports)} SOL" else "—")
                if (tokens.isNotEmpty()) append(" + ${tokens.size} token${if (tokens.size != 1) "s" else ""}")
            }
        }
    }
}
