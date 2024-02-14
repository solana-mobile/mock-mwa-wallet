package com.solana.mwallet.usecase

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

object UserAuthenticationUseCase {

    private val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Log in to mwallet")
        .setSubtitle("Log in securely access your accounts")
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
        .build()

    fun authenticate(fragment: Fragment, callback: (BiometricPrompt.AuthenticationResult?, Error?) -> Unit) {
        val executor = ContextCompat.getMainExecutor(fragment.requireContext())
        val biometricPrompt = BiometricPrompt(fragment, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int,
                                               errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.invoke(null, Error(errString.toString()))
            }

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback.invoke(result, null)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.invoke(null, null)
            }
        })
        biometricPrompt.authenticate(promptInfo)
    }

    fun authenticate(activity: FragmentActivity, callback: (BiometricPrompt.AuthenticationResult?, Error?) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity.applicationContext)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int,
                                               errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.invoke(null, Error(errString.toString()))
            }

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback.invoke(result, null)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.invoke(null, null)
            }
        })
        biometricPrompt.authenticate(promptInfo)
    }
}