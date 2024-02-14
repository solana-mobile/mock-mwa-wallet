/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.solana.mwallet.databinding.ActivityMainBinding
import com.solana.mwallet.usecase.UserAuthenticationUseCase

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.button.setOnClickListener {
            UserAuthenticationUseCase.authenticate(this) { result , error ->
                Toast.makeText(applicationContext,
                    result?.let { "Authentication succeeded!" }
                        ?: error?.let { "Authentication error: $it" }
                        ?: "Authentication failed", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}