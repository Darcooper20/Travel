package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.AwardWatchDao
import com.travelbenefits.app.data.local.dao.BenefitDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.dao.RotatingCategoryDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.CreditUsageEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.entity.RotatingCategoryEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export/import of everything the user has built up, since this app has no
 * cloud copy. JSON is the round-trippable format; CSV is a flat balance
 * sheet for spreadsheets. Secrets (API key, OAuth tokens) are never exported.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val walletCardDao: WalletCardDao,
    private val loyaltyAccountDao: LoyaltyAccountDao,
    private val pointsSnapshotDao: PointsSnapshotDao,
    private val tripDao: TripDao,
    private val benefitDao: BenefitDao,
    private val rotatingCategoryDao: RotatingCategoryDao,
    private val awardWatchDao: AwardWatchDao,
    private val json: Json,
) {
    suspend fun exportJson(): String {
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            walletCards = walletCardDao.getAll().map { it.toBackup() },
            loyaltyAccounts = loyaltyAccountDao.getAll().map { it.toBackup() },
            snapshots = pointsSnapshotDao.getAll().map { SnapshotBackup(it.program.name, it.points, it.tier, it.recordedAt, it.source.name) },
            trips = tripDao.getAll().map { it.toBackup() },
            benefits = benefitDao.getItems().map { it.toBackup() },
            creditUsage = benefitDao.getCreditUsage().map { CreditUsageBackup(it.walletCardId, it.creditLabel, it.lastUsedAt) },
            rotating = rotatingCategoryDao.getAll().map { RotatingBackup(it.walletCardId, it.quarterKey, it.categoriesCsv, it.activated) },
            watches = awardWatchDao.getAll().map { it.toBackup() },
        )
        return json.encodeToString(backup)
    }

    suspend fun exportBalancesCsv(): String {
        val rows = mutableListOf("type,name,member,balance,tier,membershipNumber,expiresOn,lastUpdated")
        loyaltyAccountDao.getAll().forEach { a ->
            rows += listOf(
                a.program.kind.name, a.program.displayName, a.memberName.orEmpty(), a.pointsNumeric?.toString() ?: a.pointsBalance.orEmpty(),
                a.tier.orEmpty(), a.membershipNumber.orEmpty(), a.pointsExpireAt?.let { date(it) }.orEmpty(), date(a.lastUpdated),
            ).joinToString(",") { csv(it) }
        }
        walletCardDao.getAll().forEach { c ->
            rows += listOf(
                "CARD", c.nickname ?: c.catalogCardId ?: c.customCardName.orEmpty(), c.memberName.orEmpty(), c.rewardsBalance?.toString().orEmpty(),
                "", c.last4?.let { "••••$it" }.orEmpty(), "", c.rewardsBalanceAsOf?.let { date(it) }.orEmpty(),
            ).joinToString(",") { csv(it) }
        }
        return rows.joinToString("\n")
    }

    /** Merges a JSON backup into the current data (existing rows are kept; matching accounts/cards are updated). Returns a summary. */
    suspend fun importJson(text: String): Result<String> = runCatching {
        val backup = json.decodeFromString<Backup>(text)
        var cards = 0
        var accounts = 0
        var trips = 0
        var benefits = 0
        val idMap = mutableMapOf<Long, Long>()
        val existingCards = walletCardDao.getAll()
        backup.walletCards.forEach { b ->
            val match = existingCards.firstOrNull { (b.catalogCardId != null && it.catalogCardId == b.catalogCardId && it.memberName == b.memberName) || (b.customCardName != null && it.customCardName == b.customCardName) }
            val entity = b.toEntity(match?.id ?: 0)
            val id = if (match != null) { walletCardDao.update(entity); match.id } else walletCardDao.insert(entity)
            idMap[b.id] = id
            cards++
        }
        backup.loyaltyAccounts.forEach { b ->
            val program = LoyaltyProgram.entries.firstOrNull { it.name == b.program } ?: return@forEach
            val match = if (b.memberName == null) loyaltyAccountDao.findByProgram(program) else loyaltyAccountDao.findByProgramAndMember(program, b.memberName)
            loyaltyAccountDao.insert(b.toEntity(program, match?.id ?: 0))
            accounts++
        }
        backup.snapshots.forEach { s ->
            val program = LoyaltyProgram.entries.firstOrNull { it.name == s.program } ?: return@forEach
            val latest = pointsSnapshotDao.latestForProgram(program)
            if (latest == null || latest.recordedAt < s.recordedAt) {
                pointsSnapshotDao.insert(PointsSnapshotEntity(program = program, points = s.points, tier = s.tier, recordedAt = s.recordedAt, source = source(s.source)))
            }
        }
        backup.trips.forEach { t ->
            val duplicate = t.confirmationNumber?.let { tripDao.findByConfirmation(it, t.provider) }
            if (duplicate == null) { tripDao.insert(t.toEntity()); trips++ }
        }
        backup.benefits.forEach { b ->
            val program = b.program?.let { p -> LoyaltyProgram.entries.firstOrNull { it.name == p } }
            if (benefitDao.findItemByTitle(b.title, program?.name) == null) { benefitDao.insertItem(b.toEntity(program, b.walletCardId?.let { idMap[it] })); benefits++ }
        }
        backup.creditUsage.forEach { u -> idMap[u.walletCardId]?.let { benefitDao.upsertCreditUsage(CreditUsageEntity(it, u.creditLabel, u.lastUsedAt)) } }
        backup.rotating.forEach { r -> idMap[r.walletCardId]?.let { rotatingCategoryDao.upsert(RotatingCategoryEntity(it, r.quarterKey, r.categoriesCsv, r.activated)) } }
        backup.watches.forEach { w -> awardWatchDao.insert(w.toEntity()) }
        "Imported $cards card(s), $accounts loyalty account(s), $trips new trip(s), $benefits certificate(s)."
    }

    private fun date(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    private fun source(name: String): LoyaltyAccountSource = runCatching { LoyaltyAccountSource.valueOf(name) }.getOrDefault(LoyaltyAccountSource.MANUAL)

    private fun WalletCardEntity.toBackup() = WalletCardBackup(id, nickname, catalogCardId, customCardName, dateAdded, notes, rewardsBalance, rewardsBalanceAsOf, last4, dateOpenedEpochDay, bonusSpendRequiredUsd, bonusDeadlineEpochDay, bonusSpendToDateUsd, bonusEarnedAt, memberName)
    private fun WalletCardBackup.toEntity(id: Long) = WalletCardEntity(id, nickname, catalogCardId, customCardName, dateAdded, notes, rewardsBalance, rewardsBalanceAsOf, last4, dateOpenedEpochDay, bonusSpendRequiredUsd, bonusDeadlineEpochDay, bonusSpendToDateUsd, bonusEarnedAt, memberName)
    private fun LoyaltyAccountEntity.toBackup() = LoyaltyAccountBackup(program.name, membershipNumber, tier, pointsBalance, source.name, sourceEmailSubject, lastUpdated, pointsNumeric, pointsExpireAt, qualifyingProgress, lastActivityAt, memberName)
    private fun LoyaltyAccountBackup.toEntity(program: LoyaltyProgram, id: Long) = LoyaltyAccountEntity(id, program, membershipNumber, tier, pointsBalance, source(source), sourceEmailSubject, lastUpdated, pointsNumeric, pointsExpireAt, qualifyingProgress, lastActivityAt, memberName)
    private fun TripEntity.toBackup() = TripBackup(kind.name, provider, confirmationNumber, title, startEpochDay, endEpochDay, origin, destination, loyaltyProgram?.name, loyaltyNumberOnBooking, totalCost, pointsUsed, pointsEarnedEstimate, source.name, sourceEmailSubject, sourceMessageId, notes, createdAt)
    private fun TripBackup.toEntity() = TripEntity(
        kind = runCatching { TripKind.valueOf(kind) }.getOrDefault(TripKind.OTHER), provider = provider, confirmationNumber = confirmationNumber, title = title,
        startEpochDay = startEpochDay, endEpochDay = endEpochDay, origin = origin, destination = destination,
        loyaltyProgram = loyaltyProgram?.let { p -> LoyaltyProgram.entries.firstOrNull { it.name == p } }, loyaltyNumberOnBooking = loyaltyNumberOnBooking,
        totalCost = totalCost, pointsUsed = pointsUsed, pointsEarnedEstimate = pointsEarnedEstimate,
        source = runCatching { TripSource.valueOf(source) }.getOrDefault(TripSource.MANUAL), sourceEmailSubject = sourceEmailSubject, sourceMessageId = sourceMessageId, notes = notes, createdAt = createdAt,
    )
    private fun BenefitItemEntity.toBackup() = BenefitBackup(kind.name, title, program?.name, walletCardId, valueUsd, expiresEpochDay, usedAt, notes, source.name, sourceEmailSubject, createdAt)
    private fun BenefitBackup.toEntity(program: LoyaltyProgram?, walletCardId: Long?) = BenefitItemEntity(
        kind = runCatching { BenefitKind.valueOf(kind) }.getOrDefault(BenefitKind.OTHER), title = title, program = program, walletCardId = walletCardId, valueUsd = valueUsd,
        expiresEpochDay = expiresEpochDay, usedAt = usedAt, notes = notes, source = source(source), sourceEmailSubject = sourceEmailSubject, createdAt = createdAt,
    )
    private fun AwardWatchEntity.toBackup() = WatchBackup(title, program?.name, origin, destination, dateFrom, dateTo, notes, active, createdAt)
    private fun WatchBackup.toEntity() = AwardWatchEntity(
        title = title, program = program?.let { p -> LoyaltyProgram.entries.firstOrNull { it.name == p } }, origin = origin, destination = destination,
        dateFrom = dateFrom, dateTo = dateTo, notes = notes, active = active, createdAt = createdAt, lastCheckedAt = null, lastResult = null, lastFound = false,
    )

    companion object {
        fun suggestedFileName(extension: String): String = "travel-benefits-backup-${LocalDate.now()}.$extension"
    }
}

@Serializable
private data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val walletCards: List<WalletCardBackup> = emptyList(),
    val loyaltyAccounts: List<LoyaltyAccountBackup> = emptyList(),
    val snapshots: List<SnapshotBackup> = emptyList(),
    val trips: List<TripBackup> = emptyList(),
    val benefits: List<BenefitBackup> = emptyList(),
    val creditUsage: List<CreditUsageBackup> = emptyList(),
    val rotating: List<RotatingBackup> = emptyList(),
    val watches: List<WatchBackup> = emptyList(),
)

@Serializable
private data class WalletCardBackup(
    val id: Long, val nickname: String?, val catalogCardId: String?, val customCardName: String?, val dateAdded: Long, val notes: String?,
    val rewardsBalance: Long? = null, val rewardsBalanceAsOf: Long? = null, val last4: String? = null, val dateOpenedEpochDay: Long? = null,
    val bonusSpendRequiredUsd: Long? = null, val bonusDeadlineEpochDay: Long? = null, val bonusSpendToDateUsd: Long? = null, val bonusEarnedAt: Long? = null, val memberName: String? = null,
)

@Serializable
private data class LoyaltyAccountBackup(
    val program: String, val membershipNumber: String?, val tier: String?, val pointsBalance: String?, val source: String, val sourceEmailSubject: String?,
    val lastUpdated: Long, val pointsNumeric: Long? = null, val pointsExpireAt: Long? = null, val qualifyingProgress: Int? = null, val lastActivityAt: Long? = null, val memberName: String? = null,
)

@Serializable
private data class SnapshotBackup(val program: String, val points: Long, val tier: String?, val recordedAt: Long, val source: String)

@Serializable
private data class TripBackup(
    val kind: String, val provider: String, val confirmationNumber: String?, val title: String, val startEpochDay: Long?, val endEpochDay: Long?,
    val origin: String?, val destination: String?, val loyaltyProgram: String?, val loyaltyNumberOnBooking: Boolean, val totalCost: String?,
    val pointsUsed: Long?, val pointsEarnedEstimate: Long?, val source: String, val sourceEmailSubject: String?, val sourceMessageId: String?, val notes: String?, val createdAt: Long,
)

@Serializable
private data class BenefitBackup(
    val kind: String, val title: String, val program: String?, val walletCardId: Long?, val valueUsd: Double?, val expiresEpochDay: Long?,
    val usedAt: Long?, val notes: String?, val source: String, val sourceEmailSubject: String?, val createdAt: Long,
)

@Serializable
private data class CreditUsageBackup(val walletCardId: Long, val creditLabel: String, val lastUsedAt: Long)

@Serializable
private data class RotatingBackup(val walletCardId: Long, val quarterKey: String, val categoriesCsv: String, val activated: Boolean)

@Serializable
private data class WatchBackup(
    val title: String, val program: String?, val origin: String?, val destination: String?, val dateFrom: String?, val dateTo: String?, val notes: String?, val active: Boolean, val createdAt: Long,
)
