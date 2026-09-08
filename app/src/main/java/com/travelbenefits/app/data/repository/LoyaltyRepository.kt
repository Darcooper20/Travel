package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoyaltyRepository @Inject constructor(
    private val dao: LoyaltyAccountDao,
) {
    fun observeAccounts(): Flow<List<LoyaltyAccount>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun upsertManual(program: LoyaltyProgram, membershipNumber: String?, tier: String?, pointsBalance: String?) {
        val existing = dao.findByProgram(program)
        dao.insert(
            LoyaltyAccountEntity(
                id = existing?.id ?: 0,
                program = program,
                membershipNumber = membershipNumber,
                tier = tier,
                pointsBalance = pointsBalance,
                source = LoyaltyAccountSource.MANUAL,
                sourceEmailSubject = null,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
