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

/**
 * Room type converters for the enum columns.
 *
 * NULLABILITY MATTERS HERE. Room generates the binding code from these
 * signatures: when a converter declares a non-null parameter, the generated
 * `bind` calls it directly, with no null check of its own. So a non-null
 * converter on a NULLABLE column (`activity_events.program`, and three more
 * like it) meant Kotlin's own non-null intrinsic threw on every insert where
 * that column was null - which for activity events is the normal case, since
 * most of them are not about a specific programme. A converter that accepts
 * and returns nullable types handles both nullable and non-null columns:
 * Room binds NULL when the result is null, and a NOT NULL column can never
 * hand back a null to convert. That is why every pair below is nullable,
 * including the ones whose columns are currently all non-null - a column
 * turning nullable later must not be able to reintroduce this crash.
 *
 * [LoyaltyProgram.valueOf] and friends still throw on a name that is not in
 * the enum. That is deliberate and separate: a stored name with no constant
 * means the database disagrees with the code, and failing loudly beats
 * silently dropping the row's programme.
 */
class Converters {
    @TypeConverter
    fun fromLoyaltyProgram(value: LoyaltyProgram?): String? = value?.name

    @TypeConverter
    fun toLoyaltyProgram(value: String?): LoyaltyProgram? = value?.let { LoyaltyProgram.valueOf(it) }

    @TypeConverter
    fun fromLoyaltyAccountSource(value: LoyaltyAccountSource?): String? = value?.name

    @TypeConverter
    fun toLoyaltyAccountSource(value: String?): LoyaltyAccountSource? = value?.let { LoyaltyAccountSource.valueOf(it) }

    @TypeConverter
    fun fromTripKind(value: TripKind?): String? = value?.name

    @TypeConverter
    fun toTripKind(value: String?): TripKind? = value?.let { TripKind.valueOf(it) }

    @TypeConverter
    fun fromTripSource(value: TripSource?): String? = value?.name

    @TypeConverter
    fun toTripSource(value: String?): TripSource? = value?.let { TripSource.valueOf(it) }

    @TypeConverter
    fun fromActivityKind(value: ActivityKind?): String? = value?.name

    @TypeConverter
    fun toActivityKind(value: String?): ActivityKind? = value?.let { ActivityKind.valueOf(it) }

    @TypeConverter
    fun fromBenefitKind(value: BenefitKind?): String? = value?.name

    @TypeConverter
    fun toBenefitKind(value: String?): BenefitKind? = value?.let { BenefitKind.valueOf(it) }

    @TypeConverter
    fun fromRewardCurrency(value: RewardCurrency?): String? = value?.name

    @TypeConverter
    fun toRewardCurrency(value: String?): RewardCurrency? = value?.let { RewardCurrency.valueOf(it) }

    @TypeConverter
    fun fromLedgerEntryKind(value: LedgerEntryKind?): String? = value?.name

    @TypeConverter
    fun toLedgerEntryKind(value: String?): LedgerEntryKind? = value?.let { LedgerEntryKind.valueOf(it) }

    @TypeConverter
    fun fromLedgerSource(value: LedgerSource?): String? = value?.name

    @TypeConverter
    fun toLedgerSource(value: String?): LedgerSource? = value?.let { LedgerSource.valueOf(it) }

    @TypeConverter
    fun fromActionState(value: ActionState?): String? = value?.name

    @TypeConverter
    fun toActionState(value: String?): ActionState? = value?.let { ActionState.valueOf(it) }
}
