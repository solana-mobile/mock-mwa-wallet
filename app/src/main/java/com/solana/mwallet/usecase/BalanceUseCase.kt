/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.usecase

import android.net.Uri
import com.solana.networking.OkHttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import okhttp3.OkHttpClient

object BalanceUseCase {
    /**
     * Fetches SOL balance in lamports for the given address on the given RPC endpoint.
     * @return balance in lamports, or null on error
     */
    suspend fun getBalance(rpcUri: Uri, addressBase58: String): Long? = withContext(Dispatchers.IO) {
        try {
            val rpcClient = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            val request = JsonRpc20Request(
                method = "getBalance",
                id = "1",
                params = buildJsonArray { add(JsonPrimitive(addressBase58)) }
            )
            val response = rpcClient.makeRequest<GetBalanceResponse>(
                request,
                GetBalanceResponse.serializer()
            )
            response.error?.let { null } ?: response.result?.value
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class GetBalanceResponse(
        val context: JsonContext? = null,
        val value: Long = 0L
    )

    @Serializable
    private data class JsonContext(
        @SerialName("slot") val slot: Long? = null
    )
}
