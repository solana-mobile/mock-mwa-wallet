/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import com.funkatronics.encoders.Base58
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

object LocalKeypair {
    fun derivePrivateKey(seedPhrase: String, accountIndex: Int = 0): ByteArray {
        var key = deriveMasterPrivateKey(seedPhraseToSeed(seedPhrase))
        for (index in solanaDerivationPath(accountIndex)) {
            key = deriveChildPrivateKey(key, index)
        }
        return key.privateKey
    }

    fun derivePublicKey(seedPhrase: String, accountIndex: Int = 0): String =
        publicKeyForPrivateKey(derivePrivateKey(seedPhrase, accountIndex))

    fun generateSeedPhrase(): String {
        val random = SecureRandom()
        return (0 until BIP39_WORD_COUNT_SHORT).joinToString(" ") {
            DEVELOPMENT_SEED_WORDS[random.nextInt(DEVELOPMENT_SEED_WORDS.size)]
        }
    }

    fun normalizeSeedPhrase(seedPhrase: String): String {
        val words = seedPhrase.trim().lowercase(Locale.US).split(Regex("\\s+"))
        require(words.size == BIP39_WORD_COUNT_SHORT || words.size == BIP39_WORD_COUNT_LONG) {
            "seed phrase must contain $BIP39_WORD_COUNT_SHORT or $BIP39_WORD_COUNT_LONG words"
        }
        return words.joinToString(" ")
    }

    fun solanaDerivationPathUri(accountIndex: Int): String =
        "m/44'/501'/$accountIndex'"

    private fun deriveChildPrivateKey(parent: KeyDerivationMaterial, index: Int): KeyDerivationMaterial {
        val hmac = Mac.getInstance(MAC)
        hmac.init(SecretKeySpec(parent.chainCode, MAC))
        hmac.update(0.toByte())
        hmac.update(parent.privateKey)
        hmac.update(index.or(0x8000_0000.toInt()).toBigEndianByteArray())
        val data = hmac.doFinal()
        return KeyDerivationMaterial(data.copyOfRange(32, 64), data.copyOf(32))
    }

    private fun deriveMasterPrivateKey(seed: ByteArray): KeyDerivationMaterial {
        val hmac = Mac.getInstance(MAC)
        hmac.init(SecretKeySpec(ED25519_SLIP10_MASTER_KEY.encodeToByteArray(), MAC))
        val data = hmac.doFinal(seed)
        return KeyDerivationMaterial(data.copyOfRange(32, 64), data.copyOf(32))
    }

    private fun publicKeyForPrivateKey(privateKey: ByteArray): String {
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
        return Base58.encodeToString(privateKeyParams.generatePublicKey().encoded)
    }

    private fun seedPhraseToSeed(seedPhrase: String): ByteArray {
        val keySpec = PBEKeySpec(
            normalizeSeedPhrase(seedPhrase).toCharArray(),
            BIP39_SALT.encodeToByteArray(),
            BIP39_ITERATION_COUNT,
            BIP39_SEED_SIZE_BITS
        )
        return try {
            SecretKeyFactory.getInstance(PBKDF2_HMAC_SHA512).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    private data class KeyDerivationMaterial(
        val chainCode: ByteArray,
        val privateKey: ByteArray,
    )

    private fun solanaDerivationPath(accountIndex: Int): IntArray =
        intArrayOf(44, 501, accountIndex)

    private const val BIP39_ITERATION_COUNT = 2048
    private const val BIP39_SALT = "mnemonic"
    private const val BIP39_SEED_SIZE_BITS = 512
    private const val BIP39_WORD_COUNT_LONG = 24
    private const val BIP39_WORD_COUNT_SHORT = 12
    private const val ED25519_SLIP10_MASTER_KEY = "ed25519 seed"
    private const val MAC = "HmacSHA512"
    private const val PBKDF2_HMAC_SHA512 = "PBKDF2withHmacSHA512"

    private val DEVELOPMENT_SEED_WORDS = arrayOf(
        "abandon",
        "ability",
        "able",
        "about",
        "above",
        "absent",
        "absorb",
        "abstract",
        "absurd",
        "abuse",
        "access",
        "accident",
        "account",
        "accuse",
        "achieve",
        "acid",
        "acoustic",
        "acquire",
        "across",
        "act",
        "action",
        "actor",
        "actress",
        "actual",
        "adapt",
        "add",
        "addict",
        "address",
        "adjust",
        "admit",
        "adult",
        "advance",
    )
}

private fun Int.toBigEndianByteArray(): ByteArray {
    val bytes = ByteArray(4)
    bytes[0] = shr(24).toByte()
    bytes[1] = shr(16).toByte()
    bytes[2] = shr(8).toByte()
    bytes[3] = toByte()
    return bytes
}
