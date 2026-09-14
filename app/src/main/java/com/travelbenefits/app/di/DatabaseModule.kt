package com.travelbenefits.app.di

import android.content.Context
import androidx.room.Room
import com.travelbenefits.app.data.local.AppDatabase
import com.travelbenefits.app.data.local.Migrations
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.dao.ProcessedEmailDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideWalletCardDao(db: AppDatabase): WalletCardDao = db.walletCardDao()

    @Provides
    fun provideLoyaltyAccountDao(db: AppDatabase): LoyaltyAccountDao = db.loyaltyAccountDao()

    @Provides
    fun provideCardLookupCacheDao(db: AppDatabase): CardLookupCacheDao = db.cardLookupCacheDao()

    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun providePointsSnapshotDao(db: AppDatabase): PointsSnapshotDao = db.pointsSnapshotDao()

    @Provides
    fun provideActivityEventDao(db: AppDatabase): ActivityEventDao = db.activityEventDao()

    @Provides
    fun provideProcessedEmailDao(db: AppDatabase): ProcessedEmailDao = db.processedEmailDao()
}
