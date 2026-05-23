/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide wallet state held alongside the Application. Keeps the active network, public key,
 * cached balance, recent signatures, and token accounts so each tab can render instantly when
 * switched while a background refresh is in flight.
 */
class WalletState(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _network = MutableStateFlow(
        prefs.getString(KEY_NETWORK, ProtocolContract.CLUSTER_DEVNET) ?: ProtocolContract.CLUSTER_DEVNET
    )
    val network: StateFlow<String> = _network

    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey: StateFlow<String?> = _publicKey

    private val _balanceLamports = MutableStateFlow<Long?>(null)
    val balanceLamports: StateFlow<Long?> = _balanceLamports

    private val _balanceLoading = MutableStateFlow(false)
    val balanceLoading: StateFlow<Boolean> = _balanceLoading

    fun setNetwork(cluster: String) {
        _network.value = cluster
        prefs.edit().putString(KEY_NETWORK, cluster).apply()
        // Wipe cached balance so tabs request a refresh.
        _balanceLamports.value = null
    }

    fun setPublicKey(address: String?) {
        if (_publicKey.value != address) {
            _publicKey.value = address
            _balanceLamports.value = null
        }
    }

    fun setBalanceLamports(lamports: Long?) {
        _balanceLamports.value = lamports
        _balanceLoading.value = false
    }

    fun markBalanceLoading() {
        _balanceLoading.value = true
    }

    fun rpcUri(): Uri = when (network.value) {
        ProtocolContract.CLUSTER_MAINNET_BETA -> Uri.parse("https://api.mainnet-beta.solana.com")
        ProtocolContract.CLUSTER_DEVNET -> Uri.parse("https://api.devnet.solana.com")
        ProtocolContract.CLUSTER_TESTNET -> Uri.parse("https://api.testnet.solana.com")
        else -> Uri.parse("https://api.devnet.solana.com")
    }

    fun explorerCluster(): String = when (network.value) {
        ProtocolContract.CLUSTER_MAINNET_BETA -> "mainnet-beta"
        ProtocolContract.CLUSTER_DEVNET -> "devnet"
        ProtocolContract.CLUSTER_TESTNET -> "testnet"
        else -> "devnet"
    }

    fun networkLabel(): String = when (network.value) {
        ProtocolContract.CLUSTER_MAINNET_BETA -> "Mainnet"
        ProtocolContract.CLUSTER_DEVNET -> "Devnet"
        ProtocolContract.CLUSTER_TESTNET -> "Testnet"
        else -> "Devnet"
    }

    companion object {
        private const val PREFS_NAME = "mwallet_state"
        private const val KEY_NETWORK = "selected_network"

        val CLUSTERS = listOf(
            ProtocolContract.CLUSTER_DEVNET,
            ProtocolContract.CLUSTER_MAINNET_BETA,
            ProtocolContract.CLUSTER_TESTNET
        )
    }
}
