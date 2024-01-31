/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.usecase

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.solana.networking.OkHttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.rpccore.JsonRpc20Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

// Note: this class is for testing purposes only. It does not comprehensively check for error
// results from the RPC server.
object SendTransactionsUseCase {
    @Suppress("BlockingMethodInNonBlockingContext") // runs in Dispatchers.IO
    suspend operator fun invoke(
        rpcUri: Uri,
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        waitForCommitmentToSendNextTransaction: Boolean?
    ) {
        withContext(Dispatchers.IO) {
            // Send all transactions and accumulate transaction signatures
            val signatures = Array<String?>(transactions.size) { null }
            val rpcClient = Rpc20Driver(rpcUri.toString(), OkHttpNetworkDriver(OkHttpClient()))
            transactions.forEachIndexed { i, transaction ->
                val transactionBase64 = Base64.encodeToString(transaction, Base64.NO_WRAP)
                Log.d(TAG, "Sending transaction: '$transactionBase64' with minContextSlot=$minContextSlot")

                signatures[i] = rpcClient.makeRequest(
                    SendTransactionRequest(transactionBase64,
                        minContextSlot, commitment, skipPreflight, maxRetries).toJsonRpc20Request(),
                    String.serializer()
                ).run {
                    error?.let {
                        Log.e(TAG, "Failed sending transaction, response code=${it.code}")
                        null
                    } ?: result?.apply {
                        if (waitForCommitmentToSendNextTransaction == true
                            && !confirmTransactions(listOf(this), commitment ?: "processed", rpcClient)) {
                            Log.e(TAG, "transaction confirmation failed (tx id: $this)")
                            return@forEachIndexed
                        }
                    } ?: run {
                        Log.e(TAG, "sendTransaction did not return a signature, response=${result}")
                        null
                    }
                }
            }

            // Ensure all transactions were submitted successfully
            val valid = signatures.map { signature -> signature != null }
            if (valid.any { !it }) {
                throw InvalidTransactionsException(valid.toBooleanArray())
            }
        }
    }

    suspend fun confirmTransactions(txids: List<String>, commitment: String, rpcClient: Rpc20Driver): Boolean =
        withTimeout(TIMEOUT_MS.toLong()) {
            suspend fun getStatuses() = rpcClient.makeRequest<SignatureStatusesResponse>(
                SignatureStatusesRequest(txids).toJsonRpc20Request(),
                SignatureStatusesResponse.serializer()
            ).apply {
                error?.let {
                    throw Error("Could not retrieve transaction status: ${it.message}")
                }
            }.result

            // wait for desired transaction status
            var inc = 1L
            while (true) {
                val currentStatuses = getStatuses()?.value

                if (currentStatuses?.any {
                        it?.confirmationStatus.commitmentOrdinal < commitment.commitmentOrdinal
                } == true) {
                    // Exponential delay before retrying.
                    delay(500 * inc)
                } else {
                    return@withTimeout true
                }

                // breakout after timeout
                if (!isActive) break
                inc++
            }

            return@withTimeout false
        }

    private val TAG = SendTransactionsUseCase::class.simpleName
    private const val TIMEOUT_MS = 20000

    private val String?.commitmentOrdinal: Int
        get() = when(this) {
            "processed" -> 0
            "confirmed" -> 1
            "finalized" -> 2
            else -> -1
        }

    class SendTransactionRequest(
        transactionBase64: String,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?
    ) : JsonRpc20Request("sendTransaction", id = "1",
        params = buildJsonArray {
            add(transactionBase64)
            addJsonObject {
                put("encoding", "base64")
                put("preflightCommitment", commitment ?: "processed")
                if (minContextSlot != null) {
                    put("minContextSlot", minContextSlot)
                }
                if (skipPreflight != null) {
                    put("skipPreflight", skipPreflight)
                }
                if (maxRetries != null) {
                    put("maxRetries", maxRetries)
                }
            }
        }) {
        // TODO: update rpc-core once polymorphic serialization bug is fixed
        fun toJsonRpc20Request() = JsonRpc20Request(method, params, id)
    }

    private fun SignatureStatusRequest(signatureBase64: String) =
        SignatureStatusesRequest(listOf(signatureBase64))

    class SignatureStatusesRequest(signatures: List<String>)
        : JsonRpc20Request("getSignatureStatuses", id = "1",
        params = buildJsonArray {
            addJsonArray {
                signatures.forEach { add(it) }
            }
            addJsonObject {
                put("searchTransactionHistory", false)
            }
        }) {
        // TODO: update rpc-core once polymorphic serialization bug is fixed
        fun toJsonRpc20Request() = JsonRpc20Request(method, params, id)
    }

    @Serializable
    class SignatureStatusesResponse(
        val context: JsonElement?,
        val value: List<SignatureStatus?>
    )

    @Serializable
    class SignatureStatus(
        val slot: ULong,
        val confirmations: Int?,
        @SerialName("err") val error: JsonElement?,
        val confirmationStatus: String?
    )

    class InvalidTransactionsException(val valid: BooleanArray, message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
}