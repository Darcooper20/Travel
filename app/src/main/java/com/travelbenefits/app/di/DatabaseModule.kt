package com.travelbenefits.app.di

import android.content.Context
import androidx.room.Room
import com.travelbenefits.app.data.local.AppDatabase
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    fun provideWalletCardDao(db: AppDatabase): WalletCardDao = db.walletCardDao()

    @Provides
    fun provideLoyaltyAccountDao(db: AppDatabase): LoyaltyAccountDao = db.loyaltyAccountDao()

    @Provides
    fun provideCardLookupCacheDao(db: AppDatabase): CardLookupCacheDao = db.cardLookupCacheDao()
}
