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
import com.solana.mwallet.MobileWalletAdapterViewModel
import com.solana.mwallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mwallet.R
import com.solana.mwallet.databinding.FragmentSignMessageBinding
import com.solana.mwallet.extensions.loadImage
import kotlinx.coroutines.launch

class SignPayloadFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignMessageBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignMessageBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.SignMessages -> {

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
                            viewBinding.textSubtitle.setText(R.string.label_sign_messages)
                            viewBinding.textMessage.text = request.request.payloads.first().decodeToString()

                            viewBinding.btnApprove.setOnClickListener {
                                activityViewModel.signPayloadsSimulateSign(request)
                            }

                            viewBinding.btnCancel.setOnClickListener {
                                activityViewModel.signPayloadsDeclined(request)
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