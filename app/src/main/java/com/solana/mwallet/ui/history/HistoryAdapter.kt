/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.solana.mwallet.R
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.usecase.SignaturesUseCase.TransactionSummary

class HistoryAdapter(
    private val onClick: (TransactionSummary) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val all = mutableListOf<TransactionSummary>()
    private val visible = mutableListOf<TransactionSummary>()
    private var filter: Filter = Filter.ALL

    enum class Filter { ALL, IN, OUT, PROGRAM }

    fun submit(list: List<TransactionSummary>) {
        all.clear()
        all.addAll(list)
        applyFilter()
    }

    fun setFilter(f: Filter) {
        filter = f
        applyFilter()
    }

    private fun applyFilter() {
        visible.clear()
        visible.addAll(when (filter) {
            Filter.ALL -> all
            Filter.IN -> all.filter { it.direction == TransactionSummary.Direction.IN }
            Filter.OUT -> all.filter { it.direction == TransactionSummary.Direction.OUT }
            Filter.PROGRAM -> all.filter {
                it.direction == TransactionSummary.Direction.PROGRAM ||
                    it.direction == TransactionSummary.Direction.SELF ||
                    it.direction == TransactionSummary.Direction.UNKNOWN
            }
        })
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = visible.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun getItemCount() = visible.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(visible[position])

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
                    subtitle.text = "From ${Format.shortAddress(item.counterparty)} · ${Format.relativeTime(item.blockTime)}"
                    amount.text = "+${Format.lamportsToSol(item.lamportsDelta)} SOL"
                    amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_success))
                }
                TransactionSummary.Direction.OUT -> {
                    badgeBg.setBackgroundResource(R.drawable.background_tx_out)
                    badgeIcon.setImageResource(R.drawable.ic_arrow_up_out)
                    badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_danger))
                    title.text = ctx.getString(R.string.history_type_send)
                    subtitle.text = "To ${Format.shortAddress(item.counterparty)} · ${Format.relativeTime(item.blockTime)}"
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
            status.text = if (item.err != null) ctx.getString(R.string.history_status_failed)
            else ctx.getString(R.string.history_status_confirmed)
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
