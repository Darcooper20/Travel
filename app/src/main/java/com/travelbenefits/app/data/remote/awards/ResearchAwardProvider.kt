package com.travelbenefits.app.data.remote.awards

import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.anthropic.AnthropicTool
import com.travelbenefits.app.data.repository.CardLookupRepository
import com.travelbenefits.app.domain.model.AwardResult
import com.travelbenefits.app.domain.model.AwardSearchRequest
import com.travelbenefits.app.domain.model.AwardSearchResponse
import com.travelbenefits.app.domain.model.ResultKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** AI web research as an award "provider": every result is a RESEARCH_LEAD, never inventory. */
@Singleton
class ResearchAwardProvider @Inject constructor(
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) : AwardSearchProvider {
    override val id = "ai_research"
    override val displayName = "AI web research (leads only)"
    override val coverage = "Recent public reports, award charts and blog/forum sightings found by web search. Not inventory."

    override suspend fun isAvailable(): Boolean = !securePrefs.anthropicApiKey.isNullOrBlank()

    override suspend fun search(request: AwardSearchRequest): Result<AwardSearchResponse> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) return Result.failure(IllegalStateException("Add an Anthropic API key in Settings."))
        return runCatching {
            val text = anthropicClient.sendAndGetFinalText(
                apiKey = apiKey, system = SYSTEM, maxTokens = 1500,
                userText = "Route: ${request.origin} → ${request.destination}; dates ${request.dateFrom} to ${request.dateTo} (±${request.flexibleDays} days); cabin ${request.cabin.label}; passengers ${request.passengers}" +
                    (request.program?.let { "; program ${it.displayName}" } ?: "") + (request.maxConnections?.let { "; max connections $it" } ?: ""),
                tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 5)),
            )
            val parsed = runCatching { json.decodeFromString<Answer>(CardLookupRepository.stripCodeFences(text)) }.getOrElse { Answer(summary = text.take(500)) }
            val results = parsed.leads.map { l ->
                AwardResult(ResultKind.RESEARCH_LEAD, displayName, l.program, request.origin, request.destination, l.date ?: request.dateFrom, request.cabin, l.points, l.taxesUsd, null, l.direct, null, l.airline, l.url, l.reportedOn, l.note)
            }
            AwardSearchResponse(request, results, displayName, coverage, listOf("Leads from web research; confirm availability on the program's own search before transferring anything.", parsed.summary).filter { it.isNotBlank() }, System.currentTimeMillis())
        }
    }

    @Serializable
    private data class Lead(val program: String? = null, val date: String? = null, val points: Long? = null, val taxesUsd: Double? = null, val direct: Boolean? = null, val airline: String? = null, val url: String? = null, val reportedOn: String? = null, val note: String? = null)

    @Serializable
    private data class Answer(val leads: List<Lead> = emptyList(), val summary: String = "")

    private companion object {
        val SYSTEM = """
            You research award-flight availability leads with web search. Return ONLY JSON:
            {"leads":[{"program":..., "date":"YYYY-MM-DD" or null, "points": integer or null, "taxesUsd": number or null, "direct": bool or null, "airline":..., "url":..., "reportedOn":"YYYY-MM-DD" or null, "note": short}],
             "summary": 1-2 sentences on how current/reliable this is}
            Only include leads with a dated source. Never claim live availability.
        """.trimIndent()
    }
}
