/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.signpayload

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.funkatronics.encoders.Base58
import com.solana.mwallet.MobileWalletAdapterViewModel
import com.solana.mwallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mwallet.R
import com.solana.mwallet.databinding.FragmentSignTransactionBinding
import com.solana.mwallet.extensions.loadImage
import com.solana.mwallet.usecase.ScanTransactionsUseCase
import kotlinx.coroutines.launch

class SignTransactionFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignTransactionBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignTransactionBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.SignTransactions -> {
                            when (request.txScanState) {
                                is ScanTransactionsUseCase.TransactionScanInProgress ->
                                    viewBinding.textSimulation.text = "scanning transaction..."
                                is ScanTransactionsUseCase.TransactionScanSucceeded ->
                                    viewBinding.textSimulation.text =
                                        request.txScanState.summary?.expectedStateChanges?.get(Base58.encodeToString(request.request.authorizedPublicKey))
                                            ?.fold("") { acc, next -> acc + next.humanReadableDiff + "\n"}?.trim() ?: "no expected state changes"
                                is ScanTransactionsUseCase.TransactionScanFailed,
                                is ScanTransactionsUseCase.NotScanable ->
                                    viewBinding.textSimulation.text = getString(R.string.label_sim_not_available)
                            }

                            if (request.request.identityUri?.isAbsolute == true &&
                                request.request.iconRelativeUri?.isHierarchical == true
                            ) {
                                val uri = Uri.withAppendedPath(
                                    request.request.identityUri!!,
                                    request.request.iconRelativeUri!!.encodedPath
                                )
                                viewBinding.imageIcon.loadImage(uri.toString())
                            }
                            viewBinding.textName.text = request.request.identityName ?: "<no name>"
                            viewBinding.textSubtitle.setText(R.string.label_requesting_transactions)

                            viewBinding.btnApprove.setOnClickListener {
                                activityViewModel.signPayloadsSimulateSign(request)
                            }

                            viewBinding.btnCancel.setOnClickListener {
                                activityViewModel.signPayloadsDeclined(request)
                            }
                        }
                        is MobileWalletAdapterServiceRequest.SignAndSendTransactions -> {
                            request.signatures?.run {
                                // When signatures are present, move on to sending the transaction
                                findNavController().navigate(SignTransactionFragmentDirections.actionSendTransaction())
                                return@collect
                            }

                            if (request.request.identityUri?.isAbsolute == true &&
                                request.request.iconRelativeUri?.isHierarchical == true
                            ) {
                                val uri = Uri.withAppendedPath(
                                    request.request.identityUri!!,
                                    request.request.iconRelativeUri!!.encodedPath
                                )
                                viewBinding.imageIcon.loadImage(uri.toString())
                            }
                            viewBinding.textName.text = request.request.identityName ?: "<no name>"
                            viewBinding.textSubtitle.setText(R.string.label_requesting_transactions)

                            viewBinding.btnApprove.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSign(request)
                            }

                            viewBinding.btnCancel.setOnClickListener {
                                activityViewModel.signAndSendTransactionsDeclined(request)
                            }
                        }
                        else -> {
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_sign_payload) {
                                    nc.navigate(SignPayloadFragmentDirections.actionSignPayloadComplete())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}