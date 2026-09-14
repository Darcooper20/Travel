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
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * Gemini API `models/{model}:generateContent` with Google Search grounding.
 *
 * Request: { systemInstruction: {parts:[{text}]}, contents: [{role: user|model, parts:[{text}]}],
 *            tools: [{google_search: {}}], generationConfig: {maxOutputTokens} }
 * Response: candidates[0].content.parts[].text, candidates[0].groundingMetadata.groundingChunks[].web{uri,title},
 *           usageMetadata{promptTokenCount, candidatesTokenCount}
 */
class GeminiClient(
    private val http: OkHttpClient,
    private val json: Json,
) : LlmClient {
    override val provider = ProviderId.GEMINI

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResult {
        if (apiKey.isBlank()) throw LlmException("Gemini: no API key configured")

        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", request.system) } }
            }
            putJsonArray("contents") {
                request.turns.forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.role == ChatTurn.Role.USER) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                    }
                }
            }
            if (request.webSearch) {
                putJsonArray("tools") {
                    addJsonObject { putJsonObject("google_search") { } }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", request.maxOutputTokens)
            }
        }

        val resp = HttpSupport.postJson(
            client = http,
            json = json,
            url = "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent",
            headers = mapOf(
                "x-goog-api-key" to apiKey,
                "Content-Type" to "application/json",
            ),
            body = body,
            providerName = "Gemini",
        )

        val candidate = resp.arr("candidates").firstOrNull()?.asObj()
            ?: throw LlmException(
                "Gemini: no candidates returned" +
                    (resp.obj("promptFeedback")?.str("blockReason")?.let { " (blocked: $it)" } ?: ""),
            )

        val texts = StringBuilder()
        candidate.obj("content")?.arr("parts")?.mapNotNull { it.asObj() }?.forEach { part ->
            if (part.bool("thought") == true) return@forEach
            part.str("text")?.let { texts.append(it) }
        }

        val citations = LinkedHashMap<String, Citation>()
        candidate.obj("groundingMetadata")?.arr("groundingChunks")?.mapNotNull { it.asObj() }?.forEach { chunk ->
            val web = chunk.obj("web") ?: return@forEach
            val uri = web.str("uri") ?: return@forEach
            citations.putIfAbsent(uri, Citation(web.str("title"), uri))
        }

        val usage = resp.obj("usageMetadata")?.let {
            TokenUsage(it.int("promptTokenCount") ?: 0, it.int("candidatesTokenCount") ?: 0)
        } ?: TokenUsage()

        val text = texts.toString().trim()
        if (text.isEmpty()) {
            throw LlmException("Gemini: empty response (finishReason=${candidate.str("finishReason")})")
        }
        return LlmResult(text, citations.values.toList(), usage)
    }
}
