/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.usecase

import androidx.annotation.DrawableRes
import com.solana.mwallet.R

/**
 * Hard-coded SPL token metadata for the most common assets we expect to see on mainnet/devnet.
 * Falls back to a generic icon and the truncated mint when the token is unknown.
 *
 * This is intentionally a static list — full metadata fetching (Metaplex / token list) is out of
 * scope for a mock wallet and would require additional RPC traffic for every list rendering.
 */
object TokenMetadata {

    data class Token(
        val mint: String,
        val symbol: String,
        val name: String,
        @DrawableRes val iconRes: Int = R.drawable.background_token_icon_generic
    )

    val SOL_PSEUDO_MINT = "So11111111111111111111111111111111111111112"

    private val byMint = listOf(
        Token(SOL_PSEUDO_MINT, "SOL", "Solana", R.drawable.background_token_icon),
        Token("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "USDC", "USD Coin", R.drawable.background_token_icon_usdc),
        Token("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", "USDT", "Tether USD", R.drawable.background_token_icon_usdc),
        Token("4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", "RAY", "Raydium", R.drawable.background_token_icon_generic),
        Token("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs", "ETH", "Wormhole ETH", R.drawable.background_token_icon_generic),
        Token("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So", "mSOL", "Marinade SOL", R.drawable.background_token_icon),
        Token("J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn", "JitoSOL", "Jito Staked SOL", R.drawable.background_token_icon),
        Token("JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN", "JUP", "Jupiter", R.drawable.background_token_icon_generic),
        Token("HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3", "PYTH", "Pyth Network", R.drawable.background_token_icon_generic),
    ).associateBy { it.mint }

    fun forMint(mint: String): Token? = byMint[mint]

    fun symbolOrShortMint(mint: String): String = byMint[mint]?.symbol ?: "${mint.take(4)}…${mint.takeLast(4)}"

    fun displayName(mint: String): String = byMint[mint]?.name ?: "Unknown token"

    @DrawableRes
    fun iconRes(mint: String): Int = byMint[mint]?.iconRes ?: R.drawable.background_token_icon_generic
}
