package com.travelbenefits.app.data.remote.anthropic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

class AnthropicAuthException(message: String) : Exception(message)

/**
 * Thin wrapper around [AnthropicApi] that:
 *  - attaches the caller's API key,
 *  - resends the turn when a server tool (web search) pauses with
 *    `stop_reason == "pause_turn"` instead of finishing, per Anthropic's
 *    documented continuation pattern, and
 *  - extracts the concatenated `text` content blocks from the final
 *    response, since callers here only ever want the model's final text.
 */
@Singleton
class AnthropicClient @Inject constructor(
    private val api: AnthropicApi,
) {
    suspend fun sendAndGetFinalText(
        apiKey: String,
        system: String,
        userText: String,
        maxTokens: Int,
        tools: List<AnthropicTool>? = null,
        maxPauseResumes: Int = 3,
    ): String {
        if (apiKey.isBlank()) {
            throw AnthropicAuthException("No Anthropic API key configured. Add one in Settings.")
        }

        val messages = mutableListOf(AnthropicMessage.userText(userText))
        var attempt = 0
        while (true) {
            val response = api.createMessage(
                apiKey = apiKey,
                request = AnthropicRequest(
                    model = AnthropicApi.DEFAULT_MODEL,
                    maxTokens = maxTokens,
                    system = system,
                    messages = messages,
                    tools = tools,
                ),
            )

            if (response.stopReason == "pause_turn" && attempt < maxPauseResumes) {
                messages += AnthropicMessage("assistant", JsonArray(response.content))
                attempt++
                continue
            }

            return extractText(response.content)
        }
    }

    private fun extractText(blocks: List<JsonObject>): String =
        blocks
            .asSequence()
            .mapNotNull { block ->
                val type = (block["type"] as? JsonPrimitive)?.contentOrNull
                if (type == "text") (block["text"] as? JsonPrimitive)?.contentOrNull else null
            }
            .joinToString("\n")
}
