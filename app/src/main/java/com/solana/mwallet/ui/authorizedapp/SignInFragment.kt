/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet.ui.authorizedapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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

class SignInFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignMessageBinding

    private var request: MobileWalletAdapterServiceRequest.SignIn? = null

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
                        is MobileWalletAdapterServiceRequest.SignIn -> {
                            this@SignInFragment.request = request

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
                            viewBinding.textSubtitle.setText(R.string.label_sign_in_subtitle)
                            viewBinding.btnApprove.setText(R.string.label_sign_in)
                            // TODO: refactor address handling so we can prepare the message before user confirms
//                            viewBinding.textMessage.text = request.signInPayload.prepareMessage()
                            viewBinding.textMessage.text = "Sign in with your Solana Account: 1234wxyz"
                        }
                        else -> {
                            this@SignInFragment.request = null
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_sign_in) {
                                    nc.navigate(SignInFragmentDirections.actionSignInComplete())
                                }
                            }
                        }
                    }
                }
            }
        }

        viewBinding.btnApprove.setOnClickListener {
            request?.let {
                Log.i(TAG, "signing in")
                activityViewModel.signIn(it, true)
            }
        }

        viewBinding.btnCancel.setOnClickListener {
            request?.let {
                Log.w(TAG, "Rejecting sign in")
                activityViewModel.signIn(it, false)
            }
        }
    }

    companion object {
        private val TAG = AuthorizeDappFragment::class.simpleName
    }
}