/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.data

import android.app.Application
import android.content.Context
import android.util.Log
import com.solana.mwallet.BuildConfig
import com.solana.mwallet.LocalKeypair
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

class SeedPhraseRepository(application: Application) {
    private val generatedSeedPhrase: String by lazy {
        LocalKeypair.generateSeedPhrase()
    }

    private val preferences =
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }

    fun deriveNextWallet(): DerivedWallet? {
        val seedPhrase = getSeedPhrase() ?: return null
        val currentCount = getDerivedWalletCount()
        val name = getOrCreateWalletName(currentCount)
        preferences.edit()
            .putInt(KEY_DERIVED_WALLET_COUNT, currentCount + 1)
            .apply()
        return deriveWallet(seedPhrase, currentCount, name)
    }

    fun getDerivedWallets(): List<DerivedWallet> {
        val seedPhrase = getSeedPhrase() ?: return emptyList()
        return (0 until getDerivedWalletCount()).map { index ->
            deriveWallet(seedPhrase, index, getOrCreateWalletName(index))
        }
    }

    fun getPrimaryKeypair(): AsymmetricCipherKeyPair? {
        val seedPhrase = getSeedPhrase() ?: return null
        val privateKeyParams = Ed25519PrivateKeyParameters(
            LocalKeypair.derivePrivateKey(seedPhrase),
            0
        )
        return AsymmetricCipherKeyPair(
            privateKeyParams.generatePublicKey(),
            privateKeyParams
        )
    }

    fun getPrimaryWallet(): DerivedWallet? =
        getDerivedWallets().firstOrNull()

    fun getSeedPhrase(): String? =
        preferences.getString(KEY_SEED_PHRASE, null)?.takeIf { it.isNotBlank() }

    fun importAvailableSeedPhraseIfEmpty() {
        importSeedPhraseIfEmpty(getAvailableSeedPhrase())
    }

    fun importSeedPhraseIfEmpty(seedPhrase: String?) {
        if (getSeedPhrase() != null || seedPhrase.isNullOrBlank()) {
            return
        }

        val normalizedSeedPhrase = runCatching {
            LocalKeypair.normalizeSeedPhrase(seedPhrase)
        }.getOrElse {
            Log.w(TAG, "Ignoring invalid seed phrase", it)
            return
        }

        preferences.edit()
            .putInt(KEY_DERIVED_WALLET_COUNT, DEFAULT_DERIVED_WALLET_COUNT)
            .putString(KEY_SEED_PHRASE, normalizedSeedPhrase)
            .putString(walletNameKey(0), getDefaultWalletName(0))
            .apply()
    }

    private fun getAvailableSeedPhrase(): String =
        BuildConfig.SEED_PHRASE?.takeIf { it.isNotBlank() } ?: generatedSeedPhrase

    private fun deriveWallet(seedPhrase: String, index: Int, name: String): DerivedWallet =
        DerivedWallet(
            address = LocalKeypair.derivePublicKey(seedPhrase, index),
            derivationPath = LocalKeypair.solanaDerivationPathUri(index),
            index = index,
            name = name
        )

    private fun getDefaultWalletName(index: Int): String =
        if (index == 0) {
            BuildConfig.WALLET_NAME
        } else {
            "${BuildConfig.WALLET_NAME} ${index + 1}"
        }

    private fun getDerivedWalletCount(): Int =
        preferences.getInt(KEY_DERIVED_WALLET_COUNT, DEFAULT_DERIVED_WALLET_COUNT)
            .coerceAtLeast(DEFAULT_DERIVED_WALLET_COUNT)

    private fun getOrCreateWalletName(index: Int): String {
        val key = walletNameKey(index)
        preferences.getString(key, null)?.takeIf { it.isNotBlank() }?.let { name ->
            return name
        }

        val name = getDefaultWalletName(index)
        preferences.edit()
            .putString(key, name)
            .apply()
        return name
    }

    private fun walletNameKey(index: Int): String =
        "$KEY_DERIVED_WALLET_NAME_PREFIX$index"

    data class DerivedWallet(
        val address: String,
        val derivationPath: String,
        val index: Int,
        val name: String,
    )

    companion object {
        private val TAG = SeedPhraseRepository::class.simpleName

        private const val DEFAULT_DERIVED_WALLET_COUNT = 1
        private const val KEY_DERIVED_WALLET_COUNT = "derived_wallet_count"
        private const val KEY_DERIVED_WALLET_NAME_PREFIX = "derived_wallet_name_"
        private const val KEY_SEED_PHRASE = "seed_phrase"
        private const val PREFERENCES_NAME = "seed_phrase_repository"
    }
}
