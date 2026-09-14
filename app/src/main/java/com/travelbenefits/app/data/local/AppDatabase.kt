package com.travelbenefits.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.dao.ProcessedEmailDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.CardLookupCacheEntity
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.data.local.entity.ProcessedEmailEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.data.local.entity.WalletCardEntity

@Database(
    entities = [
        WalletCardEntity::class,
        LoyaltyAccountEntity::class,
        CardLookupCacheEntity::class,
        TripEntity::class,
        PointsSnapshotEntity::class,
        ActivityEventEntity::class,
        ProcessedEmailEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletCardDao(): WalletCardDao
    abstract fun loyaltyAccountDao(): LoyaltyAccountDao
    abstract fun cardLookupCacheDao(): CardLookupCacheDao
    abstract fun tripDao(): TripDao
    abstract fun pointsSnapshotDao(): PointsSnapshotDao
    abstract fun activityEventDao(): ActivityEventDao
    abstract fun processedEmailDao(): ProcessedEmailDao

    companion object {
        const val DATABASE_NAME = "travel_benefits.db"
    }
}
