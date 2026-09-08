package com.travelbenefits.app.data.repository

import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.gmail.GmailApi
import com.travelbenefits.app.data.remote.gmail.GmailTextExtractor
import com.travelbenefits.app.data.remote.gmail.headerValue
import com.travelbenefits.app.domain.model.HotelProgram
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans Gmail (read-only) for emails from known hotel loyalty programs and
 * uses Claude to pull out membership number / tier / points balance, since
 * loyalty emails have no consistent format to regex against. Nothing is
 * ever sent, deleted, or modified in Gmail - only `gmail.readonly` reads.
 */
@Singleton
class GmailScanRepository @Inject constructor(
    private val gmailApi: GmailApi,
    private val gmailAuthManager: GmailAuthManager,
    private val loyaltyAccountDao: LoyaltyAccountDao,
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) {
    suspend fun scanForLoyaltyAccounts(onProgress: suspend (String) -> Unit = {}): Result<List<LoyaltyAccount>> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Add an Anthropic API key in Settings first - it's used to read the loyalty details out of your emails.",
                ),
            )
        }

        return try {
            val bearer = "Bearer ${gmailAuthManager.getFreshAccessToken()}"

            val candidates = mutableListOf<EmailCandidate>()
            for (program in HotelProgram.entries) {
                onProgress("Searching for ${program.displayName} emails…")
                val query = program.gmailSenderDomains.joinToString(" OR ") { "from:$it" }
                val listResponse = runCatching {
                    gmailApi.listMessages(bearer, query, maxResults = MESSAGES_PER_PROGRAM)
                }.getOrNull() ?: continue

                for (ref in listResponse.messages) {
                    val detail = runCatching { gmailApi.getMessage(bearer, ref.id) }.getOrNull() ?: continue
                    candidates += EmailCandidate(
                        program = program,
                        subject = detail.payload?.headerValue("Subject").orEmpty(),
                        body = GmailTextExtractor.extractBodyText(detail),
                    )
                }
            }

            if (candidates.isEmpty()) return Result.success(emptyList())

            onProgress("Reading ${candidates.size} email(s) for membership details…")
            val extracted = extractLoyaltyDetails(apiKey, candidates)

            val saved = mutableListOf<LoyaltyAccount>()
            for (item in extracted) {
                val program = runCatching { HotelProgram.valueOf(item.program) }.getOrNull() ?: continue
                if (item.membershipNumber == null && item.tier == null && item.pointsBalance == null) continue

                val existing = loyaltyAccountDao.findByProgram(program)
                val entity = LoyaltyAccountEntity(
                    id = existing?.id ?: 0,
                    program = program,
                    membershipNumber = item.membershipNumber ?: existing?.membershipNumber,
                    tier = item.tier ?: existing?.tier,
                    pointsBalance = item.pointsBalance ?: existing?.pointsBalance,
                    source = LoyaltyAccountSource.GMAIL_SCAN,
                    sourceEmailSubject = item.sourceSubject,
                    lastUpdated = System.currentTimeMillis(),
                )
                val id = loyaltyAccountDao.insert(entity)
                saved += entity.copy(id = if (entity.id != 0L) entity.id else id).toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractLoyaltyDetails(apiKey: String, candidates: List<EmailCandidate>): List<ExtractedLoyaltyItem> {
        val results = mutableListOf<ExtractedLoyaltyItem>()
        candidates.chunked(BATCH_SIZE).forEach { batch ->
            val payload = batch.mapIndexed { index, candidate ->
                buildString {
                    append("Email ").append(index).append(":\n")
                    append("Program hint: ").append(candidate.program.name).append('\n')
                    append("Subject: ").append(candidate.subject).append('\n')
                    append("Body: ").append(candidate.body.take(2500)).append('\n')
                }
            }.joinToString("\n---\n")

            val text = runCatching {
                anthropicClient.sendAndGetFinalText(
                    apiKey = apiKey,
                    system = EXTRACTION_SYSTEM_PROMPT,
                    userText = payload,
                    maxTokens = 2048,
                )
            }.getOrNull() ?: return@forEach

            val cleaned = CardLookupRepository.stripCodeFences(text)
            val parsed = runCatching { json.decodeFromString<List<ExtractedLoyaltyItem>>(cleaned) }.getOrNull()
            if (parsed != null) results += parsed
        }
        return results
    }

    private data class EmailCandidate(val program: HotelProgram, val subject: String, val body: String)

    companion object {
        private const val MESSAGES_PER_PROGRAM = 5
        private const val BATCH_SIZE = 8

        private val EXTRACTION_SYSTEM_PROMPT = """
            You extract hotel loyalty program membership details from emails.
            You will be given several emails, each labeled "Email N" with a
            program hint, subject, and body text (the body may be messy,
            HTML-stripped text). For EACH email that actually contains a
            membership/loyalty number, elite tier/status, or points balance,
            output one JSON object for it. Skip emails that are just
            marketing/promotional with no account-specific details - do not
            guess or invent numbers that are not actually present in the text.
            Respond with ONLY a JSON array (no markdown code fences, no other
            text), where each item matches exactly this shape:
            {
              "program": one of "MARRIOTT_BONVOY","HILTON_HONORS","WORLD_OF_HYATT","IHG_ONE_REWARDS","WYNDHAM_REWARDS","CHOICE_PRIVILEGES","ACCOR_LIVE_LIMITLESS","BEST_WESTERN_REWARDS","RADISSON_REWARDS",
              "membershipNumber": string or null,
              "tier": string or null (e.g. "Gold", "Platinum", "Diamond"),
              "pointsBalance": string or null (as written, e.g. "42,500 points"),
              "sourceSubject": string (copy the email's Subject line)
            }
            If nothing useful is found in any email, return an empty array: []
        """.trimIndent()
    }
}

@Serializable
private data class ExtractedLoyaltyItem(
    val program: String,
    val membershipNumber: String? = null,
    val tier: String? = null,
    val pointsBalance: String? = null,
    val sourceSubject: String? = null,
)
