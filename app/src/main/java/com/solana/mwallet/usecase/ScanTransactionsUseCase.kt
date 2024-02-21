package com.solana.mwallet.usecase

import com.funkatronics.encoders.Base58
import com.solana.mwallet.BuildConfig
import com.solana.mwallet.endpoints.BlowfishEndpoints
import com.solana.mwallet.endpoints.ScanTransactionsRequest
import com.solana.mwallet.endpoints.ScanTransactionsSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class ScanTransactionsUseCase(private val scope: CoroutineScope,
                              private val blowfish: BlowfishEndpoints) {

    val scanInProgress = TransactionScanInProgress()

    fun scanTransactionsAsync(
        chain: String,
        signer: ByteArray,
        transactions: List<ByteArray>,
        origin: String
    ): Deferred<TransactionScanState> {
        val cluster = when { // kinda hacky but works
            chain.contains("mainnet") -> "mainnet"
            chain.contains("devnet") -> "devnet"
            chain.contains("testnet") -> "testnet"
            else -> return CompletableDeferred(
                NotScanable("Cannot simulate transactions, provided chain is invalid: $chain"))
        }
        return scope.async(Dispatchers.IO) {
            try {
                val result = blowfish.scanTransactions(
                    cluster, ScanTransactionsRequest(
                        transactions.map { Base58.encodeToString(it) },
                        Base58.encodeToString(signer),
                        origin
                    ),
                    BuildConfig.BLOWFISH_API_KEY
                )
                TransactionScanSucceeded(result.summary)
            } catch (e: Exception) {
                TransactionScanFailed(e.message.toString())
            }
        }
    }

    sealed class TransactionScanState(val summary: ScanTransactionsSummary?)
    class TransactionScanInProgress internal constructor(): TransactionScanState(null)
    class TransactionScanSucceeded internal constructor(summary: ScanTransactionsSummary): TransactionScanState(summary)
    class TransactionScanFailed internal constructor(val error: String): TransactionScanState(null)
    class NotScanable internal constructor(val message: String): TransactionScanState(null)
}