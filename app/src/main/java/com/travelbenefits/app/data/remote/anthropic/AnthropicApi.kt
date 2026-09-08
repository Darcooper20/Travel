package com.travelbenefits.app.data.remote.anthropic

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicApi {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") anthropicVersion: String = ANTHROPIC_API_VERSION,
        @Body request: AnthropicRequest,
    ): AnthropicResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_API_VERSION = "2023-06-01"

        /**
         * Per Anthropic's own guidance, always use the flagship model unless
         * the person using this app deliberately wants to spend less - that's
         * their call to make (e.g. by editing this constant), not a default
         * this app should quietly downgrade.
         */
        const val DEFAULT_MODEL = "claude-opus-5"
    }
}
