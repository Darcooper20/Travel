package com.travelbenefits.app.data.local

import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.BenefitLedgerEntity
import com.travelbenefits.app.data.local.entity.RotatingCategoryEntity
import com.travelbenefits.app.data.local.entity.TransferBonusEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.domain.model.ActivityEvent
import com.travelbenefits.app.domain.model.AwardWatch
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.LedgerEntry
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TransferBonus
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.PointsSnapshot
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripStatus
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.TripSegment
import com.travelbenefits.app.domain.model.TripEvent
import com.travelbenefits.app.domain.model.TripEventKind
import com.travelbenefits.app.data.local.entity.TripSegmentEntity
import com.travelbenefits.app.data.local.entity.TripEventEntity
import com.travelbenefits.app.domain.model.WalletCard

fun WalletCardEntity.toDomain(): WalletCard = WalletCard(
    id = id,
    nickname = nickname,
    catalogCardId = catalogCardId,
    customCardName = customCardName,
    dateAdded = dateAdded,
    notes = notes,
    rewardsBalance = rewardsBalance,
    rewardsBalanceAsOf = rewardsBalanceAsOf,
    last4 = last4,
    dateOpenedEpochDay = dateOpenedEpochDay,
    bonusSpendRequiredUsd = bonusSpendRequiredUsd,
    bonusDeadlineEpochDay = bonusDeadlineEpochDay,
    bonusSpendToDateUsd = bonusSpendToDateUsd,
    bonusEarnedAt = bonusEarnedAt,
    memberName = memberName,
    isAuthorizedUser = isAuthorizedUser,
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
    memberName = memberName,
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
    paymentCardId = paymentCardId,
    status = status?.let { v -> TripStatus.entries.firstOrNull { it.name == v } } ?: TripStatus.CONFIRMED,
    loyaltyNumberState = loyaltyNumberState?.let { v -> LoyaltyNumberState.entries.firstOrNull { it.name == v } }
        ?: if (loyaltyNumberOnBooking) LoyaltyNumberState.CONFIRMED else LoyaltyNumberState.UNKNOWN,
    bookingChannel = bookingChannel?.let { v -> BookingChannel.entries.firstOrNull { it.name == v } },
    cancellationTerms = cancellationTerms,
    departureTimeLocal = departureTimeLocal,
    timeZoneId = timeZoneId,
    travelers = travelers,
    certificateId = certificateId,
    cashPriceUsd = cashPriceUsd,
    pointsProgram = pointsProgram?.let { v -> LoyaltyProgram.entries.firstOrNull { it.name == v } },
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
    paymentCardId = paymentCardId,
    status = status.name,
    loyaltyNumberState = loyaltyNumberState.name,
    bookingChannel = bookingChannel?.name,
    cancellationTerms = cancellationTerms,
    departureTimeLocal = departureTimeLocal,
    timeZoneId = timeZoneId,
    travelers = travelers,
    certificateId = certificateId,
    cashPriceUsd = cashPriceUsd,
    pointsProgram = pointsProgram?.name,
)

fun TripSegmentEntity.toDomain(): TripSegment = TripSegment(
    id, tripId, sequence, carrier, flightNumber, origin, destination, departLocal, arriveLocal, timeZoneId, cabin,
    TripStatus.entries.firstOrNull { it.name == status } ?: TripStatus.CONFIRMED,
)

fun TripEventEntity.toDomain(): TripEvent = TripEvent(id, tripId, TripEventKind.entries.firstOrNull { it.name == kind } ?: TripEventKind.NOTE, detail, occurredAt, source)

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

fun BenefitItemEntity.toDomain(): BenefitItem = BenefitItem(
    id = id,
    kind = kind,
    title = title,
    program = program,
    walletCardId = walletCardId,
    valueUsd = valueUsd,
    expiresEpochDay = expiresEpochDay,
    usedAt = usedAt,
    notes = notes,
    source = source,
    sourceEmailSubject = sourceEmailSubject,
    createdAt = createdAt,
)

fun BenefitItem.toEntity(): BenefitItemEntity = BenefitItemEntity(
    id = id,
    kind = kind,
    title = title,
    program = program,
    walletCardId = walletCardId,
    valueUsd = valueUsd,
    expiresEpochDay = expiresEpochDay,
    usedAt = usedAt,
    notes = notes,
    source = source,
    sourceEmailSubject = sourceEmailSubject,
    createdAt = createdAt,
)

fun RotatingCategoryEntity.toDomain(): RotatingSelection = RotatingSelection(
    walletCardId = walletCardId,
    quarterKey = quarterKey,
    categories = categoriesCsv.split(',').mapNotNull { name -> SpendingCategory.entries.firstOrNull { it.name == name.trim() } },
    activated = activated,
)

fun RotatingSelection.toEntity(): RotatingCategoryEntity = RotatingCategoryEntity(
    walletCardId = walletCardId,
    quarterKey = quarterKey,
    categoriesCsv = categories.joinToString(",") { it.name },
    activated = activated,
)

fun AwardWatchEntity.toDomain(): AwardWatch = AwardWatch(
    id = id,
    title = title,
    program = program,
    origin = origin,
    destination = destination,
    dateFrom = dateFrom,
    dateTo = dateTo,
    notes = notes,
    active = active,
    createdAt = createdAt,
    lastCheckedAt = lastCheckedAt,
    lastResult = lastResult,
    lastFound = lastFound,
)

fun AwardWatch.toEntity(): AwardWatchEntity = AwardWatchEntity(
    id = id,
    title = title,
    program = program,
    origin = origin,
    destination = destination,
    dateFrom = dateFrom,
    dateTo = dateTo,
    notes = notes,
    active = active,
    createdAt = createdAt,
    lastCheckedAt = lastCheckedAt,
    lastResult = lastResult,
    lastFound = lastFound,
)

fun TransferBonusEntity.toDomain(): TransferBonus = TransferBonus(
    id = id,
    from = fromCurrency,
    to = toProgram,
    bonusPercent = bonusPercent,
    endsEpochDay = endsEpochDay,
    note = note,
    checkedAt = checkedAt,
)

fun BenefitLedgerEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = id, walletCardId = walletCardId, creditLabel = creditLabel, kind = kind, amountCents = amountCents, epochDay = epochDay,
    transactionId = transactionId, note = note, source = source, needsConfirmation = needsConfirmation, createdAt = createdAt,
)
