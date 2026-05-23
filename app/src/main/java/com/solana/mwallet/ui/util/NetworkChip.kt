/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.util

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mwallet.R

/** Helpers to consistently render the small "Devnet/Mainnet/Testnet" pill in the top bar. */
object NetworkChip {

    fun apply(
        container: View,
        dot: View,
        label: TextView,
        cluster: String
    ) {
        val ctx = container.context
        val (bg, color, text) = when (cluster) {
            ProtocolContract.CLUSTER_MAINNET_BETA -> Triple(
                R.drawable.background_chip_mainnet, R.color.cluster_mainnet, R.string.network_mainnet
            )
            ProtocolContract.CLUSTER_TESTNET -> Triple(
                R.drawable.background_chip_testnet, R.color.cluster_testnet, R.string.network_testnet
            )
            else -> Triple(
                R.drawable.background_chip_devnet, R.color.cluster_devnet, R.string.network_devnet
            )
        }
        container.setBackgroundResource(bg)
        dot.backgroundTintList = ContextCompat.getColorStateList(ctx, color)
        label.setText(text)
    }
}
