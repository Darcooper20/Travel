package com.travelbenefits.app.data.remote.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deliberately loose DTOs for the Anthropic Messages API (POST /v1/messages).
 *
 * This app talks to the API over plain Retrofit/OkHttp rather than the
 * official Java SDK: the Java SDK isn't published with Android in mind (its
 * dependency footprint and JVM assumptions aren't verified here), and for
 * the two calls this app makes - a web-search benefit lookup and a JSON
 * extraction pass - a couple of DTOs are simpler and lighter than pulling
 * in a general-purpose JVM SDK. `content` blocks are kept as raw
 * [JsonObject]s rather than a strict sealed hierarchy so new/unrecognized
 * block types (the API evolves) don't crash parsing - callers pull out the
 * `type`/`text` fields they need.
 */
@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    val tools: List<AnthropicTool>? = null,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: JsonElement,
) {
    companion object {
        fun userText(text: String) = AnthropicMessage("user", JsonPrimitive(text))
    }
}

@Serializable
data class AnthropicTool(
    val type: String,
    val name: String,
    @SerialName("max_uses") val maxUses: Int? = null,
)

@Serializable
data class AnthropicResponse(
    val id: String? = null,
    val role: String? = null,
    val content: List<JsonObject> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
data class AnthropicErrorEnvelope(
    val type: String? = null,
    val error: AnthropicErrorBody? = null,
)

@Serializable
data class AnthropicErrorBody(
    val type: String? = null,
    val message: String? = null,
)
