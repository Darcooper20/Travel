package com.travelbenefits.app.data.local

import androidx.room.TypeConverter
import com.travelbenefits.app.domain.model.HotelProgram
import com.travelbenefits.app.domain.model.LoyaltyAccountSource

class Converters {
    @TypeConverter
    fun fromHotelProgram(value: HotelProgram): String = value.name

    @TypeConverter
    fun toHotelProgram(value: String): HotelProgram = HotelProgram.valueOf(value)

    @TypeConverter
    fun fromLoyaltyAccountSource(value: LoyaltyAccountSource): String = value.name

    @TypeConverter
    fun toLoyaltyAccountSource(value: String): LoyaltyAccountSource = LoyaltyAccountSource.valueOf(value)
}
