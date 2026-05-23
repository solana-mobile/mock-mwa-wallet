/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.solana.mwallet.R
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.usecase.SignaturesUseCase
import com.solana.mwallet.usecase.SignaturesUseCase.TransactionSummary

class RecentActivityAdapter(
    private val onClick: (TransactionSummary) -> Unit
) : RecyclerView.Adapter<RecentActivityAdapter.VH>() {

    private val items = mutableListOf<TransactionSummary>()

    fun submit(list: List<TransactionSummary>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val badgeBg: View = view.findViewById(R.id.tx_badge_bg)
        private val badgeIcon: ImageView = view.findViewById(R.id.tx_badge_icon)
        private val title: TextView = view.findViewById(R.id.tx_title)
        private val subtitle: TextView = view.findViewById(R.id.tx_subtitle)
        private val amount: TextView = view.findViewById(R.id.tx_amount)
        private val status: TextView = view.findViewById(R.id.tx_status)

        fun bind(item: TransactionSummary) {
            val ctx = itemView.context
            when (item.direction) {
                TransactionSummary.Direction.IN -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_in)
                    badgeIcon.setImageResource(R.drawable.ic_arrow_down_in)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_success))
                    title.text = ctx.getString(R.string.history_type_receive)
                    subtitle.text = subtitleLine(item, ctx.getString(R.string.label_pubkey))
                    amount.text = "+${Format.lamportsToSol(item.lamportsDelta)} SOL"
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_success))
                }
                TransactionSummary.Direction.OUT -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_out)
                    badgeIcon.setImageResource(R.drawable.ic_arrow_up_out)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_danger))
                    title.text = ctx.getString(R.string.history_type_send)
                    subtitle.text = subtitleLine(item, "to")
                    val outLamports = -(item.lamportsDelta + item.fee)
                    amount.text = "−${Format.lamportsToSol(outLamports.coerceAtLeast(0))} SOL"
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_danger))
                }
                TransactionSummary.Direction.PROGRAM -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_neutral)
                    badgeIcon.setImageResource(R.drawable.ic_code_neutral)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_info))
                    title.text = ctx.getString(R.string.history_type_program)
                    subtitle.text = "${item.programs.firstOrNull()?.let(Format::shortAddress) ?: "Program"} · ${Format.relativeTime(item.blockTime)}"
                    amount.text = "−${Format.lamportsToSol(item.fee)} SOL"
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
                }
                TransactionSummary.Direction.FAILED -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_out)
                    badgeIcon.setImageResource(R.drawable.ic_close_24)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_danger))
                    title.text = ctx.getString(R.string.history_status_failed)
                    subtitle.text = Format.relativeTime(item.blockTime)
                    amount.text = "Failed"
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_danger))
                }
                TransactionSummary.Direction.SELF, TransactionSummary.Direction.UNKNOWN -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_neutral)
                    badgeIcon.setImageResource(R.drawable.ic_code_neutral)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_info))
                    title.text = ctx.getString(R.string.history_type_unknown)
                    subtitle.text = Format.relativeTime(item.blockTime)
                    amount.text = Format.signedLamportsToSol(item.lamportsDelta)
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                }
            }
            status.text = item.confirmationStatusOrSlot()
            itemView.setOnClickListener { onClick(item) }
        }

        private fun subtitleLine(item: TransactionSummary, label: String): String {
            val cp = item.counterparty?.let { Format.shortAddress(it) } ?: "unknown"
            val time = Format.relativeTime(item.blockTime)
            return "$label $cp · $time"
        }
    }
}

fun TransactionSummary.confirmationStatusOrSlot(): String = "slot $slot"
