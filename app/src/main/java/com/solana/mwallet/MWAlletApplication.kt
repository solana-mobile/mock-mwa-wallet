/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.app.Application
import com.solana.mwallet.data.Ed25519KeyRepository

class MWAlletApplication : Application() {
    val keyRepository: Ed25519KeyRepository by lazy {
        Ed25519KeyRepository(this)
    }
}