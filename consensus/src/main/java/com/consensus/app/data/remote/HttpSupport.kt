package com.consensus.app.data.remote

import com.consensus.app.domain.LlmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Small helpers shared by the provider clients. All three APIs are plain
 * JSON-over-HTTPS, so this app uses OkHttp directly and builds/parses
 * JSON with kotlinx.serialization's tree API instead of typed DTOs. That
 * keeps the clients tolerant of fields the providers add or rename.
 */
internal object HttpSupport {
    private val jsonMedia = "application/json".toMediaType()

    suspend fun postJson(
        client: OkHttpClient,
        json: Json,
        url: String,
        headers: Map<String, String>,
        body: JsonObject,
        providerName: String,
    ): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw LlmException("$providerName: network error: ${e.message ?: e.javaClass.simpleName}", e)
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LlmException("$providerName: HTTP ${resp.code}: ${extractErrorMessage(json, text)}")
            }
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw LlmException("$providerName: unparseable response: ${text.take(300)}", e)
            }
        }
    }

    private fun extractErrorMessage(json: Json, body: String): String {
        if (body.isBlank()) return "(empty body)"
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val err = root["error"]
            when (err) {
                is JsonObject -> err.str("message") ?: err.toString()
                is JsonPrimitive -> err.content
                else -> root.str("message") ?: body.take(300)
            }
        } catch (e: Exception) {
            body.take(300)
        }
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()
internal fun JsonElement.asObj(): JsonObject? = this as? JsonObject
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
