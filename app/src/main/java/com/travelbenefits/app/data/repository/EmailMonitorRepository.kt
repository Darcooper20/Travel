package com.travelbenefits.app.data.repository

import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.dao.ProcessedEmailDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.entity.ProcessedEmailEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.gmail.GmailApi
import com.travelbenefits.app.data.remote.gmail.GmailTextExtractor
import com.travelbenefits.app.data.remote.gmail.headerValue
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.PromptGuard
import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What one sync run found - shown in the UI and used to decide whether to notify. */
data class SyncReport(
    val emailsRead: Int = 0,
    val skippedAlreadyProcessed: Int = 0,
    val accountsFound: Int = 0,
    val balanceChanges: Int = 0,
    val tierChanges: Int = 0,
    val newTrips: Int = 0,
    val expiryWarnings: Int = 0,
    val cardBalanceUpdates: Int = 0,
    /** Cards created from a statement email, awaiting confirmation. */
    val cardsAdded: Int = 0,
    val certificatesFound: Int = 0,
    val highlights: List<String> = emptyList(),
    /** Gmail searches that errored. Mail they would have matched was never seen, so "nothing found" would be a lie. */
    val searchFailures: Int = 0,
    /** Individual messages Gmail refused to hand over. Not added to the processed ledger, so they are retried. */
    val fetchFailures: Int = 0,
    /** Emails fetched but not understood (model call or JSON parse failed). Also retried, never silently dropped. */
    val unreadableEmails: Int = 0,
    /** First underlying error, shown so the user can act (expired key, no network, quota). */
    val failureReason: String? = null,
) {
    val notableCount: Int get() = accountsFound + balanceChanges + tierChanges + newTrips + expiryWarnings + cardBalanceUpdates + cardsAdded + certificatesFound

    /** True when some part of this run failed, so an empty result does NOT mean "there was nothing". */
    val isPartial: Boolean get() = searchFailures > 0 || fetchFailures > 0 || unreadableEmails > 0

    /** True when the run learned nothing at all AND something went wrong - the case that must never read as "all clear". */
    val isBlind: Boolean get() = emailsRead == 0 && isPartial

    private fun problemSuffix(): String {
        if (!isPartial) return ""
        val parts = listOfNotNull(
            searchFailures.takeIf { it > 0 }?.let { "$it search(es) failed" },
            fetchFailures.takeIf { it > 0 }?.let { "$it email(s) couldn't be downloaded" },
            unreadableEmails.takeIf { it > 0 }?.let { "$it email(s) couldn't be read" },
        ).joinToString(", ")
        val why = failureReason?.let { " ($it)" }.orEmpty()
        return " $parts$why - nothing was skipped permanently, the next sync retries them."
    }

    fun summary(): String {
        val body = when {
            // Ordered so a failed run can never borrow the wording of a clean one.
            isBlind -> "Couldn't read any email this run, so nothing could be checked."
            emailsRead == 0 -> "No new travel or loyalty emails since the last sync."
            notableCount == 0 -> "Read $emailsRead email(s); nothing new to record."
            else -> listOfNotNull(
                accountsFound.takeIf { it > 0 }?.let { "$it membership(s) found" },
                balanceChanges.takeIf { it > 0 }?.let { "$it balance change(s)" },
                tierChanges.takeIf { it > 0 }?.let { "$it status change(s)" },
                newTrips.takeIf { it > 0 }?.let { "$it new trip(s)" },
                expiryWarnings.takeIf { it > 0 }?.let { "$it expiry warning(s)" },
                cardBalanceUpdates.takeIf { it > 0 }?.let { "$it card balance update(s)" },
                cardsAdded.takeIf { it > 0 }?.let { "$it new card(s) to confirm" },
                certificatesFound.takeIf { it > 0 }?.let { "$it certificate(s)" },
            ).joinToString(", ") + " from $emailsRead email(s)."
        }
        return body + problemSuffix()
    }
}

/**
 * The email monitor. Reads Gmail (read-only scope, never modifies anything)
 * incrementally - only mail newer than the last sync, minus anything in the
 * processed-email ledger - and uses Claude to turn loyalty statements,
 * booking confirmations and "your points are expiring" notices into
 * structured records: loyalty accounts, balance history, trips and an
 * activity feed. Runs from the Home screen's "Sync now" and from the
 * background [com.travelbenefits.app.work.EmailSyncWorker].
 *
 * Cost/privacy guardrails: at most [SyncSettings.maxEmailsPerSync] emails
 * per run, each truncated to a few thousand characters, sent only to
 * api.anthropic.com with the user's own key. Membership numbers are stored
 * locally only.
 */
@Singleton
class EmailMonitorRepository @Inject constructor(
    private val gmailApi: GmailApi,
    private val gmailAuthManager: GmailAuthManager,
    private val loyaltyAccountDao: LoyaltyAccountDao,
    private val pointsSnapshotDao: PointsSnapshotDao,
    private val tripDao: TripDao,
    private val activityEventDao: ActivityEventDao,
    private val processedEmailDao: ProcessedEmailDao,
    private val walletRepository: WalletRepository,
    private val benefitsRepository: BenefitsRepository,
    private val tripRepository: TripRepository,
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
    private val appPrefs: AppPrefs,
    private val json: Json,
) {
    @Volatile
    private var isRunning = false

    suspend fun sync(onProgress: suspend (String) -> Unit = {}): Result<SyncReport> {
        if (isRunning) return Result.failure(IllegalStateException("A sync is already running."))
        isRunning = true
        try {
            val apiKey = securePrefs.anthropicApiKey
            if (apiKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Add an Anthropic API key in Settings first - it's used to read the details out of your emails."))
            }
            if (!gmailAuthManager.isSignedIn.value) {
                return Result.failure(IllegalStateException("Connect Gmail in Settings first."))
            }
            val settings = appPrefs.syncSettings.value

            return try {
                val bearer = "Bearer ${gmailAuthManager.getFreshAccessToken()}"
                ensureAccountEmail(bearer)

                val now = System.currentTimeMillis()
                val lastSync = appPrefs.lastSyncAt.value
                // Overlap the previous window by a day; the processed-email ledger makes re-reads free.
                val afterEpochSeconds = if (lastSync > 0) {
                    (lastSync - TimeUnit.DAYS.toMillis(1)) / 1000
                } else {
                    (now - TimeUnit.DAYS.toMillis(settings.firstScanLookbackDays.toLong())) / 1000
                }

                onProgress("Looking for new loyalty, travel, shopping and card emails…")
                // Failures are counted, never swallowed: an empty result set from a failed
                // search is indistinguishable from a genuinely empty inbox unless we say so.
                var searchFailures = 0
                var fetchFailures = 0
                var firstFailure: String? = null
                fun noteFailure(t: Throwable) { if (firstFailure == null) firstFailure = describeError(t) }
                // Insertion order = priority under the per-sync cap: bookings first
                // (time-sensitive), then hotel/airline statements, then card
                // statements, then the noisier shop/dining senders.
                val refs = linkedMapOf<String, LoyaltyProgram?>()
                if (settings.scanTripEmails) {
                    val senders = TRIP_SENDER_DOMAINS.joinToString(" OR ") { "from:$it" }
                    val query = "($senders) $TRIP_SUBJECT_TERMS after:$afterEpochSeconds"
                    runCatching { gmailApi.listMessages(bearer, query, maxResults = MESSAGES_FOR_TRIPS) }
                        .onFailure { searchFailures++; noteFailure(it) }
                        .getOrNull()?.messages?.forEach { ref -> refs.putIfAbsent(ref.id, null) }
                    // Generic confirmations from anywhere else (small airlines, boutique hotels, rail).
                    val generic = "$TRIP_SUBJECT_TERMS -category:promotions after:$afterEpochSeconds"
                    runCatching { gmailApi.listMessages(bearer, generic, maxResults = MESSAGES_FOR_TRIPS) }
                        .onFailure { searchFailures++; noteFailure(it) }
                        .getOrNull()?.messages?.forEach { ref -> refs.putIfAbsent(ref.id, null) }
                }
                val programsToScan = LoyaltyProgram.entries.filter { program ->
                    when (program.kind) {
                        LoyaltyProgramKind.HOTEL, LoyaltyProgramKind.AIRLINE -> settings.scanLoyaltyEmails
                        LoyaltyProgramKind.SHOP -> false
                    }
                } + LoyaltyProgram.entries.filter { it.kind == LoyaltyProgramKind.SHOP && settings.scanShopEmails }
                val cardRefs = mutableListOf<String>()
                if (settings.scanCardEmails) {
                    val senders = CARD_ISSUER_DOMAINS.joinToString(" OR ") { "from:$it" }
                    val query = "($senders) $CARD_SUBJECT_TERMS -category:promotions after:$afterEpochSeconds"
                    runCatching { gmailApi.listMessages(bearer, query, maxResults = MESSAGES_FOR_CARDS) }
                        .onFailure { searchFailures++; noteFailure(it) }
                        .getOrNull()?.messages?.forEach { ref -> cardRefs += ref.id }
                }
                for (program in programsToScan) {
                    if (program.kind == LoyaltyProgramKind.SHOP && cardRefs.isNotEmpty()) {
                        // Card statements slot in ahead of the first shop program.
                        cardRefs.forEach { refs.putIfAbsent(it, null) }
                        cardRefs.clear()
                    }
                    // Shop senders are mostly marketing, so skip Gmail's Promotions bucket for them; hotel/airline
                    // statements sometimes land there too, so those are read regardless.
                    val isShop = program.kind == LoyaltyProgramKind.SHOP
                    val perProgram = if (isShop) MESSAGES_PER_SHOP else MESSAGES_PER_PROGRAM
                    val exclusions = if (isShop) " -category:promotions" else ""
                    val query = "(" + program.gmailSenderDomains.joinToString(" OR ") { "from:$it" } + ")$exclusions after:$afterEpochSeconds"
                    val list = runCatching { gmailApi.listMessages(bearer, query, maxResults = perProgram) }
                        .onFailure { searchFailures++; noteFailure(it) }
                        .getOrNull() ?: continue
                    list.messages.forEach { ref -> refs.putIfAbsent(ref.id, program) }
                }
                cardRefs.forEach { refs.putIfAbsent(it, null) }

                val already = if (refs.isEmpty()) emptySet() else processedEmailDao.findExisting(refs.keys.toList()).toSet()
                val toRead = refs.keys.filterNot { it in already }.take(settings.maxEmailsPerSync)
                if (toRead.isEmpty()) {
                    val report = SyncReport(
                        emailsRead = 0,
                        skippedAlreadyProcessed = already.size,
                        searchFailures = searchFailures,
                        failureReason = firstFailure,
                    )
                    finish(now, report)
                    return Result.success(report)
                }

                onProgress("Reading ${toRead.size} email(s)…")
                val candidates = mutableListOf<EmailCandidate>()
                for ((index, id) in toRead.withIndex()) {
                    if (index % 10 == 0 && index > 0) onProgress("Reading email ${index + 1} of ${toRead.size}…")
                    val detail = runCatching { gmailApi.getMessage(bearer, id) }
                        .onFailure { fetchFailures++; noteFailure(it) }
                        .getOrNull() ?: continue
                    candidates += EmailCandidate(
                        messageId = id,
                        programHint = refs[id],
                        subject = detail.payload?.headerValue("Subject").orEmpty(),
                        from = detail.payload?.headerValue("From").orEmpty(),
                        receivedAt = detail.internalDate?.toLongOrNull() ?: now,
                        body = GmailTextExtractor.extractBodyText(detail, maxChars = BODY_CHARS),
                    )
                }

                onProgress("Extracting balances, trips and alerts from ${candidates.size} email(s)…")
                val outcome = extract(apiKey, candidates)
                outcome.firstError?.let { noteFailure(IllegalStateException(it)) }

                // Only emails whose batch was read AND parsed enter the processed ledger.
                // Marking a failed batch processed would drop those emails forever, since
                // later syncs skip anything in the ledger.
                val understood = candidates.filter { it.messageId in outcome.succeededMessageIds }
                // apply() resolves the model's sourceIndex positionally against this exact list,
                // so it must stay the FULL candidate list; filtering it would shift every index
                // and attribute balances to the wrong email.
                val report = apply(outcome.result, candidates, now)
                processedEmailDao.insertAll(understood.map { ProcessedEmailEntity(it.messageId, now) })
                val full = report.copy(
                    emailsRead = understood.size,
                    skippedAlreadyProcessed = already.size,
                    searchFailures = searchFailures,
                    fetchFailures = fetchFailures,
                    unreadableEmails = candidates.size - understood.size,
                    failureReason = firstFailure,
                )
                finish(now, full)
                Result.success(full)
            } catch (e: Exception) {
                activityEventDao.insert(
                    ActivityEventEntity(
                        kind = ActivityKind.SYNC_FAILED,
                        program = null,
                        title = "Sync failed",
                        detail = e.message ?: e.javaClass.simpleName,
                        occurredAt = System.currentTimeMillis(),
                        isRead = false,
                    ),
                )
                Result.failure(e)
            }
        } finally {
            isRunning = false
        }
    }

    /** Wipes the processed-email ledger and sync watermark so the next sync re-reads the full lookback window. */
    suspend fun resetHistory() {
        processedEmailDao.clear()
        appPrefs.resetSyncWatermark()
    }

    private suspend fun ensureAccountEmail(bearer: String) {
        if (securePrefs.gmailAccountEmail != null) return
        runCatching { gmailApi.getProfile(bearer) }.getOrNull()?.emailAddress?.let { securePrefs.gmailAccountEmail = it }
    }

    private suspend fun finish(now: Long, report: SyncReport) {
        appPrefs.recordSync(now, report.summary(), hadFailures = report.isPartial)
        activityEventDao.insert(
            ActivityEventEntity(
                // A run that could not see the mailbox is a failure, not a quiet success.
                kind = if (report.isBlind) ActivityKind.SYNC_FAILED else ActivityKind.SYNC_COMPLETED,
                program = null,
                title = if (report.isBlind) "Sync couldn't read your mail" else "Email sync finished",
                detail = report.summary(),
                occurredAt = now,
                // Partial runs stay unread so the problem is visible even when nothing was found.
                isRead = report.notableCount == 0 && !report.isPartial,
            ),
        )
        activityEventDao.prune(olderThan = now - TimeUnit.DAYS.toMillis(180))
    }

    // ---- Extraction -------------------------------------------------------

    /** What [extract] managed to do, so the caller can tell "nothing there" from "couldn't look". */
    private data class ExtractionOutcome(
        val result: ExtractionResult,
        /** Message IDs whose batch was both fetched and parsed. Only these may be marked processed. */
        val succeededMessageIds: Set<String>,
        val firstError: String?,
    )

    private suspend fun extract(apiKey: String, candidates: List<EmailCandidate>): ExtractionOutcome {
        val merged = ExtractionResult()
        val loyalty = mutableListOf<ExtractedLoyalty>()
        val trips = mutableListOf<ExtractedTrip>()
        val alerts = mutableListOf<ExtractedAlert>()
        val cardRewards = mutableListOf<ExtractedCardRewards>()
        val succeeded = mutableSetOf<String>()
        var firstError: String? = null
        candidates.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            val offset = batchIndex * BATCH_SIZE
            val payload = batch.mapIndexed { i, c ->
                buildString {
                    append("Email ").append(offset + i).append(":\n")
                    append("Received: ").append(formatDate(c.receivedAt)).append('\n')
                    append("From: ").append(c.from).append('\n')
                    c.programHint?.let { append("Program hint: ").append(it.name).append('\n') }
                    append("Subject: ").append(PromptGuard.neutralise(c.subject)).append('\n')
                    append("Body:\n").append(PromptGuard.wrapUntrusted("email ${offset + i} from ${c.from}", c.body)).append('\n')
                }
            }.joinToString("\n---\n")

            val text = runCatching {
                anthropicClient.sendAndGetFinalText(
                    apiKey = apiKey,
                    system = PromptGuard.harden(EXTRACTION_SYSTEM_PROMPT),
                    userText = payload,
                    maxTokens = 4096,
                )
            }.onFailure { if (firstError == null) firstError = describeError(it) }
                .getOrNull() ?: return@forEachIndexed

            val parsed = runCatching {
                json.decodeFromString<ExtractionResult>(CardLookupRepository.stripCodeFences(text))
            }.onFailure { if (firstError == null) firstError = "the reply wasn't valid JSON" }
                .getOrNull() ?: return@forEachIndexed
            loyalty += parsed.loyaltyUpdates
            trips += parsed.trips
            alerts += parsed.alerts
            cardRewards += parsed.cardRewards
            batch.forEach { succeeded += it.messageId }
        }
        return ExtractionOutcome(
            merged.copy(loyaltyUpdates = loyalty, trips = trips, alerts = alerts, cardRewards = cardRewards),
            succeeded,
            firstError,
        )
    }

    /** Short, user-readable cause. Never includes the request body, headers or any key. */
    private fun describeError(t: Throwable): String {
        val message = t.message?.take(140)?.takeIf { it.isNotBlank() }
        return message ?: t.javaClass.simpleName
    }

    // ---- Applying results ------------------------------------------------

    private suspend fun apply(extraction: ExtractionResult, candidates: List<EmailCandidate>, now: Long): SyncReport {
        var certificatesFound = 0
        var cardBalanceUpdates = 0
        var cardsAdded = 0
        var accountsFound = 0
        var balanceChanges = 0
        var tierChanges = 0
        var newTrips = 0
        var expiryWarnings = 0
        val highlights = mutableListOf<String>()

        fun candidateFor(index: Int?): EmailCandidate? = index?.let { candidates.getOrNull(it) }

        // Oldest first so a stale statement never overwrites a newer one within the same batch.
        val loyaltySorted = extraction.loyaltyUpdates.sortedBy { candidateFor(it.sourceIndex)?.receivedAt ?: 0L }
        for (item in loyaltySorted) {
            val program = parseProgram(item.program) ?: continue
            val numeric = item.pointsNumeric?.toLong() ?: LoyaltyAccount.parsePoints(item.pointsBalance)
            if (item.membershipNumber == null && item.tier == null && numeric == null && item.pointsBalance == null && item.qualifyingProgress == null) continue

            val candidate = candidateFor(item.sourceIndex)
            val emailAt = candidate?.receivedAt ?: now
            val existing = loyaltyAccountDao.findByProgram(program)
            if (existing?.lastActivityAt != null && emailAt < existing.lastActivityAt) {
                // Older than what we already know - only fill gaps, never regress.
                if (existing.membershipNumber == null && item.membershipNumber != null) {
                    loyaltyAccountDao.insert(existing.copy(membershipNumber = item.membershipNumber))
                }
                continue
            }

            val tier = item.tier ?: existing?.tier
            val expireAt = parseDate(item.pointsExpireOn)?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } ?: existing?.pointsExpireAt
            val entity = LoyaltyAccountEntity(
                id = existing?.id ?: 0,
                program = program,
                membershipNumber = item.membershipNumber ?: existing?.membershipNumber,
                tier = tier,
                pointsBalance = item.pointsBalance ?: existing?.pointsBalance,
                source = LoyaltyAccountSource.GMAIL_SCAN,
                sourceEmailSubject = item.sourceSubject ?: candidate?.subject,
                lastUpdated = now,
                pointsNumeric = numeric ?: existing?.pointsNumeric,
                pointsExpireAt = expireAt,
                qualifyingProgress = item.qualifyingProgress?.toInt() ?: existing?.qualifyingProgress,
                lastActivityAt = emailAt,
            )
            loyaltyAccountDao.insert(entity)

            if (existing == null) {
                accountsFound++
                highlights += "${program.displayName} membership found"
                event(ActivityKind.ACCOUNT_FOUND, program, "${program.displayName} membership found", describe(entity), emailAt)
            }
            if (numeric != null) {
                val latest = pointsSnapshotDao.latestForProgram(program)
                if (latest == null || latest.points != numeric) {
                    pointsSnapshotDao.insert(PointsSnapshotEntity(program = program, points = numeric, tier = tier, recordedAt = emailAt, source = LoyaltyAccountSource.GMAIL_SCAN))
                    if (latest != null) {
                        balanceChanges++
                        val delta = numeric - latest.points
                        val sign = if (delta >= 0) "+" else "-"
                        val label = "${program.displayName}: ${LoyaltyAccount.formatPoints(numeric)} ($sign${LoyaltyAccount.formatPoints(kotlin.math.abs(delta))})"
                        highlights += label
                        event(ActivityKind.BALANCE_CHANGED, program, label, candidate?.subject, emailAt)
                    }
                }
            }
            if (existing != null && tier != null && existing.tier != null && !tier.equals(existing.tier, ignoreCase = true)) {
                tierChanges++
                highlights += "${program.displayName} status is now $tier"
                event(ActivityKind.TIER_CHANGED, program, "${program.displayName}: now $tier (was ${existing.tier})", candidate?.subject, emailAt)
            }
        }

        for (item in extraction.trips) {
            val candidate = candidateFor(item.sourceIndex)
            val provider = item.provider?.trim().orEmpty()
            if (provider.isBlank()) continue
            val confirmation = item.confirmationNumber?.trim()?.ifBlank { null }
            val duplicate = when {
                confirmation != null -> tripDao.findByConfirmation(confirmation, provider)
                candidate != null -> tripDao.findByMessageId(candidate.messageId)
                else -> null
            }
            val kind = runCatching { TripKind.valueOf(item.kind.orEmpty().uppercase()) }.getOrDefault(TripKind.OTHER)
            val program = parseProgram(item.loyaltyProgram) ?: LoyaltyProgramCatalog.programForProvider(provider) ?: LoyaltyProgramCatalog.programForProvider(item.title)
            val start = parseDate(item.startDate)?.toEpochDay()
            val end = parseDate(item.endDate)?.toEpochDay() ?: start
            val numberState = parseNumberState(item)
            val status = item.status?.uppercase()?.let { st -> com.travelbenefits.app.domain.model.TripStatus.entries.firstOrNull { it.name == st } } ?: com.travelbenefits.app.domain.model.TripStatus.CONFIRMED
            if (duplicate != null) {
                // Same booking again: only newer emails may change it (older ones fill gaps); cancellations and changes are recorded in history.
                val dupAt = candidate?.receivedAt ?: now
                val newer = dupAt >= duplicate.createdAt - 1
                val newState = if (numberState == com.travelbenefits.app.domain.model.LoyaltyNumberState.UNKNOWN) duplicate.loyaltyNumberState else numberState.name
                tripDao.update(
                    duplicate.copy(
                        startEpochDay = if (newer) start ?: duplicate.startEpochDay else duplicate.startEpochDay,
                        endEpochDay = if (newer) end ?: duplicate.endEpochDay else duplicate.endEpochDay,
                        loyaltyProgram = duplicate.loyaltyProgram ?: program,
                        loyaltyNumberState = newState,
                        loyaltyNumberOnBooking = newState == com.travelbenefits.app.domain.model.LoyaltyNumberState.CONFIRMED.name,
                        totalCost = item.totalCost ?: duplicate.totalCost,
                        pointsUsed = item.pointsUsed?.toLong() ?: duplicate.pointsUsed,
                        status = if (newer && status != com.travelbenefits.app.domain.model.TripStatus.CONFIRMED) status.name else duplicate.status,
                        departureTimeLocal = item.departureTimeLocal ?: duplicate.departureTimeLocal,
                        timeZoneId = item.timeZoneId ?: duplicate.timeZoneId,
                        cancellationTerms = item.cancellationTerms ?: duplicate.cancellationTerms,
                    ),
                )
                if (newer && status != com.travelbenefits.app.domain.model.TripStatus.CONFIRMED) {
                    tripRepository.addEvent(duplicate.id, if (status == com.travelbenefits.app.domain.model.TripStatus.CHANGED) com.travelbenefits.app.domain.model.TripEventKind.CHANGED else com.travelbenefits.app.domain.model.TripEventKind.CANCELLED, "From email: ${candidate?.subject ?: "update"}", "gmail", dupAt)
                    event(ActivityKind.TRIP_FOUND, program, "${duplicate.title}: ${status.label.lowercase()}", candidate?.subject, dupAt)
                }
                if (item.segments.isNotEmpty() && newer) {
                    tripRepository.replaceSegments(duplicate.id, item.segments.map { it.toDomain(duplicate.id, status) })
                }
                continue
            }
            val title = item.title?.trim()?.ifBlank { null } ?: buildTitle(kind, provider, item.origin, item.destination)
            val newTripId = tripDao.insert(
                TripEntity(
                    kind = kind,
                    provider = provider,
                    confirmationNumber = confirmation,
                    title = title,
                    startEpochDay = start,
                    endEpochDay = end,
                    origin = item.origin?.ifBlank { null },
                    destination = item.destination?.ifBlank { null },
                    loyaltyProgram = program,
                    loyaltyNumberOnBooking = numberState == com.travelbenefits.app.domain.model.LoyaltyNumberState.CONFIRMED,
                    loyaltyNumberState = numberState.name,
                    status = status.name,
                    departureTimeLocal = item.departureTimeLocal,
                    timeZoneId = item.timeZoneId,
                    cancellationTerms = item.cancellationTerms,
                    totalCost = item.totalCost?.ifBlank { null },
                    pointsUsed = item.pointsUsed?.toLong(),
                    pointsEarnedEstimate = item.pointsEarnedEstimate?.toLong(),
                    source = TripSource.GMAIL_SCAN,
                    sourceEmailSubject = item.sourceSubject ?: candidate?.subject,
                    sourceMessageId = candidate?.messageId,
                    notes = null,
                    createdAt = now,
                ),
            )
            tripRepository.addEvent(newTripId, if (status == com.travelbenefits.app.domain.model.TripStatus.CANCELLED) com.travelbenefits.app.domain.model.TripEventKind.CANCELLED else com.travelbenefits.app.domain.model.TripEventKind.CREATED, "From email: ${candidate?.subject ?: title}", "gmail", candidate?.receivedAt ?: now)
            if (item.segments.isNotEmpty()) tripRepository.replaceSegments(newTripId, item.segments.map { it.toDomain(newTripId, status) })
            newTrips++
            highlights += title
            event(ActivityKind.TRIP_FOUND, program, title + (if (status != com.travelbenefits.app.domain.model.TripStatus.CONFIRMED) " (${status.label.lowercase()})" else ""), listOfNotNull(item.startDate, confirmation?.let { "Conf. $it" }).joinToString(" • ").ifBlank { null }, candidate?.receivedAt ?: now)
        }

        for (item in extraction.alerts) {
            val program = parseProgram(item.program)
            val candidate = candidateFor(item.sourceIndex)
            val at = candidate?.receivedAt ?: now
            val kind = item.kind.orEmpty().uppercase()
            if (kind == "POINTS_EXPIRING" && program != null) {
                expiryWarnings++
                val date = parseDate(item.date)
                loyaltyAccountDao.findByProgram(program)?.let { account ->
                    if (date != null) {
                        loyaltyAccountDao.insert(account.copy(pointsExpireAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
                    }
                }
                highlights += "${program.displayName} points expiring"
                event(ActivityKind.POINTS_EXPIRING, program, "${program.displayName}: points expiring${date?.let { " on $it" }.orEmpty()}", item.message, at)
            } else if (kind == "CERTIFICATE" && item.message != null) {
                val benefitKind = when {
                    item.message.contains("night", ignoreCase = true) -> BenefitKind.FREE_NIGHT
                    item.message.contains("companion", ignoreCase = true) -> BenefitKind.COMPANION
                    item.message.contains("upgrade", ignoreCase = true) -> BenefitKind.UPGRADE
                    item.message.contains("lounge", ignoreCase = true) -> BenefitKind.LOUNGE_PASS
                    else -> BenefitKind.VOUCHER
                }
                val inserted = benefitsRepository.addFromEmailIfNew(benefitKind, item.message.trim(), program, parseDate(item.date)?.toEpochDay(), candidate?.subject)
                if (inserted) {
                    certificatesFound++
                    highlights += item.message.trim()
                    event(ActivityKind.INFO, program, "Certificate found: ${item.message.trim()}", parseDate(item.date)?.let { "Expires $it" }, at)
                }
            } else if (item.message != null) {
                event(ActivityKind.INFO, program, item.message, candidate?.subject, at)
            }
        }

        if (extraction.cardRewards.isNotEmpty()) {
            var wallet = walletRepository.getResolvedCards()
            val sortedCards = extraction.cardRewards.sortedBy { candidateFor(it.sourceIndex)?.receivedAt ?: 0L }
            for (item in sortedCards) {
                val balance = item.balance?.toLong() ?: LoyaltyAccount.parsePoints(item.balanceText) ?: continue
                val candidate = candidateFor(item.sourceIndex)
                val at = candidate?.receivedAt ?: now
                val card = matchCard(item, wallet)
                    // No card in the wallet is this one. A statement addressed to you is
                    // evidence you hold the account, so add it rather than dropping what
                    // was read - but only on strong evidence, and never as a confirmed card.
                    ?: addCardFromStatement(item, candidate, at)?.also { added ->
                        cardsAdded++
                        highlights += "${added.displayName}: added from a statement email, confirm it in Cards"
                        event(ActivityKind.INFO, null, "New card found: ${added.displayName}", candidate?.subject, at)
                        wallet = wallet + added
                    }
                    ?: continue
                item.newPurchasesUsd?.toLong()?.takeIf { it > 0 }?.let { purchases ->
                    if (walletRepository.addBonusSpend(card.walletCard.id, purchases, at)) {
                        event(ActivityKind.INFO, null, "${card.displayName}: +$${LoyaltyAccount.formatPoints(purchases)} toward the welcome bonus", candidate?.subject, at)
                    }
                }
                val previousAsOf = card.walletCard.rewardsBalanceAsOf
                if (previousAsOf != null && at < previousAsOf) continue
                val previous = walletRepository.updateRewardsBalance(card.walletCard.id, balance, at, item.last4?.takeLast(4))
                if (previous != balance) {
                    cardBalanceUpdates++
                    val currencyName = card.rewardCurrency?.displayName ?: "rewards"
                    val label = if (previous == null) {
                        "${card.displayName}: ${LoyaltyAccount.formatPoints(balance)} $currencyName"
                    } else {
                        val delta = balance - previous
                        "${card.displayName}: ${LoyaltyAccount.formatPoints(balance)} $currencyName (${if (delta >= 0) "+" else "-"}${LoyaltyAccount.formatPoints(kotlin.math.abs(delta))})"
                    }
                    highlights += label
                    event(ActivityKind.BALANCE_CHANGED, null, label, candidate?.subject, at)
                }
            }
        }

        return SyncReport(
            emailsRead = candidates.size,
            cardBalanceUpdates = cardBalanceUpdates,
            cardsAdded = cardsAdded,
            certificatesFound = certificatesFound,
            accountsFound = accountsFound,
            balanceChanges = balanceChanges,
            tierChanges = tierChanges,
            newTrips = newTrips,
            expiryWarnings = expiryWarnings,
            highlights = highlights,
        )
    }

    /**
     * Pairs a statement's card description with a wallet card: last-4 wins
     * outright; otherwise issuer + name words; otherwise the currency, if the
     * wallet holds exactly one card earning it (e.g. one Amex MR card).
     */
    /**
     * Adds a card found on a statement, or null when the evidence is too thin.
     *
     * The bar is deliberately high. A mention of a card proves nothing on its
     * own - inboxes are full of offers for cards nobody holds - so this needs
     * an issuer plus either the last four digits of a real account or a product
     * name that resolves to exactly one catalog entry. Anything the model
     * flagged as an advertisement is refused outright.
     *
     * The product is pinned to the catalog only when unmistakable. Otherwise
     * the card exists with no rate data and a name built from what is known,
     * because a guessed variant would feed wrong multipliers, caps and credits
     * into every recommendation.
     */
    private suspend fun addCardFromStatement(
        item: ExtractedCardRewards,
        candidate: EmailCandidate?,
        at: Long,
    ): ResolvedWalletCard? {
        if (item.isAdvertisement == true) return null
        val issuer = item.issuer?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val last4 = item.last4?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
        val catalogEntry = CardCatalog.findUnambiguous(issuer, item.cardName)
        if (last4 == null && catalogEntry == null) return null

        val displayName = catalogEntry?.displayName
            ?: listOfNotNull(issuer, item.cardName?.trim()?.takeIf { it.isNotBlank() }).joinToString(" ")
                .let { name -> if (last4 != null) "$name ••$last4" else name }
        val id = walletRepository.addCardFromEmail(
            catalogId = catalogEntry?.id,
            displayName = displayName,
            last4 = last4,
            sourceEmailSubject = candidate?.subject,
            at = at,
        )
        return walletRepository.getResolvedCards().firstOrNull { it.walletCard.id == id }
    }

    private fun matchCard(item: ExtractedCardRewards, wallet: List<ResolvedWalletCard>): ResolvedWalletCard? {
        val last4 = item.last4?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
        if (last4 != null) wallet.firstOrNull { it.walletCard.last4 == last4 }?.let { return it }

        val text = listOfNotNull(item.issuer, item.cardName).joinToString(" ").lowercase()
        val byName = wallet.filter { card ->
            val entry = (card as? ResolvedWalletCard.Catalog)?.entry
            val names = listOfNotNull(card.displayName, entry?.displayName, card.walletCard.customCardName, card.walletCard.nickname).map { it.lowercase() }
            val issuerOk = entry == null || text.contains(entry.issuer.lowercase().substringBefore(" "))
            issuerOk && names.any { name ->
                val words = name.split(' ', '-', '®').filter { it.length > 3 && it !in GENERIC_CARD_WORDS }
                words.isNotEmpty() && words.all { text.contains(it) }
            }
        }
        if (byName.size == 1) return byName.first()
        if (byName.size > 1 && last4 == null) return byName.firstOrNull { it.walletCard.last4 == null } ?: byName.first()

        val currency = item.currency?.let { c -> RewardCurrency.entries.firstOrNull { it.name.equals(c.trim(), ignoreCase = true) } }
        if (currency != null) {
            val byCurrency = wallet.filter { it.rewardCurrency == currency }
            if (byCurrency.size == 1) return byCurrency.first()
        }
        return null
    }

    private suspend fun event(kind: ActivityKind, program: LoyaltyProgram?, title: String, detail: String?, at: Long) {
        activityEventDao.insert(ActivityEventEntity(kind = kind, program = program, title = title, detail = detail, occurredAt = at, isRead = false))
    }

    private fun describe(e: LoyaltyAccountEntity): String = listOfNotNull(
        e.tier?.let { "Status: $it" },
        e.pointsNumeric?.let { LoyaltyAccount.formatPoints(it) + " points" } ?: e.pointsBalance,
        e.membershipNumber?.let { "#$it" },
    ).joinToString(" • ")

    private fun buildTitle(kind: TripKind, provider: String, origin: String?, destination: String?): String = when (kind) {
        TripKind.FLIGHT -> listOfNotNull(origin, destination).joinToString(" → ").ifBlank { provider }.let { "$provider: $it" }
        TripKind.HOTEL -> destination?.let { "$provider, $it" } ?: provider
        else -> listOfNotNull(provider, destination).joinToString(" - ")
    }

    private fun parseNumberState(item: ExtractedTrip): com.travelbenefits.app.domain.model.LoyaltyNumberState =
        item.loyaltyNumberState?.uppercase()?.let { st -> com.travelbenefits.app.domain.model.LoyaltyNumberState.entries.firstOrNull { it.name == st } }
            ?: if (item.loyaltyNumberOnBooking == true) com.travelbenefits.app.domain.model.LoyaltyNumberState.CONFIRMED else com.travelbenefits.app.domain.model.LoyaltyNumberState.UNKNOWN

    private fun ExtractedSegment.toDomain(tripId: Long, status: com.travelbenefits.app.domain.model.TripStatus) = com.travelbenefits.app.domain.model.TripSegment(
        0, tripId, 0, carrier, flightNumber, origin, destination, departLocal, arriveLocal, timeZoneId, cabin, status,
    )

    private fun parseProgram(name: String?): LoyaltyProgram? =
        name?.let { n -> LoyaltyProgram.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.trim().take(10), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

    private fun formatDate(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    private data class EmailCandidate(
        val messageId: String,
        val programHint: LoyaltyProgram?,
        val subject: String,
        val from: String,
        val receivedAt: Long,
        val body: String,
    )

    companion object {
        private const val MESSAGES_PER_PROGRAM = 10
        private const val MESSAGES_PER_SHOP = 4
        private const val MESSAGES_FOR_TRIPS = 25
        private const val MESSAGES_FOR_CARDS = 20

        private val GENERIC_CARD_WORDS = setOf("card", "credit", "visa", "mastercard", "american", "express", "rewards", "preferred", "world", "elite", "signature", "infinite", "cash", "back", "from", "with", "bank")

        /** Card issuer transactional domains, for "your statement is ready" / rewards summary emails. */
        private val CARD_ISSUER_DOMAINS = listOf(
            "chase.com", "americanexpress.com", "aexp.com", "capitalone.com", "citi.com", "citibank.com", "wellsfargo.com",
            "bankofamerica.com", "discover.com", "usbank.com", "barclaycardus.com", "barclays.com", "bilt.com", "biltrewards.com", "synchrony.com",
        )
        private const val CARD_SUBJECT_TERMS =
            "subject:(statement OR \"rewards summary\" OR \"points balance\" OR \"miles balance\" OR \"your rewards\" OR \"cash back\" OR \"Ultimate Rewards\" OR \"Membership Rewards\" OR \"ThankYou\")"
        private const val BATCH_SIZE = 6
        private const val BODY_CHARS = 3500

        /** Online travel agencies, corporate booking tools, car rental and rail senders whose mail is worth reading for bookings. */
        private val TRIP_SENDER_DOMAINS = listOf(
            "expedia.com", "booking.com", "hotels.com", "priceline.com", "kayak.com", "orbitz.com", "travelocity.com",
            "agoda.com", "trip.com", "hopper.com", "airbnb.com", "vrbo.com", "navan.com", "tripactions.com", "concur.com",
            "amextravel.com", "chase.com", "capitalone.com", "amtrak.com", "hertz.com", "avis.com", "enterprise.com",
            "nationalcar.com", "sixt.com", "budget.com", "turo.com", "google.com",
        )

        private const val TRIP_SUBJECT_TERMS =
            "subject:(confirmation OR confirmed OR itinerary OR reservation OR \"booking\" OR \"e-ticket\" OR eticket OR \"your trip\" OR \"your stay\" OR \"your upcoming\")"

        private val EXTRACTION_SYSTEM_PROMPT: String
            get() {
                val programNames = LoyaltyProgram.entries.joinToString(", ") { "\"${it.name}\"" }
                val currencyNames = RewardCurrency.entries.joinToString(", ") { "\"${it.name}\"" }
                return """
                    You extract structured travel-loyalty data from emails. You will be
                    given several emails, each labelled "Email N" with the received date,
                    sender, an optional program hint, the subject, and body text (messy,
                    HTML-stripped). Output ONLY one JSON object - no markdown fences, no
                    commentary - with exactly this shape:
                    {
                      "loyaltyUpdates": [
                        {
                          "sourceIndex": N (the Email number),
                          "program": one of $programNames,
                          "membershipNumber": string or null,
                          "tier": string or null (e.g. "Gold", "Platinum Elite", "Medallion Silver", "Premier 1K"),
                          "pointsBalance": string or null (as written, e.g. "42,500 points"),
                          "pointsNumeric": integer or null (the balance as a plain number),
                          "pointsExpireOn": "YYYY-MM-DD" or null (only if the email states an expiration date),
                          "qualifyingProgress": integer or null (year-to-date elite nights / MQDs / Loyalty Points / PQP if stated),
                          "sourceSubject": string
                        }
                      ],
                      "trips": [
                        {
                          "sourceIndex": N,
                          "kind": "FLIGHT" | "HOTEL" | "CAR" | "RAIL" | "OTHER",
                          "provider": string (airline, hotel brand/property, rental company, or OTA),
                          "confirmationNumber": string or null,
                          "title": short human title, e.g. "Delta SFO → JFK" or "Hyatt Regency Austin",
                          "startDate": "YYYY-MM-DD" or null,
                          "endDate": "YYYY-MM-DD" or null,
                          "origin": string or null,
                          "destination": string or null (city or property),
                          "loyaltyProgram": one of the program names above, or null,
                          "loyaltyNumberState": "CONFIRMED" if a frequent-flyer/loyalty number is shown attached to the booking, "MISSING" only if the email explicitly says none is attached or asks you to add one, else "UNKNOWN",
                          "status": "CONFIRMED" | "CHANGED" | "CANCELLED" | "PARTIALLY_CANCELLED" (cancellation and schedule-change emails MUST be included with the right status),
                          "departureTimeLocal": "HH:mm" local time of departure/check-in or null,
                          "timeZoneId": IANA zone of the departure airport/hotel (e.g. "America/New_York") or null if unsure,
                          "cancellationTerms": short quote of the cancellation/refund terms if stated, else null,
                          "segments": [ { "carrier": string, "flightNumber": string or null, "origin": string, "destination": string, "departLocal": "YYYY-MM-DDTHH:mm" or null, "arriveLocal": "YYYY-MM-DDTHH:mm" or null, "timeZoneId": string or null, "cabin": string or null } ] (flights only; empty for hotels),
                          "totalCost": string or null (as written, with currency),
                          "pointsUsed": integer or null (if paid with points),
                          "pointsEarnedEstimate": integer or null (only if the email states points to be earned),
                          "sourceSubject": string
                        }
                      ],
                      "alerts": [
                        {
                          "sourceIndex": N,
                          "kind": "POINTS_EXPIRING" | "TIER_CHANGE" | "CERTIFICATE" | "OTHER",
                          "program": program name or null,
                          "date": "YYYY-MM-DD" or null (for CERTIFICATE: its expiration date),
                          "message": one short sentence (for CERTIFICATE: the certificate's name, e.g. "Free Night Award up to 35,000 points" or "Companion Pass")
                        }
                      ],
                      "cardRewards": [
                        {
                          "sourceIndex": N,
                          "issuer": string (e.g. "Chase", "American Express", "Capital One", "Citi"),
                          "cardName": string or null (product name as written, e.g. "Sapphire Preferred", "Platinum Card"),
                          "last4": string or null (last four digits of the card if shown),
                          "currency": one of $currencyNames or null,
                          "balance": number or null (the current rewards balance: points, miles, or whole dollars of cash back),
                          "balanceText": string or null (as written, e.g. "84,210 points" or "$123.45 cash back"),
                          "newPurchasesUsd": number or null (this statement's new purchases/charges total, whole dollars, if the email states it),
                          "isAdvertisement": true if this email is promoting/offering the card rather than reporting on an account the reader already holds, false if it is a statement or rewards summary for their own account, null if unclear
                        }
                      ]
                    }
                    Program notes: the list includes shops, dining, grocery, rideshare and
                    cash-back programs. For those, "pointsBalance"/"pointsNumeric" may be
                    stars, points or fuel points; for dollar-denominated balances (gift
                    card, Walmart Cash, Uber Cash, ExtraBucks, Rakuten cash back, DoorDash
                    credits) put the whole-dollar amount in pointsNumeric and the text in
                    pointsBalance. Credit-card statement or rewards-summary emails go in
                    cardRewards, not loyaltyUpdates. Set "isAdvertisement" honestly: an
                    offer, pre-approval, or "apply now" email is an advertisement even when
                    it quotes a rewards rate, while a statement, payment notice or rewards
                    summary for an existing account is not. The app uses this to decide
                    whether the reader actually holds the card, so guessing costs them.
                    Rules: include an email in loyaltyUpdates only if it shows account-specific
                    details (a member number, tier/status, or a points/miles balance). Include a
                    trip for any booking/confirmation/itinerary, schedule change or cancellation -
                    not marketing, price alerts, or searches. Cancellations are important: report
                    them with status CANCELLED and the same confirmation number. Never invent
                    numbers or dates that aren't in the text; use null. A single email can
                    contribute to more than one list. If nothing qualifies, return
                    {"loyaltyUpdates":[],"trips":[],"alerts":[],"cardRewards":[]}.
                """.trimIndent()
            }
    }
}

@Serializable
private data class ExtractionResult(
    val loyaltyUpdates: List<ExtractedLoyalty> = emptyList(),
    val trips: List<ExtractedTrip> = emptyList(),
    val alerts: List<ExtractedAlert> = emptyList(),
    val cardRewards: List<ExtractedCardRewards> = emptyList(),
)

@Serializable
private data class ExtractedCardRewards(
    val sourceIndex: Int? = null,
    val issuer: String? = null,
    val cardName: String? = null,
    val last4: String? = null,
    val currency: String? = null,
    val balance: Double? = null,
    val balanceText: String? = null,
    val newPurchasesUsd: Double? = null,
    /** True when the email is selling the card rather than reporting on one the reader holds. */
    val isAdvertisement: Boolean? = null,
)

@Serializable
private data class ExtractedLoyalty(
    val sourceIndex: Int? = null,
    val program: String? = null,
    val membershipNumber: String? = null,
    val tier: String? = null,
    val pointsBalance: String? = null,
    val pointsNumeric: Double? = null,
    val pointsExpireOn: String? = null,
    val qualifyingProgress: Double? = null,
    val sourceSubject: String? = null,
)

@Serializable
private data class ExtractedTrip(
    val sourceIndex: Int? = null,
    val kind: String? = null,
    val provider: String? = null,
    val confirmationNumber: String? = null,
    val title: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val loyaltyProgram: String? = null,
    val loyaltyNumberOnBooking: Boolean? = null,
    val loyaltyNumberState: String? = null,
    val status: String? = null,
    val departureTimeLocal: String? = null,
    val timeZoneId: String? = null,
    val cancellationTerms: String? = null,
    val segments: List<ExtractedSegment> = emptyList(),
    val totalCost: String? = null,
    val pointsUsed: Double? = null,
    val pointsEarnedEstimate: Double? = null,
    val sourceSubject: String? = null,
)

@Serializable
private data class ExtractedSegment(
    val carrier: String? = null,
    val flightNumber: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val departLocal: String? = null,
    val arriveLocal: String? = null,
    val timeZoneId: String? = null,
    val cabin: String? = null,
)

@Serializable
private data class ExtractedAlert(
    val sourceIndex: Int? = null,
    val kind: String? = null,
    val program: String? = null,
    val date: String? = null,
    val message: String? = null,
)
