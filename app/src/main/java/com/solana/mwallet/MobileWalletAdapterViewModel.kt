/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mwallet

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture
import com.solana.mwallet.usecase.*
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.nio.charset.StandardCharsets

class MobileWalletAdapterViewModel(application: Application) : AndroidViewModel(application) {
    private val _mobileWalletAdapterServiceEvents =
        MutableStateFlow<MobileWalletAdapterServiceRequest>(MobileWalletAdapterServiceRequest.None)
    val mobileWalletAdapterServiceEvents =
        _mobileWalletAdapterServiceEvents.asSharedFlow() // expose as event stream, rather than a stateful object

    private var clientTrustUseCase: ClientTrustUseCase? = null
    private var scenario: LocalScenario? = null

    private val walletIconUri = Uri.parse(application.getString(R.string.wallet_icon_uri))

    fun processLaunch(intent: Intent?, callingPackage: String?): Boolean {
        if (intent == null) {
            Log.e(TAG, "No Intent available")
            return false
        } else if (intent.data == null) {
            Log.e(TAG, "Intent has no data URI")
            return false
        }

        val associationUri = intent.data?.let { uri -> AssociationUri.parse(uri) }
        if (associationUri == null) {
            Log.e(TAG, "Unsupported association URI '${intent.data}'")
            return false
        } else if (associationUri !is LocalAssociationUri) {
            Log.w(TAG, "Current implementation of mwallet does not support remote clients")
            return false
        }

        clientTrustUseCase = ClientTrustUseCase(
            viewModelScope,
            getApplication<Application>().packageManager,
            callingPackage,
            associationUri
        )

        scenario = associationUri.createScenario(
            getApplication<MwalletApplication>().applicationContext,
            MobileWalletAdapterConfig(
                10,
                10,
                arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION, 0),
                LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
                arrayOf(
                    ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS,
                    ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA
                )
            ),
            AuthIssuerConfig("mwallet"),
            MobileWalletAdapterScenarioCallbacks()
        ).also { it.start() }

        return true
    }

    override fun onCleared() {
        scenario?.close()
        scenario = null
    }

    fun authorizeDapp(
        request: MobileWalletAdapterServiceRequest.AuthorizationRequest,
        authorized: Boolean
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            if (authorized) {
                val publicKey = getKeypairSafe().getOrElse {
                    request.request.completeWithDecline()
                    return@launch
                }.public as Ed25519PublicKeyParameters
                val account = buildAccount(publicKey.encoded, "mwallet",
                    chains = arrayOf(request.request.chain),
                    features = arrayOf(
                        ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS,
                        ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA
                    )
                )
                request.request.completeWithAuthorize(account,
                    // Android 12 and up require verified links, which we don't have
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) BuildConfig.WALLET_URI_BASE else null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray(), null)
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun signIn(
        request: MobileWalletAdapterServiceRequest.SignIn,
        authorizeSignIn: Boolean
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            if (authorizeSignIn) {
                val keypair = getKeypairSafe().getOrElse {
                    request.request.completeWithDecline()
                    return@launch
                }
                val publicKey = keypair.public as Ed25519PublicKeyParameters
                val address = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
                val siwsMessage = request.signInPayload.prepareMessage(address)
                val signResult = try {
                    val messageBytes = siwsMessage.encodeToByteArray()
                    SolanaSigningUseCase.signMessage(messageBytes, keypair)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "failed to sign SIWS payload", e)
                    request.request.completeWithInternalError(e)
                    return@launch
                }

                val signInResult = SignInResult(publicKey.encoded,
                    siwsMessage.encodeToByteArray(), signResult.signature, "ed25519")

                val account = buildAccount(publicKey.encoded, "mwallet")
                request.request.completeWithAuthorize(account, null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray(), signInResult)
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun signPayloadsSimulateSign(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            val keypair = getApplication<MwalletApplication>().keyRepository.getKeypair(request.request.authorizedPublicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val valid = BooleanArray(request.request.payloads.size) { true }
            val signedPayloads = when (request) {
                is MobileWalletAdapterServiceRequest.SignTransactions ->
                    Array(request.request.payloads.size) { i ->
                        try {
                            SolanaSigningUseCase.signTransaction(request.request.payloads[i], keypair).signedPayload
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                            valid[i] = false
                            byteArrayOf()
                        }
                    }
                is MobileWalletAdapterServiceRequest.SignMessages ->
                    Array(request.request.payloads.size) { i ->
                        SolanaSigningUseCase.signMessage(request.request.payloads[i], keypair).signedPayload
                    }
            }

            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.authorizedPublicKey}")
                request.request.completeWithSignedPayloads(signedPayloads)
            } else {
                Log.e(TAG, "One or more transactions not valid")
                request.request.completeWithInvalidPayloads(valid)
            }
        }
    }

    fun signPayloadsDeclined(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signAndSendTransactionsSign(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        viewModelScope.launch {
            val keypair = getApplication<MwalletApplication>().keyRepository.getKeypair(request.request.publicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val signingResults = request.request.payloads.map { payload ->
                try {
                    SolanaSigningUseCase.signTransaction(payload, keypair)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "not a valid Solana transaction", e)
                    SolanaSigningUseCase.SigningResult(byteArrayOf(), byteArrayOf())
                }
            }

            val valid = signingResults.map { result -> result.signature.isNotEmpty() }
            if (valid.all { it }) {
                Log.d(TAG, "Signing with ${request.request.publicKey}")
                val signatures = signingResults.map { result -> result.signature }
                val signedTransactions = signingResults.map { result -> result.signedPayload }
                val requestWithSignatures = request.copy(
                    signatures = signatures.toTypedArray(),
                    signedTransactions = signedTransactions.toTypedArray()
                )
                if (!updateExistingRequest(request, requestWithSignatures)) {
                    return@launch
                }
            } else {
                Log.e(TAG, "One or more transactions not valid")
                if (rejectStaleRequest(request)) {
                    return@launch
                }
                request.request.completeWithInvalidSignatures(valid.toBooleanArray())
            }
        }
    }

    fun signAndSendTransactionsDeclined(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signAndSendTransactionsSend(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        Log.d(TAG, "Sending transactions to ${request.endpointUri}")

        viewModelScope.launch(Dispatchers.IO) {
            request.signedTransactions!!
            request.signatures!!

            try {
                SendTransactionsUseCase(
                    request.endpointUri,
                    request.signedTransactions,
                    request.request.minContextSlot,
                    request.request.commitment,
                    request.request.skipPreflight,
                    request.request.maxRetries,
                    request.request.waitForCommitmentYoSendNExtTransaction
                )
                // TODO: await confirmation and update UI with progress
                Log.d(TAG, "All transactions submitted via RPC")
                if (rejectStaleRequest(request)) {
                    return@launch
                }
                request.request.completeWithSignatures(request.signatures)
            } catch (e: SendTransactionsUseCase.InvalidTransactionsException) {
                Log.e(TAG, "Failed submitting transactions via RPC", e)
                if (rejectStaleRequest(request)) {
                    return@launch
                }
                request.request.completeWithInvalidSignatures(e.valid)
            }
        }
    }

    private fun rejectStaleRequest(request: MobileWalletAdapterServiceRequest): Boolean {
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(
                request,
                MobileWalletAdapterServiceRequest.None
            )
        ) {
            Log.w(TAG, "Discarding stale request")
            if (request is MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest) {
                request.request.cancel()
            }
            return true
        }
        return false
    }

    private fun <T : MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest> updateExistingRequest(
        request: T,
        updated: T
    ): Boolean {
        require(request.request === updated.request) { "When updating a request, the same underlying ScenarioRequest is expected" }
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(request, updated)
        ) {
            Log.w(TAG, "Discarding stale request")
            request.request.cancel()
            return false
        }
        return true
    }

    private fun cancelAndReplaceRequest(request: MobileWalletAdapterServiceRequest) {
        val oldRequest = _mobileWalletAdapterServiceEvents.getAndUpdate { request }
        if (oldRequest is MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest) {
            oldRequest.request.cancel()
        }
    }

    private fun buildAccount(publicKey: ByteArray, label: String, icon: Uri? = walletIconUri,
                             chains: Array<String>? = null, features: Array<String>? = null) =
        AuthorizedAccount(
            publicKey, Base58.encodeToString(publicKey), "base58",
            label, icon, chains, features
        )

    private fun chainOrClusterToRpcUri(chainOrCluster: String?): Uri {
        return when (chainOrCluster) {
            ProtocolContract.CHAIN_SOLANA_MAINNET,
            ProtocolContract.CLUSTER_MAINNET_BETA ->
                Uri.parse("https://api.mainnet-beta.solana.com")
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            ProtocolContract.CLUSTER_DEVNET ->
                Uri.parse("https://api.devnet.solana.com")
            ProtocolContract.CHAIN_SOLANA_TESTNET,
            ProtocolContract.CLUSTER_TESTNET ->
                Uri.parse("https://api.testnet.solana.com")
            else -> throw IllegalArgumentException("Unsupported chain/cluster: $chainOrCluster")
        }
    }

    private suspend fun getKeypair(): AsymmetricCipherKeyPair {
        return BuildConfig.PRIVATE_KEY?.let { privateKey ->
            val privateKeyRaw = Base64.decode(privateKey, Base64.NO_PADDING or Base64.NO_WRAP)
            val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyRaw, 0)
            (getApplication<MwalletApplication>().keyRepository.getKeypair(privateKeyParams.generatePublicKey().encoded)
                ?: AsymmetricCipherKeyPair(
                    privateKeyParams.generatePublicKey(),
                    privateKeyParams
                ).also {
                    getApplication<MwalletApplication>().keyRepository.insertKeypair(it)
                }).also {
                val publicKey = it.public as Ed25519PublicKeyParameters
                val address = Base58.encodeToString(publicKey.encoded)
                Log.d(TAG, "Using local keypair (add=$address) for authorize request")
            }
        } ?: getApplication<MwalletApplication>().keyRepository.generateKeypair().also {
            val publicKey = it.public as Ed25519PublicKeyParameters
            val address = Base58.encodeToString(publicKey.encoded)
            Log.d(TAG, "Generated a new keypair (add=$address) for authorize request")
        }
    }

    private suspend fun getKeypairSafe(): Result<AsymmetricCipherKeyPair> =
        try {
            Result.success(getKeypair())
        } catch (e: UserNotAuthenticatedException) {
            val future = NotifyingCompletableFuture<BiometricPrompt.AuthenticationResult>()
            _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.UserAuthenticationRequest(future))
            future.runCatching {
                withTimeout(USER_AUTHENTICATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { get() }
                    getKeypair()
                }
            }
        }

    private suspend fun <T> doThingWithAuthentication(thing: suspend () -> T): Result<T> =
        try {
            Result.success(thing())
        } catch (e: UserNotAuthenticatedException) {
            val future = NotifyingCompletableFuture<BiometricPrompt.AuthenticationResult>()
            _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.UserAuthenticationRequest(future))
            future.runCatching {
                withTimeout(USER_AUTHENTICATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { get() }
                    thing()
                }
            }
        }

    private inner class MobileWalletAdapterScenarioCallbacks : LocalScenario.Callbacks {
        override fun onScenarioReady() = Unit
        override fun onScenarioServingClients() = Unit
        override fun onScenarioServingComplete() {
            viewModelScope.launch(Dispatchers.Main) {
                scenario?.close()
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.None)
            }
        }
        override fun onScenarioComplete() = Unit
        override fun onScenarioError() = Unit
        override fun onScenarioTeardownComplete() {
            viewModelScope.launch {
                // No need to cancel any outstanding request; the scenario is torn down, and so
                // cancelling a request that originated from it isn't actionable
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionTerminated)
            }
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            val clientTrustUseCase = clientTrustUseCase!! // should never be null if we get here

            val authorizationRequest = request.signInPayload?.let { signInPayload ->
                MobileWalletAdapterServiceRequest.SignIn(request, signInPayload,
                    clientTrustUseCase.verificationInProgress)
            } ?: MobileWalletAdapterServiceRequest.AuthorizeDapp(request,
                clientTrustUseCase.verificationInProgress)
            cancelAndReplaceRequest(authorizationRequest)

            val verify = clientTrustUseCase.verifyAuthorizationSourceAsync(request.identityUri)
            viewModelScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    verify.await()
                } ?: clientTrustUseCase.verificationTimedOut

                if (!updateExistingRequest(
                        authorizationRequest,
                        when (authorizationRequest) {
                            is MobileWalletAdapterServiceRequest.AuthorizeDapp ->
                                authorizationRequest.copy(sourceVerificationState = verificationState)
                            is MobileWalletAdapterServiceRequest.SignIn ->
                                authorizationRequest.copy(sourceVerificationState = verificationState)
                        }
                    )
                ) {
                    return@launch
                }
            }
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            val reverify = clientTrustUseCase!!.verifyReauthorizationSourceAsync(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
            viewModelScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    reverify.await()
                }
                when (verificationState) {
                    is ClientTrustUseCase.VerificationInProgress -> throw IllegalStateException()
                    is ClientTrustUseCase.VerificationSucceeded -> {
                        Log.i(TAG, "Reauthorization source verification succeeded")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.NotVerifiable -> {
                        Log.i(TAG, "Reauthorization source not verifiable; approving")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.VerificationFailed -> {
                        Log.w(TAG, "Reauthorization source verification failed")
                        request.completeWithDecline()
                    }
                    null -> {
                        Log.w(TAG, "Timed out waiting for reauthorization source verification")
                        request.completeWithDecline()
                    }
                }
            }
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignTransactions(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignMessages(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                val endpointUri = chainOrClusterToRpcUri(request.chain)
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignAndSendTransactions(request, endpointUri))
            } else {
                request.completeWithDecline()
            }
        }

        private fun verifyPrivilegedMethodSource(request: VerifiableIdentityRequest): Boolean {
            return clientTrustUseCase!!.verifyPrivilegedMethodSource(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            Log.d(TAG, "'${event.identityName}' deauthorized")
            event.complete()
        }

        override fun onLowPowerAndNoConnection() {
            Log.w(TAG, "Device is in power save mode and no connection was made. The connection was likely suppressed by power save mode.")
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.LowPowerNoConnection)
            }
        }
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest
        object LowPowerNoConnection : MobileWalletAdapterServiceRequest
        data class UserAuthenticationRequest(
            val future: NotifyingCompletableFuture<BiometricPrompt.AuthenticationResult>
        ) : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
        sealed class AuthorizationRequest(
            override val request: AuthorizeRequest,
            open val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : MobileWalletAdapterRemoteRequest(request)
        data class AuthorizeDapp(
            override val request: AuthorizeRequest,
            override val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : AuthorizationRequest(request, sourceVerificationState)
        data class SignIn(
            override val request: AuthorizeRequest,
            val signInPayload: SignInWithSolana.Payload,
            override val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : AuthorizationRequest(request, sourceVerificationState)
        sealed class SignPayloads(override val request: SignPayloadsRequest) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransactions(override val request: SignTransactionsRequest) : SignPayloads(request)
        data class SignMessages(override val request: SignMessagesRequest) : SignPayloads(request)
        data class SignAndSendTransactions(
            override val request: SignAndSendTransactionsRequest,
            val endpointUri: Uri,
            val signedTransactions: Array<ByteArray>? = null,
            val signatures: Array<ByteArray>? = null
        ) : MobileWalletAdapterRemoteRequest(request)
    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
        private const val SOURCE_VERIFICATION_TIMEOUT_MS = 3000L
        private const val LOW_POWER_NO_CONNECTION_TIMEOUT_MS = 3000L
        private const val USER_AUTHENTICATION_TIMEOUT_MS = 3000L
    }
}