package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.dao.TripDetailDao
import com.travelbenefits.app.data.local.entity.AttributionEventEntity
import com.travelbenefits.app.data.local.entity.ExpectationEntity
import com.travelbenefits.app.data.local.entity.TripEventEntity
import com.travelbenefits.app.data.local.entity.TripSegmentEntity
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.TripEvent
import com.travelbenefits.app.domain.model.TripEventKind
import com.travelbenefits.app.domain.model.TripSegment
import com.travelbenefits.app.domain.model.TripStatus
import com.travelbenefits.app.domain.model.Expectation
import com.travelbenefits.app.domain.model.ExpectationKind
import com.travelbenefits.app.domain.model.ExpectationStatus
import com.travelbenefits.app.domain.model.AttributionEvent
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
    private val detailDao: TripDetailDao,
) {
    fun observeTrips(): Flow<List<Trip>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTrip(id: Long): Flow<Trip?> = dao.observeAll().map { list -> list.firstOrNull { it.id == id }?.toDomain() }

    suspend fun getTrips(): List<Trip> = dao.getAll().map { it.toDomain() }

    fun observeSegments(tripId: Long): Flow<List<TripSegment>> = detailDao.observeSegments(tripId).map { l -> l.map { it.toDomain() } }

    fun observeAllSegments(): Flow<List<TripSegment>> = detailDao.observeAllSegments().map { l -> l.map { it.toDomain() } }

    fun observeEvents(tripId: Long): Flow<List<TripEvent>> = detailDao.observeEvents(tripId).map { l -> l.map { it.toDomain() } }

    suspend fun replaceSegments(tripId: Long, segments: List<TripSegment>) {
        detailDao.deleteSegments(tripId)
        detailDao.insertSegments(segments.mapIndexed { i, seg -> TripSegmentEntity(tripId = tripId, sequence = i, carrier = seg.carrier, flightNumber = seg.flightNumber, origin = seg.origin, destination = seg.destination, departLocal = seg.departLocal, arriveLocal = seg.arriveLocal, timeZoneId = seg.timeZoneId, cabin = seg.cabin, status = seg.status.name) })
    }

    suspend fun addEvent(tripId: Long, kind: TripEventKind, detail: String, source: String = "app", at: Long = System.currentTimeMillis()) =
        detailDao.insertEvent(TripEventEntity(tripId = tripId, kind = kind.name, detail = detail, occurredAt = at, source = source))

    /** Marks a trip cancelled (or partially), keeping the record and history; reminders stop through [Trip.isUpcoming]. */
    suspend fun setStatus(tripId: Long, status: TripStatus, detail: String, source: String = "app") {
        val existing = dao.findById(tripId) ?: return
        if (existing.status == status.name) return
        dao.update(existing.copy(status = status.name))
        addEvent(tripId, when (status) { TripStatus.CANCELLED, TripStatus.PARTIALLY_CANCELLED -> TripEventKind.CANCELLED; TripStatus.CHANGED -> TripEventKind.CHANGED; TripStatus.CONFIRMED -> TripEventKind.REBOOKED }, detail, source)
    }

    suspend fun setLoyaltyNumberState(tripId: Long, state: LoyaltyNumberState, program: LoyaltyProgram?) {
        val existing = dao.findById(tripId) ?: return
        dao.update(existing.copy(loyaltyNumberState = state.name, loyaltyNumberOnBooking = state == LoyaltyNumberState.CONFIRMED, loyaltyProgram = program ?: existing.loyaltyProgram))
    }

    /** Records how the trip is paid and links benefits; writes a history event. */
    suspend fun recordPayment(tripId: Long, paymentCardId: Long?, pointsProgram: LoyaltyProgram?, pointsUsed: Long?, cashPriceUsd: Double?, certificateId: Long?, channel: com.travelbenefits.app.domain.model.BookingChannel?, cancellationTerms: String?, travelers: String?) {
        val existing = dao.findById(tripId) ?: return
        dao.update(
            existing.copy(
                paymentCardId = paymentCardId, pointsProgram = pointsProgram?.name, pointsUsed = pointsUsed ?: existing.pointsUsed, cashPriceUsd = cashPriceUsd,
                certificateId = certificateId, bookingChannel = channel?.name, cancellationTerms = cancellationTerms, travelers = travelers,
            ),
        )
        addEvent(tripId, TripEventKind.PAYMENT, listOfNotNull(paymentCardId?.let { "card #$it" }, pointsProgram?.let { "${pointsUsed ?: 0} ${it.displayName} points" }, certificateId?.let { "certificate #$it" }).joinToString(", ").ifBlank { "payment details updated" })
    }

    suspend fun setDepartureTime(tripId: Long, timeLocal: String?, zoneId: String?) {
        val existing = dao.findById(tripId) ?: return
        dao.update(existing.copy(departureTimeLocal = timeLocal, timeZoneId = zoneId))
    }

    // ---- Expectations & attribution ----
    fun observeExpectations(): Flow<List<Expectation>> = detailDao.observeExpectations().map { l -> l.map { it.toDomain() } }

    suspend fun getExpectations(): List<Expectation> = detailDao.expectations().map { it.toDomain() }

    /** Idempotent: one expectation per (refType, refId, kind). */
    suspend fun ensureExpectation(kind: ExpectationKind, refType: String, refId: String, walletCardId: Long?, program: LoyaltyProgram?, expected: Double, unit: String, dueByEpochDay: Long, note: String?): Long {
        detailDao.findExpectation(refType, refId, kind.name)?.let { existing ->
            if (existing.status == ExpectationStatus.OPEN.name && existing.expectedAmount != expected) detailDao.updateExpectation(existing.copy(expectedAmount = expected, dueByEpochDay = dueByEpochDay))
            return existing.id
        }
        return detailDao.insertExpectation(ExpectationEntity(kind = kind.name, refType = refType, refId = refId, walletCardId = walletCardId, program = program?.name, expectedAmount = expected, unit = unit, dueByEpochDay = dueByEpochDay, status = ExpectationStatus.OPEN.name, receivedAmount = null, evidence = null, note = note, createdAt = System.currentTimeMillis(), resolvedAt = null))
    }

    suspend fun resolveExpectation(id: Long, status: ExpectationStatus, received: Double?, evidence: String?) {
        val existing = detailDao.findExpectationById(id) ?: return
        detailDao.updateExpectation(existing.copy(status = status.name, receivedAmount = received ?: existing.receivedAmount, evidence = evidence ?: existing.evidence, resolvedAt = if (status == ExpectationStatus.OPEN) null else System.currentTimeMillis()))
    }

    fun observeAttribution(): Flow<List<AttributionEvent>> = detailDao.observeAttribution().map { l -> l.map { AttributionEvent(it.id, it.kind, it.fromCurrency, it.toProgram, it.amount, it.valueUsd, it.occurredAt, it.note) } }

    suspend fun recordAttribution(kind: String, fromCurrency: String?, toProgram: String?, amount: Double, valueUsd: Double?, note: String?) =
        detailDao.insertAttribution(AttributionEventEntity(kind = kind, fromCurrency = fromCurrency, toProgram = toProgram, amount = amount, valueUsd = valueUsd, occurredAt = System.currentTimeMillis(), note = note))

    private fun ExpectationEntity.toDomain() = Expectation(
        id, ExpectationKind.entries.firstOrNull { it.name == kind } ?: ExpectationKind.OTHER, refType, refId, walletCardId,
        program?.let { p -> LoyaltyProgram.entries.firstOrNull { it.name == p } }, expectedAmount, unit, dueByEpochDay,
        ExpectationStatus.entries.firstOrNull { it.name == status } ?: ExpectationStatus.OPEN, receivedAmount, evidence, note, createdAt, resolvedAt,
    )

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
            loyaltyNumberState = (if (loyaltyNumberOnBooking) LoyaltyNumberState.CONFIRMED else LoyaltyNumberState.UNKNOWN).name,
            status = TripStatus.CONFIRMED.name,
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

    suspend fun markLoyaltyAttached(tripId: Long, program: LoyaltyProgram?) = setLoyaltyNumberState(tripId, LoyaltyNumberState.CONFIRMED, program)

    suspend fun delete(id: Long) = dao.deleteById(id)
}
