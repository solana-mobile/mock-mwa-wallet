/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.usecase.SignaturesUseCase
import com.solana.mwallet.usecase.SignaturesUseCase.TransactionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionDetailSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.Theme_MwalletApp_BottomSheetDialog2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_transaction_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val signature = args.getString(ARG_SIG).orEmpty()
        val cluster = args.getString(ARG_CLUSTER, "devnet")
        val app = requireActivity().application as MwalletApplication
        val owner = app.walletState.publicKey.value.orEmpty()

        view.findViewById<TextView>(R.id.detail_signature).text = signature
        view.findViewById<AppCompatButton>(R.id.btn_detail_explorer).setOnClickListener {
            val url = Format.explorerUrl(cluster, signature, isTx = true)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                SignaturesUseCase.getTransactionSummary(app.walletState.rpcUri(), signature, owner)
            }
            renderSummary(view, summary, signature)
        }
    }

    private fun renderSummary(view: View, summary: TransactionSummary?, signature: String) {
        val ctx = requireContext()
        val title: TextView = view.findViewById(R.id.detail_title)
        val whenView: TextView = view.findViewById(R.id.detail_when)
        val amount: TextView = view.findViewById(R.id.detail_amount)
        val slot: TextView = view.findViewById(R.id.detail_slot)
        val blockTime: TextView = view.findViewById(R.id.detail_block_time)
        val fee: TextView = view.findViewById(R.id.detail_fee)
        val badgeBg: View = view.findViewById(R.id.detail_badge_bg)
        val badgeIcon: ImageView = view.findViewById(R.id.detail_badge_icon)

        if (summary == null) {
            title.text = ctx.getString(R.string.history_type_unknown)
            whenView.text = "—"
            amount.text = "—"
            slot.text = "—"
            blockTime.text = "—"
            fee.text = "—"
            return
        }

        when (summary.direction) {
            TransactionSummary.Direction.IN -> {
                title.text = ctx.getString(R.string.history_type_receive)
                amount.text = "+${Format.lamportsToSol(summary.lamportsDelta)} SOL"
                amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_success))
                badgeBg.setBackgroundResource(R.drawable.background_tx_in)
                badgeIcon.setImageResource(R.drawable.ic_arrow_down_in)
                badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_success))
            }
            TransactionSummary.Direction.OUT -> {
                title.text = ctx.getString(R.string.history_type_send)
                val out = -(summary.lamportsDelta + summary.fee)
                amount.text = "−${Format.lamportsToSol(out.coerceAtLeast(0))} SOL"
                amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_danger))
                badgeBg.setBackgroundResource(R.drawable.background_tx_out)
                badgeIcon.setImageResource(R.drawable.ic_arrow_up_out)
                badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_danger))
            }
            TransactionSummary.Direction.PROGRAM -> {
                title.text = ctx.getString(R.string.history_type_program)
                amount.text = "−${Format.lamportsToSol(summary.fee)} SOL"
                amount.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                badgeBg.setBackgroundResource(R.drawable.background_tx_neutral)
                badgeIcon.setImageResource(R.drawable.ic_code_neutral)
                badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_info))
            }
            TransactionSummary.Direction.FAILED -> {
                title.text = ctx.getString(R.string.history_status_failed)
                amount.text = "Failed"
                amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_danger))
                badgeBg.setBackgroundResource(R.drawable.background_tx_out)
                badgeIcon.setImageResource(R.drawable.ic_close_24)
                badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_danger))
            }
            TransactionSummary.Direction.SELF, TransactionSummary.Direction.UNKNOWN -> {
                title.text = ctx.getString(R.string.history_type_unknown)
                amount.text = Format.signedLamportsToSol(summary.lamportsDelta)
                badgeBg.setBackgroundResource(R.drawable.background_tx_neutral)
                badgeIcon.setImageResource(R.drawable.ic_code_neutral)
                badgeIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_info))
            }
        }

        whenView.text = "${Format.relativeTime(summary.blockTime)} · ${if (summary.err == null) ctx.getString(R.string.history_status_confirmed) else ctx.getString(R.string.history_status_failed)}"
        slot.text = summary.slot.toString()
        blockTime.text = Format.absoluteTime(summary.blockTime)
        fee.text = "${Format.lamportsToSol(summary.fee)} SOL"
    }

    companion object {
        const val TAG = "TransactionDetailSheet"
        private const val ARG_SIG = "sig"
        private const val ARG_CLUSTER = "cluster"

        fun newInstance(signature: String, cluster: String): TransactionDetailSheet {
            return TransactionDetailSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SIG, signature)
                    putString(ARG_CLUSTER, cluster)
                }
            }
        }
    }
}
