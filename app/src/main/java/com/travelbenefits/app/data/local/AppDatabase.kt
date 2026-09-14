package com.travelbenefits.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.travelbenefits.app.data.local.dao.ActionStateDao
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.dao.UserOverrideDao
import com.travelbenefits.app.data.local.dao.AwardWatchDao
import com.travelbenefits.app.data.local.dao.BenefitDao
import com.travelbenefits.app.data.local.dao.BenefitLedgerDao
import com.travelbenefits.app.data.local.dao.NotifiedAlertDao
import com.travelbenefits.app.data.local.dao.PlaidDao
import com.travelbenefits.app.data.local.dao.RotatingCategoryDao
import com.travelbenefits.app.data.local.dao.TransferBonusDao
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.PointsSnapshotDao
import com.travelbenefits.app.data.local.dao.ProcessedEmailDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.ActionStateEntity
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.UserOverrideEntity
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.BenefitLedgerEntity
import com.travelbenefits.app.data.local.entity.CreditUsageEntity
import com.travelbenefits.app.data.local.entity.NotifiedAlertEntity
import com.travelbenefits.app.data.local.entity.PlaidAccountEntity
import com.travelbenefits.app.data.local.entity.PlaidItemEntity
import com.travelbenefits.app.data.local.entity.TransactionEntity
import com.travelbenefits.app.data.local.entity.RotatingCategoryEntity
import com.travelbenefits.app.data.local.entity.TransferBonusEntity
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
        BenefitItemEntity::class,
        CreditUsageEntity::class,
        RotatingCategoryEntity::class,
        AwardWatchEntity::class,
        TransferBonusEntity::class,
        NotifiedAlertEntity::class,
        PlaidItemEntity::class,
        PlaidAccountEntity::class,
        TransactionEntity::class,
        BenefitLedgerEntity::class,
        ActionStateEntity::class,
        UserOverrideEntity::class,
    ],
    version = 7,
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
    abstract fun benefitDao(): BenefitDao
    abstract fun rotatingCategoryDao(): RotatingCategoryDao
    abstract fun awardWatchDao(): AwardWatchDao
    abstract fun transferBonusDao(): TransferBonusDao
    abstract fun notifiedAlertDao(): NotifiedAlertDao
    abstract fun plaidDao(): PlaidDao
    abstract fun benefitLedgerDao(): BenefitLedgerDao
    abstract fun actionStateDao(): ActionStateDao
    abstract fun userOverrideDao(): UserOverrideDao

    companion object {
        const val DATABASE_NAME = "travel_benefits.db"
    }
}
