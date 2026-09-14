package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.MemberDao
import com.travelbenefits.app.data.local.dao.MerchantOfferDao
import com.travelbenefits.app.data.local.entity.MemberEntity
import com.travelbenefits.app.data.local.entity.MerchantOfferEntity
import com.travelbenefits.app.domain.model.MerchantOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class HouseholdMember(val id: Long, val name: String, val isOwner: Boolean, val notes: String?)

@Singleton
class OfferRepository @Inject constructor(
    private val offerDao: MerchantOfferDao,
    private val memberDao: MemberDao,
) {
    fun observeOffers(): Flow<List<MerchantOffer>> = offerDao.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun addOffer(walletCardId: Long?, merchant: String, description: String, valueUsd: Double?, percentBack: Double?, minSpendUsd: Double?, expiresEpochDay: Long?, enrolled: Boolean, kind: MerchantOffer.Kind, notes: String?): Long =
        offerDao.insert(MerchantOfferEntity(walletCardId = walletCardId, merchant = merchant.trim(), description = description.trim(), valueUsd = valueUsd, percentBack = percentBack, minSpendUsd = minSpendUsd, expiresEpochDay = expiresEpochDay, enrolled = enrolled, kind = kind.name, source = "manual", notes = notes, createdAt = System.currentTimeMillis()))

    suspend fun setEnrolled(id: Long, enrolled: Boolean) {
        val e = offerDao.findById(id) ?: return
        offerDao.update(e.copy(enrolled = enrolled))
    }

    suspend fun deleteOffer(id: Long) = offerDao.deleteById(id)

    fun observeMembers(): Flow<List<HouseholdMember>> = memberDao.observeAll().map { l -> l.map { HouseholdMember(it.id, it.name, it.isOwner, it.notes) } }

    suspend fun addMember(name: String, notes: String?): Long = memberDao.insert(MemberEntity(name = name.trim(), isOwner = false, notes = notes))

    suspend fun deleteMember(id: Long) = memberDao.deleteById(id)

    private fun MerchantOfferEntity.toDomain() = MerchantOffer(id, walletCardId, merchant, description, valueUsd, percentBack, minSpendUsd, expiresEpochDay, enrolled, MerchantOffer.Kind.entries.firstOrNull { it.name == kind } ?: MerchantOffer.Kind.CARD_OFFER, source, notes)
}
