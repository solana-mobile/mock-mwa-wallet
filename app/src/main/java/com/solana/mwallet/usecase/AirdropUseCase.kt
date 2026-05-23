/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.usecase

import android.net.Uri
import com.solana.networking.OkHttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import okhttp3.OkHttpClient

/** Requests a devnet/testnet airdrop. Returns the signature on success. */
object AirdropUseCase {

    suspend fun requestAirdrop(
        rpcUri: Uri,
        addressBase58: String,
        lamports: Long = 1_000_000_000L
    ): String? = withContext(Dispatchers.IO) {
        try {
            val driver = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            val req = JsonRpc20Request(
                method = "requestAirdrop",
                id = "1",
                params = buildJsonArray {
                    add(addressBase58)
                    add(lamports)
                }
            )
            val res = driver.makeRequest(req, String.serializer())
            if (res.error != null) null else res.result
        } catch (_: Exception) {
            null
        }
    }
}
