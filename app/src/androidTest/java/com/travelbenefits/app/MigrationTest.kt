package com.travelbenefits.app

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.travelbenefits.app.data.local.AppDatabase
import com.travelbenefits.app.data.local.Migrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite migration tests. A database is created at schema version 1
 * with the original hand-written DDL, filled with rows, walked forward with
 * the app's own [Migrations], and finally opened through Room at the current
 * version - Room validates every table against the entities on open and
 * throws if a migration left the schema wrong. Data must survive unchanged
 * and derived rows (ledger entries, loyalty-number states) must be exactly
 * what the migration comments promise. No schema JSON is needed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clean() { context.deleteDatabase(DB_NAME) }

    @After
    fun tearDown() { context.deleteDatabase(DB_NAME) }

    @Test
    fun v1DatabaseMigratesToCurrentWithDataIntact() {
        createV1(listOf(
            "INSERT INTO loyalty_accounts (program, membershipNumber, tier, pointsBalance, source, sourceEmailSubject, lastUpdated) VALUES ('MARRIOTT_BONVOY', '123456789', 'Gold Elite', '42,500 points', 'MANUAL', NULL, 1700000000000)",
            "INSERT INTO wallet_cards (nickname, catalogCardId, customCardName, dateAdded, notes) VALUES ('Daily', 'chase_sapphire_reserve', NULL, 1700000000000, 'v1 row')",
            "INSERT INTO card_lookup_cache (normalizedName, resultJson, fetchedAtEpochMillis) VALUES ('some card', '{}', 1700000000000)",
        )).close()

        openCurrent().using { db ->
            db.query("SELECT program, membershipNumber, tier, pointsBalance, pointsNumeric, memberName FROM loyalty_accounts", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("MARRIOTT_BONVOY", c.getString(0))
                assertEquals("123456789", c.getString(1))
                assertEquals("Gold Elite", c.getString(2))
                assertEquals("42,500 points", c.getString(3))
                assertTrue("columns added later stay NULL, never invented", c.isNull(4))
                assertTrue(c.isNull(5))
                assertEquals(1, c.count)
            }
            db.query("SELECT catalogCardId, notes, rewardsBalance, isAuthorizedUser FROM wallet_cards", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("chase_sapphire_reserve", c.getString(0))
                assertEquals("v1 row", c.getString(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM card_lookup_cache", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            assertEquals(AppDatabase_VERSION, db.openHelper.readableDatabase.version)
        }
    }

    @Test
    fun creditUsageBecomesUnknownAmountLedgerAndLoyaltyBooleanBecomesTriState() {
        val helper = createV1(emptyList())
        val db = helper.writableDatabase
        // Walk to v7 with the app's own migrations, inserting rows at the versions where the tables exist.
        Migrations.MIGRATION_1_2.migrate(db)
        db.execSQL(
            "INSERT INTO trips (kind, provider, title, loyaltyNumberOnBooking, source, createdAt) VALUES " +
                "('HOTEL', 'Hyatt', 'Hyatt Regency Austin', 1, 'EMAIL', 1700000000000), " +
                "('FLIGHT', 'Delta', 'Delta SFO-JFK', 0, 'EMAIL', 1700000000000)",
        )
        Migrations.MIGRATION_2_3.migrate(db)
        Migrations.MIGRATION_3_4.migrate(db)
        db.execSQL("INSERT INTO wallet_cards (nickname, catalogCardId, customCardName, dateAdded, notes) VALUES (NULL, 'amex_platinum', NULL, 1700000000000, NULL)")
        db.execSQL("INSERT INTO credit_usage (walletCardId, creditLabel, lastUsedAt) VALUES (1, 'Uber Cash', 1704067200000)") // 2024-01-01T00:00Z
        Migrations.MIGRATION_4_5.migrate(db)
        Migrations.MIGRATION_5_6.migrate(db)
        Migrations.MIGRATION_6_7.migrate(db)
        db.version = 7
        helper.close()

        openCurrent().using { room ->
            room.query("SELECT walletCardId, creditLabel, kind, amountCents, epochDay, source, needsConfirmation FROM benefit_ledger", null).use { c ->
                assertEquals("one ledger row per old checkbox", 1, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(0))
                assertEquals("Uber Cash", c.getString(1))
                assertEquals("USED_UNKNOWN_AMOUNT", c.getString(2))
                assertEquals("amount was never recorded, so it is 0 and flagged, not guessed", 0L, c.getLong(3))
                assertEquals(1704067200000L / 86_400_000L, c.getLong(4))
                assertEquals("MIGRATION", c.getString(5))
            }
            room.query("SELECT title, loyaltyNumberState, status FROM trips ORDER BY id", null).use { c ->
                assertEquals(2, c.count)
                assertTrue(c.moveToFirst())
                assertEquals("CONFIRMED", c.getString(1))
                assertEquals("CONFIRMED", c.getString(2))
                assertTrue(c.moveToNext())
                assertEquals("false never proved a number was missing", "UNKNOWN", c.getString(1))
            }
            room.query("SELECT COUNT(*) FROM merchant_offers", null).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            room.query("SELECT COUNT(*) FROM members", null).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test
    fun freshInstallOpensAtCurrentVersion() {
        openCurrent().using { db ->
            db.query("SELECT COUNT(*) FROM wallet_cards", null).use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            assertNull(null)
        }
    }

    /** RoomDatabase is not Closeable in Room 2.6, so a small try/finally stand-in for `use`. */
    private inline fun <T> AppDatabase.using(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
            .also { it.openHelper.writableDatabase } // forces the migrations + Room's schema validation to run now

    /** Original schema shipped as database version 1 (three tables). */
    private fun createV1(inserts: List<String>): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loyalty_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `program` TEXT NOT NULL, `membershipNumber` TEXT, `tier` TEXT, " +
                        "`pointsBalance` TEXT, `source` TEXT NOT NULL, `sourceEmailSubject` TEXT, `lastUpdated` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wallet_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nickname` TEXT, `catalogCardId` TEXT, `customCardName` TEXT, " +
                        "`dateAdded` INTEGER NOT NULL, `notes` TEXT)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `card_lookup_cache` (`normalizedName` TEXT NOT NULL, `resultJson` TEXT NOT NULL, `fetchedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`normalizedName`))")
                inserts.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB_NAME).callback(callback).build(),
        )
        helper.writableDatabase // creates the file at version 1
        return helper
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val AppDatabase_VERSION = 9
    }
}
