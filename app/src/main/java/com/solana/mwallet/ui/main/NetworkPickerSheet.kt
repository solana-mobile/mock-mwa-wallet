/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mwallet.R
import com.solana.mwallet.MwalletApplication

class NetworkPickerSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.Theme_MwalletApp_BottomSheetDialog2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_network_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val state = (requireActivity().application as MwalletApplication).walletState
        val current = state.network.value

        fun bind(rowId: Int, checkId: Int, cluster: String) {
            view.findViewById<View>(checkId).visibility =
                if (current == cluster) View.VISIBLE else View.INVISIBLE
            view.findViewById<View>(rowId).setOnClickListener {
                state.setNetwork(cluster)
                dismiss()
            }
        }

        bind(R.id.network_devnet_row, R.id.network_devnet_check, ProtocolContract.CLUSTER_DEVNET)
        bind(R.id.network_mainnet_row, R.id.network_mainnet_check, ProtocolContract.CLUSTER_MAINNET_BETA)
        bind(R.id.network_testnet_row, R.id.network_testnet_check, ProtocolContract.CLUSTER_TESTNET)
    }

    companion object {
        const val TAG = "NetworkPickerSheet"
    }
}
