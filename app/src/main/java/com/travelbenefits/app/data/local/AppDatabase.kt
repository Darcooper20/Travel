package com.travelbenefits.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.CardLookupCacheEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity

@Database(
    entities = [
        WalletCardEntity::class,
        LoyaltyAccountEntity::class,
        CardLookupCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletCardDao(): WalletCardDao
    abstract fun loyaltyAccountDao(): LoyaltyAccountDao
    abstract fun cardLookupCacheDao(): CardLookupCacheDao

    companion object {
        const val DATABASE_NAME = "travel_benefits.db"
    }
}
