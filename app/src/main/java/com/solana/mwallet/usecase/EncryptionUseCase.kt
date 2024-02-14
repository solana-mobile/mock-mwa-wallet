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
        val ivParams = IvParameterSpec(encryptedBytes.sliceArray(48 until encryptedBytes.size))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams)

        return cipher.doFinal(encryptedBytes.sliceArray(0 until 48)).also {
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
                        setUserAuthenticationParameters(300,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    } else {
                        setUserAuthenticationValidityDurationSeconds(300)
                    }
                }
                .build()).run { getSecretKey() }

    private fun getCipher(): Cipher =
        Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7)
}