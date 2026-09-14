package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.dao.AwardWatchDao
import com.travelbenefits.app.data.local.dao.TransferBonusDao
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import com.travelbenefits.app.data.local.entity.TransferBonusEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.anthropic.AnthropicTool
import com.travelbenefits.app.domain.model.AwardWatch
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.TransferBonus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of checking one award watch. */
data class WatchCheckResult(val watch: AwardWatch, val found: Boolean, val summary: String, val newlyFound: Boolean)

/**
 * Web-search research that runs on a schedule: award-availability watches
 * (the honest stand-in for Roame/Seats.aero alerts, since no program exposes
 * inventory to personal apps) and current bank transfer bonuses. Every
 * result is labelled as researched, not verified, and carries a check date.
 */
@Singleton
class ResearchRepository @Inject constructor(
    private val awardWatchDao: AwardWatchDao,
    private val transferBonusDao: TransferBonusDao,
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) {
    fun observeWatches(): Flow<List<AwardWatch>> = awardWatchDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeBonuses(): Flow<List<TransferBonus>> = transferBonusDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getBonuses(): List<TransferBonus> = transferBonusDao.getAll().map { it.toDomain() }

    suspend fun addWatch(title: String, program: LoyaltyProgram?, origin: String?, destination: String?, dateFrom: String?, dateTo: String?, notes: String?): Long =
        awardWatchDao.insert(
            AwardWatchEntity(
                title = title,
                program = program,
                origin = origin,
                destination = destination,
                dateFrom = dateFrom,
                dateTo = dateTo,
                notes = notes,
                active = true,
                createdAt = System.currentTimeMillis(),
                lastCheckedAt = null,
                lastResult = null,
                lastFound = false,
            ),
        )

    suspend fun setActive(id: Long, active: Boolean) {
        val existing = awardWatchDao.findById(id) ?: return
        awardWatchDao.update(existing.copy(active = active))
    }

    suspend fun deleteWatch(id: Long) = awardWatchDao.deleteById(id)

    /** Checks every active watch (up to [maxWatches]) and stores the outcome. */
    suspend fun checkWatches(maxWatches: Int = MAX_WATCHES_PER_RUN, onlyId: Long? = null): Result<List<WatchCheckResult>> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) return Result.failure(IllegalStateException("Add an Anthropic API key in Settings to check award watches."))
        val watches = if (onlyId != null) listOfNotNull(awardWatchDao.findById(onlyId)) else awardWatchDao.getActive().take(maxWatches)
        val results = mutableListOf<WatchCheckResult>()
        for (watch in watches) {
            val question = buildString {
                append("Award watch: ").append(watch.title).append('\n')
                watch.program?.let { append("Program: ").append(it.displayName).append('\n') }
                watch.origin?.let { append("Origin: ").append(it).append('\n') }
                watch.destination?.let { append("Destination / property: ").append(it).append('\n') }
                if (watch.dateFrom != null || watch.dateTo != null) append("Dates: ").append(watch.dateFrom ?: "?").append(" to ").append(watch.dateTo ?: "?").append('\n')
                watch.notes?.let { append("Notes: ").append(it).append('\n') }
                append("Today: ").append(LocalDate.now()).append('\n')
            }
            val text = runCatching {
                anthropicClient.sendAndGetFinalText(
                    apiKey = apiKey,
                    system = WATCH_SYSTEM_PROMPT,
                    userText = question,
                    maxTokens = 1500,
                    tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 5)),
                )
            }.getOrNull() ?: continue
            val parsed = runCatching { json.decodeFromString<WatchAnswer>(CardLookupRepository.stripCodeFences(text)) }.getOrNull()
                ?: WatchAnswer(found = false, summary = text.take(600))
            val summary = buildString {
                append(parsed.summary.trim())
                parsed.bestOption?.takeIf { it.isNotBlank() }?.let { append("\nBest option: ").append(it.trim()) }
                parsed.whereToConfirm?.takeIf { it.isNotBlank() }?.let { append("\nConfirm at: ").append(it.trim()) }
            }
            val newlyFound = parsed.found && !watch.lastFound
            awardWatchDao.update(watch.copy(lastCheckedAt = System.currentTimeMillis(), lastResult = summary, lastFound = parsed.found))
            results += WatchCheckResult(watch.toDomain(), parsed.found, summary, newlyFound)
        }
        return Result.success(results)
    }

    suspend fun bonusesAreStale(): Boolean {
        val last = transferBonusDao.lastCheckedAt() ?: return true
        return System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(BONUS_REFRESH_DAYS)
    }

    /** Researches currently running transfer bonuses for the bank currencies the app knows and replaces the stored list. */
    suspend fun refreshTransferBonuses(): Result<List<TransferBonus>> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) return Result.failure(IllegalStateException("Add an Anthropic API key in Settings to check transfer bonuses."))
        val currencies = TransferPartnerCatalog.partners.map { it.from }.distinct()
        val programs = TransferPartnerCatalog.partners.map { it.to }.distinct()
        val userText = buildString {
            append("Today is ").append(LocalDate.now()).append(".\n")
            append("Bank currencies: ").append(currencies.joinToString { "${it.name} (${it.displayName})" }).append('\n')
            append("Programs of interest: ").append(programs.joinToString { "${it.name} (${it.displayName})" }).append('\n')
        }
        return runCatching {
            val text = anthropicClient.sendAndGetFinalText(
                apiKey = apiKey,
                system = BONUS_SYSTEM_PROMPT,
                userText = userText,
                maxTokens = 2000,
                tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 6)),
            )
            val parsed = json.decodeFromString<BonusAnswer>(CardLookupRepository.stripCodeFences(text))
            val now = System.currentTimeMillis()
            val entities = parsed.bonuses.mapNotNull { b ->
                val from = RewardCurrency.entries.firstOrNull { it.name.equals(b.fromCurrency?.trim(), ignoreCase = true) } ?: return@mapNotNull null
                val to = LoyaltyProgram.entries.firstOrNull { it.name.equals(b.toProgram?.trim(), ignoreCase = true) } ?: return@mapNotNull null
                val percent = b.bonusPercent?.toInt()?.takeIf { it in 1..200 } ?: return@mapNotNull null
                TransferBonusEntity(
                    fromCurrency = from,
                    toProgram = to,
                    bonusPercent = percent,
                    endsEpochDay = b.endsOn?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() },
                    note = b.note,
                    checkedAt = now,
                )
            }
            transferBonusDao.clear()
            // Always keep at least a marker row so "last checked" is known even when nothing is running.
            transferBonusDao.insertAll(entities.ifEmpty { listOf(TransferBonusEntity(fromCurrency = RewardCurrency.GENERIC_POINTS, toProgram = LoyaltyProgram.MARRIOTT_BONVOY, bonusPercent = 0, endsEpochDay = null, note = "No bonuses found", checkedAt = now)) })
            entities.map { it.toDomain() }
        }
    }

    fun profile(program: LoyaltyProgram) = LoyaltyProgramCatalog.profileFor(program)

    private companion object {
        const val MAX_WATCHES_PER_RUN = 5
        const val BONUS_REFRESH_DAYS = 7L

        val WATCH_SYSTEM_PROMPT = """
            You research award availability for one saved "watch" (a flight route
            or hotel stay on given dates, paid with a specific loyalty program).
            Use web search to look for recent reports, program award calendars,
            saver/standard award pricing and any award-search aggregators covering
            it. You cannot query live inventory, so "found" means credible,
            recent evidence that awards are bookable for these dates at a
            reasonable rate - not a guarantee. Respond with ONLY JSON:
            {
              "found": boolean,
              "summary": 1-3 plain sentences on what you found and how recent it is,
              "bestOption": short description of the best-looking option and rough points cost, or null,
              "whereToConfirm": the exact site/search page to confirm live availability
            }
        """.trimIndent()

        val BONUS_SYSTEM_PROMPT = """
            You track credit-card points transfer bonuses. Using web search, find
            transfer bonuses that are CURRENTLY running (today's date is given)
            from the listed bank currencies to the listed loyalty programs. Only
            include a bonus if a credible, dated source shows it is live now;
            skip expired or rumoured ones. Respond with ONLY JSON:
            {"bonuses":[{"fromCurrency": one of the bank currency codes given,
                         "toProgram": one of the program codes given,
                         "bonusPercent": integer (e.g. 30 for a 30% bonus),
                         "endsOn": "YYYY-MM-DD" or null,
                         "note": short source/detail}]}
            If none are running, respond {"bonuses":[]}.
        """.trimIndent()
    }
}

@Serializable
private data class WatchAnswer(
    val found: Boolean = false,
    val summary: String = "",
    val bestOption: String? = null,
    val whereToConfirm: String? = null,
)

@Serializable
private data class BonusAnswer(val bonuses: List<BonusItem> = emptyList())

@Serializable
private data class BonusItem(
    val fromCurrency: String? = null,
    val toProgram: String? = null,
    val bonusPercent: Double? = null,
    val endsOn: String? = null,
    val note: String? = null,
)
