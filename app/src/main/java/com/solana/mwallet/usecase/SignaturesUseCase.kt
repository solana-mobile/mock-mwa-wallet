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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * Fetches recent signatures for an address, plus light-weight transaction summaries.
 *
 * Two-phase: first call getSignaturesForAddress for cheap listing, then call getTransaction
 * (jsonParsed) lazily for each row to derive direction and amount changes.
 */
object SignaturesUseCase {

    data class SignatureInfo(
        val signature: String,
        val slot: Long,
        val blockTime: Long?,
        val err: String?,
        val memo: String?,
        val confirmationStatus: String?
    )

    data class TransactionSummary(
        val signature: String,
        val slot: Long,
        val blockTime: Long?,
        val fee: Long,
        val err: String?,
        val memo: String?,
        val direction: Direction,
        val lamportsDelta: Long,
        val counterparty: String?,
        val programs: List<String>,
        val rawJson: String?
    ) {
        enum class Direction { IN, OUT, SELF, PROGRAM, FAILED, UNKNOWN }
    }

    suspend fun getSignaturesForAddress(
        rpcUri: Uri,
        addressBase58: String,
        limit: Int = 25
    ): List<SignatureInfo> = withContext(Dispatchers.IO) {
        try {
            val driver = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            val req = JsonRpc20Request(
                method = "getSignaturesForAddress",
                id = "1",
                params = buildJsonArray {
                    add(addressBase58)
                    addJsonObject {
                        put("limit", limit)
                        put("commitment", "confirmed")
                    }
                }
            )
            val res = driver.makeRequest(req, ListSerializer(SignatureResult.serializer()))
            if (res.error != null) return@withContext emptyList()
            res.result?.map { entry ->
                SignatureInfo(
                    signature = entry.signature ?: "",
                    slot = entry.slot ?: 0L,
                    blockTime = entry.blockTime,
                    err = entry.err?.takeIf { it !is JsonNull }?.toString(),
                    memo = entry.memo,
                    confirmationStatus = entry.confirmationStatus
                )
            }?.filter { it.signature.isNotBlank() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTransactionSummary(
        rpcUri: Uri,
        signature: String,
        owner: String
    ): TransactionSummary? = withContext(Dispatchers.IO) {
        try {
            val driver = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            val req = JsonRpc20Request(
                method = "getTransaction",
                id = "1",
                params = buildJsonArray {
                    add(signature)
                    addJsonObject {
                        put("encoding", "jsonParsed")
                        put("commitment", "confirmed")
                        put("maxSupportedTransactionVersion", 0)
                    }
                }
            )
            val res = driver.makeRequest(req, TxResult.serializer())
            if (res.error != null) return@withContext null
            val result = res.result ?: return@withContext null

            val accountKeys = result.transaction?.message?.accountKeys?.map { it.pubkey ?: "" } ?: emptyList()
            val ownerIdx = accountKeys.indexOf(owner).takeIf { it >= 0 }
            val preBalances = result.meta?.preBalances ?: emptyList()
            val postBalances = result.meta?.postBalances ?: emptyList()
            val fee = result.meta?.fee ?: 0L

            val lamportsDelta: Long = if (ownerIdx != null && ownerIdx < preBalances.size && ownerIdx < postBalances.size) {
                postBalances[ownerIdx] - preBalances[ownerIdx]
            } else 0L

            val programs = result.transaction?.message?.instructions
                ?.mapNotNull { it.programId }
                ?.distinct()
                ?: emptyList()

            val hasFailure = result.meta?.err != null && result.meta.err !is JsonNull

            val direction = when {
                hasFailure -> TransactionSummary.Direction.FAILED
                lamportsDelta > 0 -> TransactionSummary.Direction.IN
                lamportsDelta < -fee -> TransactionSummary.Direction.OUT
                lamportsDelta == -fee && programs.any { it != "11111111111111111111111111111111" } ->
                    TransactionSummary.Direction.PROGRAM
                lamportsDelta == 0L -> TransactionSummary.Direction.PROGRAM
                else -> TransactionSummary.Direction.UNKNOWN
            }

            val counterparty: String? = if (direction == TransactionSummary.Direction.IN || direction == TransactionSummary.Direction.OUT) {
                accountKeys.firstOrNull { it != owner && it.isNotBlank() && it != "11111111111111111111111111111111" }
            } else null

            TransactionSummary(
                signature = signature,
                slot = result.slot ?: 0L,
                blockTime = result.blockTime,
                fee = fee,
                err = if (hasFailure) result.meta?.err?.toString() else null,
                memo = result.transaction?.message?.instructions?.firstOrNull {
                    it.program == "spl-memo" || it.programId?.startsWith("Memo") == true
                }?.parsed?.toString(),
                direction = direction,
                lamportsDelta = lamportsDelta,
                counterparty = counterparty,
                programs = programs,
                rawJson = null
            )
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class SignatureResult(
        val signature: String? = null,
        val slot: Long? = null,
        val blockTime: Long? = null,
        val err: JsonElement? = null,
        val memo: String? = null,
        val confirmationStatus: String? = null
    )

    @Serializable
    private data class TxResult(
        val slot: Long? = null,
        val blockTime: Long? = null,
        val meta: Meta? = null,
        val transaction: TxBody? = null
    )

    @Serializable
    private data class Meta(
        val err: JsonElement? = null,
        val fee: Long? = null,
        val preBalances: List<Long>? = null,
        val postBalances: List<Long>? = null,
        val logMessages: List<String>? = null
    )

    @Serializable
    private data class TxBody(
        val message: Message? = null,
        val signatures: List<String>? = null
    )

    @Serializable
    private data class Message(
        val accountKeys: List<AccountKey>? = null,
        val instructions: List<Instruction>? = null,
        val recentBlockhash: String? = null
    )

    @Serializable
    private data class AccountKey(
        val pubkey: String? = null,
        val signer: Boolean? = null,
        val writable: Boolean? = null,
        val source: String? = null
    )

    @Serializable
    private data class Instruction(
        val programId: String? = null,
        val program: String? = null,
        val parsed: JsonElement? = null
    )
}
