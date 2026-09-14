package com.consensus.app.data.remote

import com.consensus.app.domain.ChatTurn
import com.consensus.app.domain.Citation
import com.consensus.app.domain.LlmClient
import com.consensus.app.domain.LlmException
import com.consensus.app.domain.LlmRequest
import com.consensus.app.domain.LlmResult
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient

/**
 * Anthropic Messages API (POST /v1/messages) with the server-side web
 * search tool. Follows the same continuation pattern as the Travel Benefits
 * app's client: when a server tool pauses the turn (`stop_reason ==
 * "pause_turn"`), resend with the partial assistant content appended.
 */
class AnthropicClient(
    private val http: OkHttpClient,
    private val json: Json,
) : LlmClient {
    override val provider = ProviderId.ANTHROPIC

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResult {
        if (apiKey.isBlank()) throw LlmException("Claude: no API key configured")

        val messages = mutableListOf<JsonObject>()
        request.turns.forEach { turn ->
            messages += buildJsonObject {
                put("role", if (turn.role == ChatTurn.Role.USER) "user" else "assistant")
                put("content", turn.text)
            }
        }

        var totalUsage = TokenUsage()
        var resumes = 0
        while (true) {
            val body = buildJsonObject {
                put("model", request.model)
                put("max_tokens", request.maxOutputTokens)
                put("system", request.system)
                put("messages", JsonArray(messages))
                if (request.webSearch) {
                    putJsonArray("tools") {
                        addJsonObject {
                            put("type", WEB_SEARCH_TOOL_TYPE)
                            put("name", "web_search")
                            put("max_uses", 5)
                        }
                    }
                }
            }
            val resp = HttpSupport.postJson(
                client = http,
                json = json,
                url = "https://api.anthropic.com/v1/messages",
                headers = mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to "2023-06-01",
                    "content-type" to "application/json",
                ),
                body = body,
                providerName = "Claude",
            )
            resp.obj("usage")?.let {
                totalUsage += TokenUsage(it.int("input_tokens") ?: 0, it.int("output_tokens") ?: 0)
            }
            val content = resp.arr("content")
            if (resp.str("stop_reason") == "pause_turn" && resumes < 3) {
                messages += buildJsonObject {
                    put("role", "assistant")
                    put("content", JsonArray(content))
                }
                resumes++
                continue
            }
            val texts = StringBuilder()
            val citations = LinkedHashMap<String, Citation>()
            content.mapNotNull { it.asObj() }.forEach { block ->
                if (block.str("type") == "text") {
                    block.str("text")?.let { texts.append(it) }
                    block.arr("citations").mapNotNull { it.asObj() }.forEach { c ->
                        val url = c.str("url") ?: return@forEach
                        citations.putIfAbsent(url, Citation(c.str("title"), url))
                    }
                }
            }
            val text = texts.toString().trim()
            if (text.isEmpty()) {
                throw LlmException("Claude: empty response (stop_reason=${resp.str("stop_reason")})")
            }
            return LlmResult(text, citations.values.toList(), totalUsage)
        }
    }

    companion object {
        /** Same tool version the Travel Benefits app uses successfully. Update if Anthropic bumps it. */
        const val WEB_SEARCH_TOOL_TYPE = "web_search_20260209"
    }
}
