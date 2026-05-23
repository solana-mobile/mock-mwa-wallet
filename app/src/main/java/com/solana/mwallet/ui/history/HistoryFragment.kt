/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.history

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.main.NetworkPickerSheet
import com.solana.mwallet.ui.main.TransactionDetailSheet
import com.solana.mwallet.ui.util.NetworkChip
import com.solana.mwallet.usecase.SignaturesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val app get() = requireActivity().application as MwalletApplication
    private val state get() = app.walletState

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var recycler: RecyclerView
    private lateinit var loading: View
    private lateinit var empty: View
    private lateinit var networkChip: View
    private lateinit var networkChipDot: View
    private lateinit var networkChipLabel: TextView
    private lateinit var chips: ChipGroup
    private lateinit var adapter: HistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.history_swipe)
        recycler = view.findViewById(R.id.history_list)
        loading = view.findViewById(R.id.history_loading)
        empty = view.findViewById(R.id.history_empty)
        networkChip = view.findViewById(R.id.history_network_chip)
        networkChipDot = view.findViewById(R.id.history_network_chip_dot)
        networkChipLabel = view.findViewById(R.id.history_network_chip_label)
        chips = view.findViewById(R.id.history_filter_chips)

        adapter = HistoryAdapter { item -> openDetail(item) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        networkChip.setOnClickListener {
            NetworkPickerSheet().show(parentFragmentManager, NetworkPickerSheet.TAG)
        }
        swipe.setOnRefreshListener { refresh() }

        chips.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val f = when (id) {
                R.id.chip_filter_in -> HistoryAdapter.Filter.IN
                R.id.chip_filter_out -> HistoryAdapter.Filter.OUT
                R.id.chip_filter_program -> HistoryAdapter.Filter.PROGRAM
                else -> HistoryAdapter.Filter.ALL
            }
            adapter.setFilter(f)
            empty.visibility = if (adapter.isEmpty() && loading.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            state.network.collectLatest { NetworkChip.apply(networkChip, networkChipDot, networkChipLabel, it); refresh() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            state.publicKey.collectLatest { refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        NetworkChip.apply(networkChip, networkChipDot, networkChipLabel, state.network.value)
        refresh()
    }

    private fun refresh() {
        val address = state.publicKey.value
        if (address == null) {
            adapter.submit(emptyList())
            loading.visibility = View.GONE
            empty.visibility = View.VISIBLE
            swipe.isRefreshing = false
            return
        }
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val sigs = withContext(Dispatchers.IO) {
                SignaturesUseCase.getSignaturesForAddress(state.rpcUri(), address, limit = 25)
            }
            val summaries = withContext(Dispatchers.IO) {
                sigs.map { info ->
                    SignaturesUseCase.getTransactionSummary(state.rpcUri(), info.signature, address)
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
            adapter.submit(summaries)
            loading.visibility = View.GONE
            empty.visibility = if (adapter.isEmpty()) View.VISIBLE else View.GONE
            swipe.isRefreshing = false
        }
    }

    private fun openDetail(item: SignaturesUseCase.TransactionSummary) {
        TransactionDetailSheet.newInstance(
            signature = item.signature,
            cluster = state.explorerCluster()
        ).show(parentFragmentManager, TransactionDetailSheet.TAG)
    }
}
