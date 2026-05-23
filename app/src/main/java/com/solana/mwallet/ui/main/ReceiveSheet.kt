/*
 * Copyright (c) 2026 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.solana.mwallet.MwalletApplication
import com.solana.mwallet.R
import com.solana.mwallet.ui.util.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.Theme_MwalletApp_BottomSheetDialog2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_receive, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as MwalletApplication
        val address = app.walletState.publicKey.value
        if (address.isNullOrBlank()) {
            dismiss(); return
        }

        view.findViewById<TextView>(R.id.text_receive_subtitle).text = getString(
            R.string.label_receive_sheet_body
        )
        view.findViewById<TextView>(R.id.text_receive_pubkey).text = address

        val qrImage = view.findViewById<ImageView>(R.id.receive_qr)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { QrCode.bitmapFor(address, 600) }
            if (bmp != null) qrImage.setImageBitmap(bmp)
        }

        view.findViewById<AppCompatButton>(R.id.btn_receive_copy).setOnClickListener {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
                ClipData.newPlainText("Solana address", address)
            )
            Toast.makeText(requireContext(), R.string.label_copied, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<AppCompatButton>(R.id.btn_receive_share).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, address)
            }
            startActivity(Intent.createChooser(share, getString(R.string.label_share_address)))
        }
    }

    companion object {
        const val TAG = "ReceiveSheet"
    }
}
