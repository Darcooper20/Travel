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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
