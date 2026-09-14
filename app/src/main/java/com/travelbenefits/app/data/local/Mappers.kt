package com.travelbenefits.app.data.local

import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.domain.model.ActivityEvent
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.PointsSnapshot
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.WalletCard

fun WalletCardEntity.toDomain(): WalletCard = WalletCard(
    id = id,
    nickname = nickname,
    catalogCardId = catalogCardId,
    customCardName = customCardName,
    dateAdded = dateAdded,
    notes = notes,
)

fun LoyaltyAccountEntity.toDomain(): LoyaltyAccount = LoyaltyAccount(
    id = id,
    program = program,
    membershipNumber = membershipNumber,
    tier = tier,
    pointsBalance = pointsBalance,
    source = source,
    sourceEmailSubject = sourceEmailSubject,
    lastUpdated = lastUpdated,
    pointsNumeric = pointsNumeric,
    pointsExpireAt = pointsExpireAt,
    qualifyingProgress = qualifyingProgress,
    lastActivityAt = lastActivityAt,
)

fun TripEntity.toDomain(): Trip = Trip(
    id = id,
    kind = kind,
    provider = provider,
    confirmationNumber = confirmationNumber,
    title = title,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    origin = origin,
    destination = destination,
    loyaltyProgram = loyaltyProgram,
    loyaltyNumberOnBooking = loyaltyNumberOnBooking,
    totalCost = totalCost,
    pointsUsed = pointsUsed,
    pointsEarnedEstimate = pointsEarnedEstimate,
    source = source,
    sourceEmailSubject = sourceEmailSubject,
    sourceMessageId = sourceMessageId,
    notes = notes,
    createdAt = createdAt,
)

fun Trip.toEntity(): TripEntity = TripEntity(
    id = id,
    kind = kind,
    provider = provider,
    confirmationNumber = confirmationNumber,
    title = title,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    origin = origin,
    destination = destination,
    loyaltyProgram = loyaltyProgram,
    loyaltyNumberOnBooking = loyaltyNumberOnBooking,
    totalCost = totalCost,
    pointsUsed = pointsUsed,
    pointsEarnedEstimate = pointsEarnedEstimate,
    source = source,
    sourceEmailSubject = sourceEmailSubject,
    sourceMessageId = sourceMessageId,
    notes = notes,
    createdAt = createdAt,
)

fun PointsSnapshotEntity.toDomain(): PointsSnapshot = PointsSnapshot(
    id = id,
    program = program,
    points = points,
    tier = tier,
    recordedAt = recordedAt,
    source = source,
)

fun ActivityEventEntity.toDomain(): ActivityEvent = ActivityEvent(
    id = id,
    kind = kind,
    program = program,
    title = title,
    detail = detail,
    occurredAt = occurredAt,
    isRead = isRead,
)
