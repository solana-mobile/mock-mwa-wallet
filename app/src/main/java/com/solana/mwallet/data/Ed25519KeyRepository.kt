/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.data

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.solana.mwallet.usecase.EncryptionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

class Ed25519KeyRepository(private val application: Application) {
    private val db by lazy {
        Room.databaseBuilder(application, Ed25519KeyDatabase::class.java, "keys").build()
    }

    suspend fun clearKeypairs() {
        withContext(Dispatchers.IO) {
            db.keysDao().deleteAll()
        }
    }

    suspend fun insertKeypair(keypair: AsymmetricCipherKeyPair) {
        withContext(Dispatchers.IO) {
            val publicKey = keypair.public as Ed25519PublicKeyParameters
            val privateKey = keypair.private as Ed25519PrivateKeyParameters
            val publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_PADDING or Base64.NO_WRAP)
            val encryptedPrivateKey = EncryptionUseCase.encrypt(privateKey.encoded)
            val id = db.keysDao().insert(
                Ed25519KeyPair(publicKeyBase64 = publicKeyBase64,
                    encryptedPrivateKey = encryptedPrivateKey)
            )
            Log.d(TAG, "Inserted key entry with id=$id for $publicKeyBase64")
        }
    }

    suspend fun getKeypair(publicKeyRaw: ByteArray): AsymmetricCipherKeyPair? {
        val publicKeyBase64 = Base64.encodeToString(publicKeyRaw, Base64.NO_PADDING or Base64.NO_WRAP)
        return withContext(Dispatchers.IO) {
            db.keysDao().get(publicKeyBase64)?.let { keypair ->
                val privateKey = EncryptionUseCase.decrypt(keypair.encryptedPrivateKey)
                val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
                AsymmetricCipherKeyPair(privateKeyParams.generatePublicKey(), privateKeyParams)
            }
        }
    }

    companion object {
        private val TAG = Ed25519KeyRepository::class.simpleName
    }
}