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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * Fetches SPL token accounts owned by a given Solana address.
 * Returns a flattened list of non-zero balances with their decimals and mint address.
 */
object TokenAccountsUseCase {

    private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

    data class TokenAccount(
        val mint: String,
        val amount: Long,
        val decimals: Int,
        val tokenAccount: String,
        val uiAmount: Double
    )

    suspend fun getTokenAccounts(rpcUri: Uri, addressBase58: String): List<TokenAccount> =
        withContext(Dispatchers.IO) {
            val driver = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            val results = mutableListOf<TokenAccount>()
            listOf(TOKEN_PROGRAM, TOKEN_2022_PROGRAM).forEach { programId ->
                try {
                    val req = JsonRpc20Request(
                        method = "getTokenAccountsByOwner",
                        id = "1",
                        params = buildJsonArray {
                            add(addressBase58)
                            addJsonObject { put("programId", programId) }
                            addJsonObject {
                                put("encoding", "jsonParsed")
                                put("commitment", "confirmed")
                            }
                        }
                    )
                    val res = driver.makeRequest(req, TokenAccountsResponse.serializer())
                    res.result?.value?.forEach { acct ->
                        val info = acct.account?.data?.parsed?.info ?: return@forEach
                        val tokenAmount = info.tokenAmount ?: return@forEach
                        val amount = tokenAmount.amount?.toLongOrNull() ?: 0L
                        if (amount <= 0L) return@forEach
                        results.add(
                            TokenAccount(
                                mint = info.mint ?: "",
                                amount = amount,
                                decimals = tokenAmount.decimals ?: 0,
                                tokenAccount = acct.pubkey ?: "",
                                uiAmount = tokenAmount.uiAmount ?: (amount / Math.pow(10.0, (tokenAmount.decimals ?: 0).toDouble()))
                            )
                        )
                    }
                } catch (_: Exception) {
                    // continue with next program
                }
            }
            results.sortedByDescending { it.uiAmount }
        }

    @Serializable
    private data class TokenAccountsResponse(
        val context: JsonElement? = null,
        val value: List<TokenAccountEntry>? = null
    )

    @Serializable
    private data class TokenAccountEntry(
        val pubkey: String? = null,
        val account: AccountInfo? = null
    )

    @Serializable
    private data class AccountInfo(
        val data: AccountData? = null,
        val executable: Boolean? = null,
        val lamports: Long? = null,
        val owner: String? = null,
        val rentEpoch: JsonElement? = null,
        val space: Int? = null
    )

    @Serializable
    private data class AccountData(
        val program: String? = null,
        val parsed: ParsedData? = null,
        val space: Int? = null
    )

    @Serializable
    private data class ParsedData(
        val type: String? = null,
        val info: ParsedInfo? = null
    )

    @Serializable
    private data class ParsedInfo(
        val mint: String? = null,
        val owner: String? = null,
        val state: String? = null,
        val tokenAmount: TokenAmount? = null
    )

    @Serializable
    private data class TokenAmount(
        val amount: String? = null,
        val decimals: Int? = null,
        val uiAmount: Double? = null,
        val uiAmountString: String? = null
    )
}
