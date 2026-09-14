package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: TripKind,
    val provider: String,
    val confirmationNumber: String?,
    val title: String,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
    val origin: String?,
    val destination: String?,
    val loyaltyProgram: LoyaltyProgram?,
    val loyaltyNumberOnBooking: Boolean,
    val totalCost: String?,
    val pointsUsed: Long?,
    val pointsEarnedEstimate: Long?,
    val source: TripSource,
    val sourceEmailSubject: String?,
    val sourceMessageId: String?,
    val notes: String?,
    val createdAt: Long,
    /** Added in DB v7. */
    val paymentCardId: Long? = null,
    /** Added in DB v8 (all nullable). */
    val status: String? = null,
    val loyaltyNumberState: String? = null,
    val bookingChannel: String? = null,
    val cancellationTerms: String? = null,
    val departureTimeLocal: String? = null,
    val timeZoneId: String? = null,
    val travelers: String? = null,
    val certificateId: Long? = null,
    val cashPriceUsd: Double? = null,
    val pointsProgram: String? = null,
)
