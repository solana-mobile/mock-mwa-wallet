/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.usecase

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Transaction
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object SolanaSigningUseCase {
    // throws IllegalArgumentException
    fun signTransaction(
        transactionBytes: ByteArray,
        keypair: AsymmetricCipherKeyPair
    ): SigningResult {
        val publicKey = SolanaPublicKey((keypair.public as Ed25519PublicKeyParameters).encoded)

        val message = try {
            Transaction.from(transactionBytes).message
        } catch (e: Exception) {
            throw IllegalArgumentException("Provided bytes do not represent a valid Solana transaction")
        }

        require(message.accounts.contains(publicKey)) {
            "Transaction does not require a signature with the requested keypair"
        }

        val signingResult = signMessage(message.serialize(), keypair)
        val signedTransaction = Transaction(listOf(signingResult.signature), message)

        return SigningResult(signedTransaction.serialize(), signingResult.signature)
    }

    fun signMessage(
        message: ByteArray,
        keypair: AsymmetricCipherKeyPair
    ): SigningResult {
        val privateKey = keypair.private as Ed25519PrivateKeyParameters

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        val sig = signer.generateSignature()
        assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

        val signedMessage = message.copyOf(message.size + SIGNATURE_LEN)
        sig.copyInto(signedMessage, message.size)

        return SigningResult(signedMessage, sig)
    }

    const val SIGNATURE_LEN = 64
    const val PUBLIC_KEY_LEN = 32

    data class SigningResult(
        val signedPayload: ByteArray,
        val signature: ByteArray
    )
}