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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient

/**
 * "Responses API" client, used for both OpenAI (api.openai.com) and xAI
 * (api.x.ai), which exposes the same request/response shape and the same
 * server-side `web_search` tool. Only the base URL, key and display name
 * differ.
 *
 * Request: { model, input: [{role, content}], tools: [{type: "web_search"}], max_output_tokens }
 * Response: output[] items; type "message" -> content[] of type "output_text"
 *           with `text` and `annotations[]` of type "url_citation".
 */
class OpenAiCompatibleClient(
    override val provider: ProviderId,
    private val displayName: String,
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val json: Json,
) : LlmClient {

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResult {
        if (apiKey.isBlank()) throw LlmException("$displayName: no API key configured")

        val body = buildJsonObject {
            put("model", request.model)
            put("max_output_tokens", request.maxOutputTokens)
            putJsonArray("input") {
                addJsonObject {
                    put("role", "system")
                    put("content", request.system)
                }
                request.turns.forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.role == ChatTurn.Role.USER) "user" else "assistant")
                        put("content", turn.text)
                    }
                }
            }
            if (request.webSearch) {
                putJsonArray("tools") {
                    addJsonObject { put("type", "web_search") }
                }
            }
        }

        val resp = HttpSupport.postJson(
            client = http,
            json = json,
            url = baseUrl.trimEnd('/') + "/v1/responses",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            ),
            body = body,
            providerName = displayName,
        )

        resp.obj("error")?.let { err ->
            throw LlmException("$displayName: ${err.str("message") ?: err.toString()}")
        }

        val texts = StringBuilder()
        val citations = LinkedHashMap<String, Citation>()
        resp.arr("output").mapNotNull { it.asObj() }.forEach { item ->
            if (item.str("type") != "message") return@forEach
            item.arr("content").mapNotNull { it.asObj() }.forEach { part ->
                if (part.str("type") == "output_text") {
                    part.str("text")?.let { texts.append(it) }
                    part.arr("annotations").mapNotNull { it.asObj() }.forEach { a ->
                        if (a.str("type") == "url_citation") {
                            val url = a.str("url") ?: return@forEach
                            citations.putIfAbsent(url, Citation(a.str("title"), url))
                        }
                    }
                }
            }
        }
        // Some implementations also expose a convenience top-level field.
        if (texts.isEmpty()) resp.str("output_text")?.let { texts.append(it) }

        val usage = resp.obj("usage")?.let {
            TokenUsage(it.int("input_tokens") ?: 0, it.int("output_tokens") ?: 0)
        } ?: TokenUsage()

        val text = texts.toString().trim()
        if (text.isEmpty()) {
            val status = resp.str("status")
            val incomplete = resp.obj("incomplete_details")?.str("reason")
            throw LlmException("$displayName: empty response (status=$status${incomplete?.let { ", $it" } ?: ""})")
        }
        return LlmResult(text, citations.values.toList(), usage)
    }
}
