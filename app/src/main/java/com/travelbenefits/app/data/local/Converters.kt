package com.travelbenefits.app.data.local

import androidx.room.TypeConverter
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram

class Converters {
    @TypeConverter
    fun fromLoyaltyProgram(value: LoyaltyProgram): String = value.name

    @TypeConverter
    fun toLoyaltyProgram(value: String): LoyaltyProgram = LoyaltyProgram.valueOf(value)

    @TypeConverter
    fun fromLoyaltyAccountSource(value: LoyaltyAccountSource): String = value.name

    @TypeConverter
    fun toLoyaltyAccountSource(value: String): LoyaltyAccountSource = LoyaltyAccountSource.valueOf(value)
}
