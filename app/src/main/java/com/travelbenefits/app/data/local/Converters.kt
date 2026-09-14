package com.travelbenefits.app.data.local

import androidx.room.TypeConverter
import com.travelbenefits.app.domain.model.ActionState
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.LedgerSource
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource

class Converters {
    @TypeConverter
    fun fromLoyaltyProgram(value: LoyaltyProgram): String = value.name

    @TypeConverter
    fun toLoyaltyProgram(value: String): LoyaltyProgram = LoyaltyProgram.valueOf(value)

    @TypeConverter
    fun fromLoyaltyAccountSource(value: LoyaltyAccountSource): String = value.name

    @TypeConverter
    fun toLoyaltyAccountSource(value: String): LoyaltyAccountSource = LoyaltyAccountSource.valueOf(value)

    @TypeConverter
    fun fromTripKind(value: TripKind): String = value.name

    @TypeConverter
    fun toTripKind(value: String): TripKind = TripKind.valueOf(value)

    @TypeConverter
    fun fromTripSource(value: TripSource): String = value.name

    @TypeConverter
    fun toTripSource(value: String): TripSource = TripSource.valueOf(value)

    @TypeConverter
    fun fromActivityKind(value: ActivityKind): String = value.name

    @TypeConverter
    fun toActivityKind(value: String): ActivityKind = ActivityKind.valueOf(value)

    @TypeConverter
    fun fromBenefitKind(value: BenefitKind): String = value.name

    @TypeConverter
    fun toBenefitKind(value: String): BenefitKind = BenefitKind.valueOf(value)

    @TypeConverter
    fun fromRewardCurrency(value: RewardCurrency): String = value.name

    @TypeConverter
    fun toRewardCurrency(value: String): RewardCurrency = RewardCurrency.valueOf(value)

    @TypeConverter
    fun fromLedgerEntryKind(value: LedgerEntryKind): String = value.name

    @TypeConverter
    fun toLedgerEntryKind(value: String): LedgerEntryKind = LedgerEntryKind.valueOf(value)

    @TypeConverter
    fun fromLedgerSource(value: LedgerSource): String = value.name

    @TypeConverter
    fun toLedgerSource(value: String): LedgerSource = LedgerSource.valueOf(value)

    @TypeConverter
    fun fromActionState(value: ActionState): String = value.name

    @TypeConverter
    fun toActionState(value: String): ActionState = ActionState.valueOf(value)
}
