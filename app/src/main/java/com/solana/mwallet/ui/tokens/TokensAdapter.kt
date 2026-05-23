/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.tokens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.solana.mwallet.R
import com.solana.mwallet.ui.util.Format
import com.solana.mwallet.usecase.TokenMetadata

class TokensAdapter(
    private val onClick: (TokenRow) -> Unit
) : RecyclerView.Adapter<TokensAdapter.VH>() {

    data class TokenRow(
        val mint: String,
        val uiAmount: Double,
        val decimals: Int,
        val isSol: Boolean = false
    )

    private val items = mutableListOf<TokenRow>()

    fun submit(list: List<TokenRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_token, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: View = view.findViewById(R.id.token_icon)
        private val symbol: TextView = view.findViewById(R.id.token_symbol)
        private val name: TextView = view.findViewById(R.id.token_name)
        private val amount: TextView = view.findViewById(R.id.token_amount)
        private val mintText: TextView = view.findViewById(R.id.token_mint)

        fun bind(item: TokenRow) {
            val sym = if (item.isSol) "SOL" else TokenMetadata.symbolOrShortMint(item.mint)
            val nm = if (item.isSol) itemView.context.getString(R.string.tokens_sol_name)
            else TokenMetadata.displayName(item.mint)
            symbol.text = sym
            name.text = nm
            amount.text = Format.tokenAmount(item.uiAmount, item.decimals)
            mintText.text = if (item.isSol) "native" else Format.shortAddress(item.mint, 4, 4)
            icon.setBackgroundResource(
                if (item.isSol) R.drawable.background_token_icon
                else TokenMetadata.iconRes(item.mint)
            )
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
