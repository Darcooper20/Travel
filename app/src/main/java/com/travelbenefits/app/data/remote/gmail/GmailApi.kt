package com.travelbenefits.app.data.remote.gmail

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GmailApi {
    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 15,
    ): GmailListResponse

    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Query("format") format: String = "full",
    ): GmailMessageDetail

    companion object {
        const val BASE_URL = "https://gmail.googleapis.com/"

        /** Read-only scope - this app never sends, deletes, or modifies any email. */
        const val SCOPE_GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
