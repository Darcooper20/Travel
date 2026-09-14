package com.travelbenefits.app.data.remote.plaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/** Client for the self-hosted worker in plaid-backend/. All URLs are built from the user's configured base. */
interface PlaidBackendApi {
    @POST
    suspend fun createLinkToken(@Url url: String, @Header("Authorization") auth: String): LinkTokenResponse

    @POST
    suspend fun exchange(@Url url: String, @Header("Authorization") auth: String, @Body body: ExchangeRequest): ExchangeResponse

    @GET
    suspend fun items(@Url url: String, @Header("Authorization") auth: String): ItemsResponse

    @POST
    suspend fun sync(@Url url: String, @Header("Authorization") auth: String, @Body body: SyncRequest): SyncResponse

    @DELETE
    suspend fun removeItem(@Url url: String, @Header("Authorization") auth: String): RemovedResponse
}

@Serializable
data class LinkTokenResponse(@SerialName("link_token") val linkToken: String)

@Serializable
data class ExchangeRequest(@SerialName("public_token") val publicToken: String, @SerialName("institution_name") val institutionName: String? = null)

@Serializable
data class BackendAccount(
    @SerialName("account_id") val accountId: String,
    val name: String? = null,
    @SerialName("official_name") val officialName: String? = null,
    val mask: String? = null,
    val type: String? = null,
    val subtype: String? = null,
)

@Serializable
data class ExchangeResponse(
    @SerialName("item_id") val itemId: String,
    @SerialName("institution_name") val institutionName: String? = null,
    val accounts: List<BackendAccount> = emptyList(),
)

@Serializable
data class ItemsResponse(val items: List<BackendItem> = emptyList())

@Serializable
data class BackendItem(@SerialName("item_id") val itemId: String, @SerialName("institution_name") val institutionName: String? = null, @SerialName("created_at") val createdAt: Long? = null)

@Serializable
data class SyncRequest(@SerialName("item_id") val itemId: String, val cursor: String? = null)

@Serializable
data class BackendTransaction(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("account_id") val accountId: String,
    val amount: Double = 0.0,
    val date: String? = null,
    val name: String? = null,
    @SerialName("merchant_name") val merchantName: String? = null,
    val pending: Boolean = false,
    @SerialName("pfc_primary") val pfcPrimary: String? = null,
    @SerialName("pfc_detailed") val pfcDetailed: String? = null,
)

@Serializable
data class SyncResponse(
    val added: List<BackendTransaction> = emptyList(),
    val modified: List<BackendTransaction> = emptyList(),
    val removed: List<String> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val accounts: List<BackendAccount> = emptyList(),
)

@Serializable
data class RemovedResponse(val removed: Boolean = false)
