package com.solana.mwallet.endpoints

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import java.lang.reflect.Type

interface BlowfishEndpoints {

    // https://api.blowfish.xyz/solana/v0/mainnet/scan/transactions
    @Headers("X-Api-Version: 2023-06-05")
    @POST("/solana/v0/{CLUSTER}/scan/transactions")
    suspend fun scanTransactions(
        @Path("CLUSTER") cluster: String,
        @Body request: ScanTransactionsRequest,
        @Header("X-Api-Key") token: String
    ): ScanTransactionsResponse
}

data class ScanTransactionsRequest(
    val transactions: List<String>,
    val userAccount: String,
    val origin: String
)

@Serializable
data class ScanTransactionsResponse(
    @SerialName("aggregated") val summary: ScanTransactionsSummary,
    val perTransaction: List<JsonElement>,
    val requestId: String
)
@Serializable
data class ScanTransactionsSummary(
    val action: String,
    val warnings: List<ScanTransactionsWarning>,
    val error: ScanTransactionsError? = null,
    val expectedStateChanges: Map<String, List<ScanTransactionsExpectedStateChange>>
)
@Serializable
data class ScanTransactionsWarning(val severity: String, val kind: String, val message: String)
@Serializable
data class ScanTransactionsError(val kind: String, @SerialName("humanReadableError") val message: String)
@Serializable
data class ScanTransactionsExpectedStateChange(val humanReadableDiff: String, val suggestedColor: String, val rawInfo: JsonElement)

object BlowfishConverterFactory : Converter.Factory() {

    private val json = Json { ignoreUnknownKeys = true }
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        return when (type) {
            ScanTransactionsRequest::class.java -> Converter<ScanTransactionsRequest, RequestBody> {
                buildJsonObject {
                    putJsonArray("transactions") { it.transactions.forEach { add(it) } }
                    put("userAccount", it.userAccount)
                    putJsonObject("metadata") {
                        put("origin", it.origin)
                    }
                }.toString().toRequestBody("application/json".toMediaType())
            }
            else -> super.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return when (type) {
            ScanTransactionsResponse::class.java -> Converter {
                json.decodeFromString(ScanTransactionsResponse.serializer(), it.string())
            }
            else -> super.responseBodyConverter(type, annotations, retrofit)
        }
    }
}