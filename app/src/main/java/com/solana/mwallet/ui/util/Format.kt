/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.util

import android.text.format.DateUtils
import kotlin.math.absoluteValue

object Format {

    private const val LAMPORTS_PER_SOL = 1_000_000_000.0

    fun lamportsToSol(lamports: Long): String =
        "%.4f".format(lamports / LAMPORTS_PER_SOL).trimSuffixZeros()

    fun signedLamportsToSol(lamports: Long): String {
        val sol = lamports.absoluteValue / LAMPORTS_PER_SOL
        val prefix = if (lamports >= 0) "+" else "−"
        return "$prefix${"%.4f".format(sol).trimSuffixZeros()} SOL"
    }

    fun shortAddress(address: String?, head: Int = 4, tail: Int = 4): String {
        if (address.isNullOrBlank()) return "—"
        if (address.length <= head + tail + 1) return address
        return "${address.take(head)}…${address.takeLast(tail)}"
    }

    fun tokenAmount(uiAmount: Double, decimals: Int): String {
        val maxDecimals = decimals.coerceAtMost(6)
        return "%.${maxDecimals}f".format(uiAmount).trimSuffixZeros()
    }

    fun relativeTime(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0L) return "unknown time"
        val now = System.currentTimeMillis()
        val ts = epochSeconds * 1000L
        return DateUtils.getRelativeTimeSpanString(
            ts,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    fun absoluteTime(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0L) return "—"
        val date = java.util.Date(epochSeconds * 1000L)
        val format = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
        return format.format(date)
    }

    private fun String.trimSuffixZeros(): String {
        if (!contains(".")) return this
        return trimEnd('0').trimEnd('.')
    }

    fun explorerUrl(cluster: String, addressOrSig: String, isTx: Boolean = false): String {
        val base = "https://explorer.solana.com"
        val path = if (isTx) "tx/$addressOrSig" else "address/$addressOrSig"
        val clusterParam = if (cluster == "mainnet-beta") "" else "?cluster=$cluster"
        return "$base/$path$clusterParam"
    }
}
