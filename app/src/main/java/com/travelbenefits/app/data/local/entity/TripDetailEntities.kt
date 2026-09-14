package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_segments")
data class TripSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val sequence: Int,
    val carrier: String?,
    val flightNumber: String?,
    val origin: String?,
    val destination: String?,
    val departLocal: String?,
    val arriveLocal: String?,
    val timeZoneId: String?,
    val cabin: String?,
    val status: String,
)

/** Booking history: every change, cancellation, payment or benefit recorded against a trip. */
@Entity(tableName = "trip_events")
data class TripEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val kind: String,
    val detail: String,
    val occurredAt: Long,
    val source: String,
)

/**
 * Something the app expects to arrive: card rewards for a purchase, program
 * points/nights for a stay, a statement credit. Reconciled against records
 * later; discrepancies go to the queue.
 */
@Entity(tableName = "expectations")
data class ExpectationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refType: String,
    val refId: String,
    val walletCardId: Long?,
    val program: String?,
    val expectedAmount: Double,
    val unit: String,
    val dueByEpochDay: Long,
    val status: String,
    val receivedAmount: Double?,
    val evidence: String?,
    val note: String?,
    val createdAt: Long,
    val resolvedAt: Long?,
)

/** Movements that must not inflate totals: transfers between balances, de-duplicated imports, redemptions. */
@Entity(tableName = "attribution_events")
data class AttributionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val fromCurrency: String?,
    val toProgram: String?,
    val amount: Double,
    val valueUsd: Double?,
    val occurredAt: Long,
    val note: String?,
)
