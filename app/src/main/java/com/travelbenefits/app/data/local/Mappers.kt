package com.travelbenefits.app.data.local

import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.domain.model.LoyaltyAccount
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
)
