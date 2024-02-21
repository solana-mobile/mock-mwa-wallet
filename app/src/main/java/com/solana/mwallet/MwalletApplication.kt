/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.app.Application
import com.solana.mwallet.data.Ed25519KeyRepository
import com.solana.mwallet.endpoints.BlowfishConverterFactory
import com.solana.mwallet.endpoints.BlowfishEndpoints
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class MwalletApplication : Application() {
    val keyRepository: Ed25519KeyRepository by lazy {
        Ed25519KeyRepository(this)
    }

    val blowfishService: BlowfishEndpoints by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.blowfish.xyz/")
            .client(OkHttpClient())
            .addConverterFactory(BlowfishConverterFactory)
            .build()

        retrofit.create(BlowfishEndpoints::class.java)
    }
}