package com.consensus.app.domain

import kotlinx.serialization.Serializable

/** One prior turn of conversation, provider-agnostic. */
data class ChatTurn(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

/** A single completion request to one provider. */
data class LlmRequest(
    val model: String,
    val system: String,
    val turns: List<ChatTurn>,
    val webSearch: Boolean,
    val maxOutputTokens: Int,
)

@Serializable
data class Citation(val title: String?, val url: String)

@Serializable
data class TokenUsage(val input: Int = 0, val output: Int = 0) {
    operator fun plus(other: TokenUsage) = TokenUsage(input + other.input, output + other.output)
}

data class LlmResult(
    val text: String,
    val citations: List<Citation>,
    val usage: TokenUsage,
)

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface LlmClient {
    val provider: ProviderId
    suspend fun complete(apiKey: String, request: LlmRequest): LlmResult
}
