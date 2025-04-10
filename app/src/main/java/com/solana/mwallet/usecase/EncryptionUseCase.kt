package com.solana.mwallet.usecase

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.funkatronics.encoders.Base64
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object EncryptionUseCase {
    private val TAG = EncryptionUseCase::class.simpleName
    private const val KEY_NAME = "secret key name"
    private const val KEY_VALIDITY_SECONDS = 900 // 15 minutes
    private const val ENCRYPTED_PRIVATE_KEY_LENGTH_BYTES = 48

    fun encrypt(plaintextString: String): ByteArray =
        encrypt(plaintextString.toByteArray(Charset.defaultCharset()))

    fun encryptBase64(plaintextString: String): String =
        Base64.encodeToString(encrypt(plaintextString))

    fun encrypt(plaintextBytes: ByteArray): ByteArray {
        // Exceptions are unhandled for getCipher() and getSecretKey().
        val cipher = getCipher()
        val secretKey = getSecretKey()
        // InvalidKeyException
        // UserNotAuthenticatedException (key validity timed out)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ivParams = cipher.parameters.getParameterSpec(IvParameterSpec::class.java)
        return cipher.doFinal(plaintextBytes).also {
            Log.d(TAG, "Encrypted information: ${Base64.encodeToString(it)}")
        } + ivParams.iv
    }

    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        // Exceptions are unhandled for getCipher() and getSecretKey().
        val cipher = getCipher()
        val secretKey = getSecretKey()
        val ivParams = IvParameterSpec(
            encryptedBytes.sliceArray(ENCRYPTED_PRIVATE_KEY_LENGTH_BYTES until encryptedBytes.size))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams)

        return cipher.doFinal(
            encryptedBytes.sliceArray(0 until ENCRYPTED_PRIVATE_KEY_LENGTH_BYTES)
        ).also {
            Log.d(TAG, "Decrypted information: ${it.decodeToString()}")
        }
    }

    private fun generateSecretKey(keyGenParameterSpec: KeyGenParameterSpec) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey =
        (KeyStore.getInstance("AndroidKeyStore").apply {
            // Before the keystore can be accessed, it must be loaded.
            load(null)
        }.getKey(KEY_NAME, null) as? SecretKey) ?:
            generateSecretKey(KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(KEY_VALIDITY_SECONDS,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    } else {
                        setUserAuthenticationValidityDurationSeconds(KEY_VALIDITY_SECONDS)
                    }
                }
                .build()).run { getSecretKey() }

    private fun getCipher(): Cipher =
        Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7)
}