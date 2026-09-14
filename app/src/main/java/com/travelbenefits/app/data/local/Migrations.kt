package com.travelbenefits.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations. Room validates the post-migration schema against
 * the entities on open, so the CREATE/ALTER statements below must match the
 * entity classes column-for-column (name, affinity, NOT NULL, primary key).
 */
object Migrations {

    /** v1 -> v2: numeric/expiry/progress columns on loyalty accounts, plus trips, balance history, activity feed and the processed-email ledger. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `loyalty_accounts` ADD COLUMN `pointsNumeric` INTEGER")
            db.execSQL("ALTER TABLE `loyalty_accounts` ADD COLUMN `pointsExpireAt` INTEGER")
            db.execSQL("ALTER TABLE `loyalty_accounts` ADD COLUMN `qualifyingProgress` INTEGER")
            db.execSQL("ALTER TABLE `loyalty_accounts` ADD COLUMN `lastActivityAt` INTEGER")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trips` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`provider` TEXT NOT NULL, " +
                    "`confirmationNumber` TEXT, " +
                    "`title` TEXT NOT NULL, " +
                    "`startEpochDay` INTEGER, " +
                    "`endEpochDay` INTEGER, " +
                    "`origin` TEXT, " +
                    "`destination` TEXT, " +
                    "`loyaltyProgram` TEXT, " +
                    "`loyaltyNumberOnBooking` INTEGER NOT NULL, " +
                    "`totalCost` TEXT, " +
                    "`pointsUsed` INTEGER, " +
                    "`pointsEarnedEstimate` INTEGER, " +
                    "`source` TEXT NOT NULL, " +
                    "`sourceEmailSubject` TEXT, " +
                    "`sourceMessageId` TEXT, " +
                    "`notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `points_snapshots` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`program` TEXT NOT NULL, " +
                    "`points` INTEGER NOT NULL, " +
                    "`tier` TEXT, " +
                    "`recordedAt` INTEGER NOT NULL, " +
                    "`source` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`program` TEXT, " +
                    "`title` TEXT NOT NULL, " +
                    "`detail` TEXT, " +
                    "`occurredAt` INTEGER NOT NULL, " +
                    "`isRead` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `processed_emails` (" +
                    "`messageId` TEXT NOT NULL, " +
                    "`processedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`messageId`))",
            )
        }
    }

    /** v2 -> v3: rewards balance / last-4 on wallet cards (credit-card points tracked like any other balance). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `rewardsBalance` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `rewardsBalanceAsOf` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `last4` TEXT")
        }
    }

    /** v3 -> v4: welcome-bonus/member columns, certificates, credit usage, rotating categories, award watches, transfer bonuses, reminder ledger. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `dateOpenedEpochDay` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `bonusSpendRequiredUsd` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `bonusDeadlineEpochDay` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `bonusSpendToDateUsd` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `bonusEarnedAt` INTEGER")
            db.execSQL("ALTER TABLE `wallet_cards` ADD COLUMN `memberName` TEXT")
            db.execSQL("ALTER TABLE `loyalty_accounts` ADD COLUMN `memberName` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `benefit_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`program` TEXT, " +
                    "`walletCardId` INTEGER, " +
                    "`valueUsd` REAL, " +
                    "`expiresEpochDay` INTEGER, " +
                    "`usedAt` INTEGER, " +
                    "`notes` TEXT, " +
                    "`source` TEXT NOT NULL, " +
                    "`sourceEmailSubject` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `credit_usage` (" +
                    "`walletCardId` INTEGER NOT NULL, " +
                    "`creditLabel` TEXT NOT NULL, " +
                    "`lastUsedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`walletCardId`, `creditLabel`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rotating_categories` (" +
                    "`walletCardId` INTEGER NOT NULL, " +
                    "`quarterKey` TEXT NOT NULL, " +
                    "`categoriesCsv` TEXT NOT NULL, " +
                    "`activated` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`walletCardId`, `quarterKey`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `award_watches` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`program` TEXT, " +
                    "`origin` TEXT, " +
                    "`destination` TEXT, " +
                    "`dateFrom` TEXT, " +
                    "`dateTo` TEXT, " +
                    "`notes` TEXT, " +
                    "`active` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`lastCheckedAt` INTEGER, " +
                    "`lastResult` TEXT, " +
                    "`lastFound` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transfer_bonuses` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fromCurrency` TEXT NOT NULL, " +
                    "`toProgram` TEXT NOT NULL, " +
                    "`bonusPercent` INTEGER NOT NULL, " +
                    "`endsEpochDay` INTEGER, " +
                    "`note` TEXT, " +
                    "`checkedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notified_alerts` (" +
                    "`alertKey` TEXT NOT NULL, " +
                    "`notifiedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`alertKey`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
