/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface Ed25519KeyDao {
    @Query("SELECT * FROM keys WHERE public_key_b64 = :publicKeyBase64")
    fun get(publicKeyBase64: String): Ed25519KeyPair?

    @Query("SELECT public_key_b64 FROM keys")
    fun getAllPublicKeysBase64(): List<String>

    @Insert
    fun insert(keypair: Ed25519KeyPair): Long
}