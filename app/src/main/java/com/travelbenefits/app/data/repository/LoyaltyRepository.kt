package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.PointsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoyaltyRepository @Inject constructor(
    private val dao: LoyaltyAccountDao,
    private val snapshotDao: PointsSnapshotDao,
) {
    fun observeAccounts(): Flow<List<LoyaltyAccount>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSnapshots(): Flow<List<PointsSnapshot>> = snapshotDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun historyFor(program: LoyaltyProgram, limit: Int = 30): List<PointsSnapshot> =
        snapshotDao.recentForProgram(program, limit).map { it.toDomain() }

    /**
     * Saves a manually entered/edited account. A manual edit is the user's
     * word, so it wins over whatever a scan found - but the scan-derived
     * fields it doesn't touch (expiry, source subject) are kept.
     */
    suspend fun upsertManual(
        program: LoyaltyProgram,
        membershipNumber: String?,
        tier: String?,
        pointsBalance: String?,
        qualifyingProgress: Int?,
        pointsExpireAt: Long?,
        memberName: String? = null,
        editingId: Long? = null,
    ) {
        val member = memberName?.trim()?.ifBlank { null }
        val existing = editingId?.let { id -> dao.getAll().firstOrNull { it.id == id } }
            ?: if (member == null) dao.findByProgram(program) else dao.findByProgramAndMember(program, member)
        val numeric = LoyaltyAccount.parsePoints(pointsBalance)
        val now = System.currentTimeMillis()
        dao.insert(
            LoyaltyAccountEntity(
                id = existing?.id ?: 0,
                program = program,
                membershipNumber = membershipNumber,
                tier = tier,
                pointsBalance = pointsBalance,
                source = LoyaltyAccountSource.MANUAL,
                sourceEmailSubject = existing?.sourceEmailSubject,
                lastUpdated = now,
                pointsNumeric = numeric,
                pointsExpireAt = pointsExpireAt ?: existing?.pointsExpireAt,
                qualifyingProgress = qualifyingProgress,
                lastActivityAt = now,
                memberName = member,
            ),
        )
        if (member == null && numeric != null && snapshotDao.latestForProgram(program)?.points != numeric) {
            snapshotDao.insert(PointsSnapshotEntity(program = program, points = numeric, tier = tier, recordedAt = now, source = LoyaltyAccountSource.MANUAL))
        }
    }

    /** Adjusts the owner's balance for a program by [delta] (transfer in, redemption out) and records a snapshot. */
    suspend fun adjustBalance(program: LoyaltyProgram, delta: Long, note: String) {
        val existing = dao.findByProgram(program)
        val now = System.currentTimeMillis()
        val current = existing?.pointsNumeric ?: 0L
        val next = (current + delta).coerceAtLeast(0)
        dao.insert(
            (existing ?: LoyaltyAccountEntity(program = program, membershipNumber = null, tier = null, pointsBalance = null, source = LoyaltyAccountSource.MANUAL, sourceEmailSubject = null, lastUpdated = now))
                .copy(pointsNumeric = next, pointsBalance = null, lastUpdated = now, lastActivityAt = now, sourceEmailSubject = note),
        )
        snapshotDao.insert(PointsSnapshotEntity(program = program, points = next, tier = existing?.tier, recordedAt = now, source = LoyaltyAccountSource.MANUAL))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
