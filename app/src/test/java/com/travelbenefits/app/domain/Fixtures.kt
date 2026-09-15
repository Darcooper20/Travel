package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.CardCredit
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.RuleProvenance
import com.travelbenefits.app.domain.model.SpendTransaction
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TravelPerk
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import com.travelbenefits.app.domain.model.WalletCard

/** Shared builders so each test states only what it actually cares about. */
object Fixtures {

    val provenance = RuleProvenance("https://example.test", "2026-09-01")

    fun wallet(
        id: Long,
        dateOpenedEpochDay: Long? = null,
        memberName: String? = null,
        bonusEarnedAt: Long? = null,
    ) = WalletCard(
        id = id,
        nickname = null,
        catalogCardId = "card$id",
        customCardName = null,
        dateAdded = 0,
        notes = null,
        dateOpenedEpochDay = dateOpenedEpochDay,
        memberName = memberName,
        bonusEarnedAt = bonusEarnedAt,
    )

    fun entry(
        id: Long,
        rates: List<RewardRate> = emptyList(),
        base: Double = 1.0,
        currency: RewardCurrency = RewardCurrency.CASH_BACK,
        annualFeeUsd: Int = 0,
        discontinued: Boolean = false,
        credits: List<CardCredit> = emptyList(),
        perks: List<TravelPerk> = emptyList(),
        authorizedUserFeeUsd: Int? = null,
        rotatingCapUsd: Int? = null,
    ) = CardCatalogEntry(
        id = "card$id",
        displayName = "Card $id",
        issuer = "Test",
        network = "Visa",
        annualFeeUsd = annualFeeUsd,
        rewardCurrency = currency,
        baseMultiplier = base,
        categoryRates = rates,
        credits = credits,
        dataAsOf = "test",
        isDiscontinued = discontinued,
        rotatingCapUsd = rotatingCapUsd,
        provenance = provenance,
        travelPerks = perks,
        authorizedUserFeeUsd = authorizedUserFeeUsd,
    )

    fun card(
        id: Long,
        rates: List<RewardRate> = emptyList(),
        base: Double = 1.0,
        currency: RewardCurrency = RewardCurrency.CASH_BACK,
        annualFeeUsd: Int = 0,
        discontinued: Boolean = false,
        credits: List<CardCredit> = emptyList(),
        perks: List<TravelPerk> = emptyList(),
        authorizedUserFeeUsd: Int? = null,
        rotating: RotatingSelection? = null,
        rotatingCapUsd: Int? = null,
        dateOpenedEpochDay: Long? = null,
        memberName: String? = null,
        bonusEarnedAt: Long? = null,
    ) = ResolvedWalletCard.Catalog(
        wallet(id, dateOpenedEpochDay, memberName, bonusEarnedAt),
        entry(id, rates, base, currency, annualFeeUsd, discontinued, credits, perks, authorizedUserFeeUsd, rotatingCapUsd),
        rotating,
    )

    fun txn(
        id: String,
        cardId: Long?,
        amountUsd: Double,
        epochDay: Long,
        category: SpendingCategory?,
        merchant: String? = null,
        pending: Boolean = false,
        accountId: String = "acct",
    ) = SpendTransaction(
        transactionId = id,
        accountId = accountId,
        walletCardId = cardId,
        amountUsd = amountUsd,
        epochDay = epochDay,
        name = merchant ?: "Purchase",
        merchantName = merchant,
        category = category,
        pending = pending,
    )

    fun account(
        id: Long = 1,
        program: LoyaltyProgram = LoyaltyProgram.MARRIOTT_BONVOY,
        membershipNumber: String? = "123456",
        tier: String? = null,
        pointsNumeric: Long? = null,
        pointsBalance: String? = null,
        pointsExpireAt: Long? = null,
        qualifyingProgress: Int? = null,
        lastActivityAt: Long? = null,
        memberName: String? = null,
    ) = LoyaltyAccount(
        id = id,
        program = program,
        membershipNumber = membershipNumber,
        tier = tier,
        pointsBalance = pointsBalance,
        source = LoyaltyAccountSource.MANUAL,
        sourceEmailSubject = null,
        lastUpdated = 0L,
        pointsNumeric = pointsNumeric,
        pointsExpireAt = pointsExpireAt,
        qualifyingProgress = qualifyingProgress,
        lastActivityAt = lastActivityAt,
        memberName = memberName,
    )

    fun trip(
        id: Long = 1,
        kind: TripKind = TripKind.HOTEL,
        startEpochDay: Long? = null,
        program: LoyaltyProgram? = LoyaltyProgram.MARRIOTT_BONVOY,
        loyaltyNumberState: LoyaltyNumberState = LoyaltyNumberState.UNKNOWN,
        title: String = "Hyatt Regency Austin",
        confirmationNumber: String? = "ABC123",
    ) = Trip(
        id = id,
        kind = kind,
        provider = "Test provider",
        confirmationNumber = confirmationNumber,
        title = title,
        startEpochDay = startEpochDay,
        endEpochDay = startEpochDay?.plus(2),
        origin = null,
        destination = "Austin",
        loyaltyProgram = program,
        loyaltyNumberOnBooking = loyaltyNumberState == LoyaltyNumberState.CONFIRMED,
        totalCost = null,
        pointsUsed = null,
        pointsEarnedEstimate = null,
        source = TripSource.MANUAL,
        sourceEmailSubject = null,
        sourceMessageId = null,
        notes = null,
        createdAt = 0L,
        loyaltyNumberState = loyaltyNumberState,
    )
}
