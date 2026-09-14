package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.local.toEntity
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val dao: TripDao,
) {
    fun observeTrips(): Flow<List<Trip>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addManual(
        kind: TripKind,
        provider: String,
        title: String,
        confirmationNumber: String?,
        startEpochDay: Long?,
        endEpochDay: Long?,
        destination: String?,
        loyaltyProgram: LoyaltyProgram?,
        loyaltyNumberOnBooking: Boolean,
        notes: String?,
    ): Long = dao.insert(
        TripEntity(
            kind = kind,
            provider = provider,
            confirmationNumber = confirmationNumber,
            title = title.ifBlank { provider },
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay ?: startEpochDay,
            origin = null,
            destination = destination,
            loyaltyProgram = loyaltyProgram ?: LoyaltyProgramCatalog.programForProvider(provider),
            loyaltyNumberOnBooking = loyaltyNumberOnBooking,
            totalCost = null,
            pointsUsed = null,
            pointsEarnedEstimate = null,
            source = TripSource.MANUAL,
            sourceEmailSubject = null,
            sourceMessageId = null,
            notes = notes,
            createdAt = System.currentTimeMillis(),
        ),
    )

    suspend fun update(trip: Trip) = dao.update(trip.toEntity())

    suspend fun markLoyaltyAttached(tripId: Long, program: LoyaltyProgram?) {
        val existing = dao.findById(tripId) ?: return
        dao.update(existing.copy(loyaltyNumberOnBooking = true, loyaltyProgram = program ?: existing.loyaltyProgram))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
