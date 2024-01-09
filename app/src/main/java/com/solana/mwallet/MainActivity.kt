/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.solana.mwallet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
    }
}