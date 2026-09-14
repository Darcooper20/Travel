package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.entity.CardLookupCacheEntity
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.anthropic.AnthropicTool
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.CardLookupResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves benefit data for a card the user wants to add: the fast path is
 * the built-in [CardCatalog]; when a card isn't there, this falls back to a
 * live Anthropic web-search lookup, cached locally for [CACHE_TTL_MILLIS] so
 * you're not paying for (or waiting on) an API call every time.
 */
@Singleton
class CardLookupRepository @Inject constructor(
    private val cardLookupCacheDao: CardLookupCacheDao,
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) {
    fun searchCatalog(query: String): List<CardCatalogEntry> = CardCatalog.search(query)

    suspend fun lookupCustomCard(name: String, forceRefresh: Boolean = false): Result<CardLookupResult> {
        val normalized = WalletRepository.normalizeCardName(name)

        if (!forceRefresh) {
            cardLookupCacheDao.find(normalized)?.let { cached ->
                val fresh = System.currentTimeMillis() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS
                if (fresh) {
                    runCatching { json.decodeFromString<CardLookupResult>(cached.resultJson) }
                        .getOrNull()
                        ?.let { return Result.success(it) }
                }
            }
        }

        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Add an Anthropic API key in Settings to look up cards that aren't in the built-in catalog.",
                ),
            )
        }

        return try {
            val text = anthropicClient.sendAndGetFinalText(
                apiKey = apiKey,
                system = LOOKUP_SYSTEM_PROMPT,
                userText = "Credit card: $name",
                maxTokens = 2048,
                tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 5)),
            )
            val result = parseLookupJson(text)
            cardLookupCacheDao.upsert(
                CardLookupCacheEntity(
                    normalizedName = normalized,
                    resultJson = json.encodeToString(result),
                    fetchedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Web-search lookup of a rotating-category card's categories for a quarter. Not cached: it's a once-a-quarter call. */
    suspend fun lookupRotatingCategories(cardName: String, quarterKey: String): Result<RotatingLookup> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) return Result.failure(IllegalStateException("Add an Anthropic API key in Settings to look up this quarter's categories."))
        return runCatching {
            val text = anthropicClient.sendAndGetFinalText(
                apiKey = apiKey,
                system = ROTATING_SYSTEM_PROMPT,
                userText = "Card: $cardName\nQuarter: ${quarterKey.replace("-", " ")}",
                maxTokens = 800,
                tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 4)),
            )
            val parsed = json.decodeFromString<RotatingAnswer>(stripCodeFences(text))
            RotatingLookup(
                categories = parsed.categories.mapNotNull { name -> com.travelbenefits.app.domain.model.SpendingCategory.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }.distinct(),
                note = parsed.asWritten?.let { "Issuer lists: $it" + (parsed.note?.let { n -> " ($n)" } ?: "") } ?: parsed.note,
            )
        }
    }

    private fun parseLookupJson(rawText: String): CardLookupResult =
        json.decodeFromString(stripCodeFences(rawText))

    companion object {
        private val CACHE_TTL_MILLIS = TimeUnit.DAYS.toMillis(30)
        private val CODE_FENCE_REGEX = Regex("^```[a-zA-Z]*\\n|```\\s*$")

        fun stripCodeFences(raw: String): String = raw.trim().replace(CODE_FENCE_REGEX, "").trim()

        private val ROTATING_SYSTEM_PROMPT: String
            get() {
                val categories = com.travelbenefits.app.domain.model.SpendingCategory.entries.joinToString(", ") { "\"${it.name}\" (${it.label})" }
                return """
                    Using web search, find the bonus categories the named credit card
                    offers (or lets the cardholder choose) for the named calendar
                    quarter, as announced by the issuer. Map them onto this app's
                    categories: $categories. Respond with ONLY JSON:
                    {"categories": [app category names], "asWritten": "the issuer's own wording", "note": "caveats, e.g. activation deadline or spend cap"}
                    If the quarter's categories haven't been announced yet, return
                    {"categories": [], "asWritten": null, "note": "not announced yet"}.
                """.trimIndent()
            }

        private val LOOKUP_SYSTEM_PROMPT = """
            You are a credit card benefits researcher. Use web search to find
            current, accurate information about the credit card named in the
            user's message (assume it is a US-issued card unless stated
            otherwise). Respond with ONLY a single JSON object - no markdown
            code fences, no other text before or after it - matching exactly
            this shape:
            {
              "displayName": string,
              "issuer": string,
              "annualFeeUsd": number or null,
              "summary": string (2-3 sentences),
              "categoryHighlights": [{"categoryLabel": string, "multiplierDescription": string}],
              "credits": [{"label": string, "description": string}],
              "hotelBenefitSummaries": [string],
              "sourcesNote": string (briefly note how current this information is and that it should be verified with the issuer)
            }
            If you cannot find the card, still return valid JSON with your
            best understanding, and say so plainly in "summary" and "sourcesNote".
            Never fabricate specific numbers you did not find - use general
            language in "summary"/"sourcesNote" instead if you're unsure.
        """.trimIndent()
    }
}

data class RotatingLookup(val categories: List<com.travelbenefits.app.domain.model.SpendingCategory>, val note: String?)

@kotlinx.serialization.Serializable
private data class RotatingAnswer(
    val categories: List<String> = emptyList(),
    val asWritten: String? = null,
    val note: String? = null,
)
