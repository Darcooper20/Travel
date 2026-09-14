package com.travelbenefits.app.data.local

import androidx.room.TypeConverter
import com.travelbenefits.app.domain.model.ActivityKind
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
}
