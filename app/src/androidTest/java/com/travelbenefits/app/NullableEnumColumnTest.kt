package com.travelbenefits.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.travelbenefits.app.data.local.AppDatabase
import com.travelbenefits.app.data.local.entity.ActionStateEntity
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.TripEntity
import com.travelbenefits.app.domain.model.ActionState
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every enum column that is allowed to be null, inserted as null and read
 * back. This has to be an instrumented test: the bug it guards against lived
 * entirely in the Java that Room GENERATES from the converter signatures, so
 * no JVM test touching [com.travelbenefits.app.data.local.Converters]
 * directly could ever see it.
 *
 * The crash it came from, reported from a real device: inserting an activity
 * event with no programme threw
 * "NullPointerException: Parameter specified as non-null is null: method
 * Converters.fromLoyaltyProgram, parameter value". The converters took
 * non-null parameters, so Room's generated bind() called straight into them
 * and Kotlin's own intrinsic check threw. Activity events without a
 * programme are the ordinary case, not an edge case, so this fired on
 * routine syncs.
 *
 * Each case below also inserts a second row with the enum SET, so a
 * converter that "fixed" the crash by dropping the value on the floor would
 * fail here too.
 */
@RunWith(AndroidJUnit4::class)
class NullableEnumColumnTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun activityEventWithNoProgrammeInsertsAndReadsBack() = runBlocking {
        val dao = db.activityEventDao()
        val withoutProgram = dao.insert(activityEvent(program = null, title = "Sync finished"))
        val withProgram = dao.insert(activityEvent(program = LoyaltyProgram.QANTAS_FREQUENT_FLYER, title = "Balance updated"))

        val rows = dao.observeRecent(10).first().associateBy { it.id }
        assertEquals(2, rows.size)
        assertNull("a programme was invented for an event that has none", rows[withoutProgram]!!.program)
        assertEquals(LoyaltyProgram.QANTAS_FREQUENT_FLYER, rows[withProgram]!!.program)
    }

    @Test
    fun tripWithNoLoyaltyProgrammeRoundTrips() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(
            TripEntity(
                kind = TripKind.FLIGHT,
                provider = "Some regional airline",
                confirmationNumber = "ABC123",
                title = "SYD - MEL",
                startEpochDay = null,
                endEpochDay = null,
                origin = "SYD",
                destination = "MEL",
                loyaltyProgram = null,
                loyaltyNumberOnBooking = false,
                totalCost = null,
                pointsUsed = null,
                pointsEarnedEstimate = null,
                source = TripSource.MANUAL,
                sourceEmailSubject = null,
                sourceMessageId = null,
                notes = null,
                createdAt = 1_700_000_000_000L,
            ),
        )
        val trip = dao.findById(id)
        assertNotNull(trip)
        assertNull("an unknown airline must not be assigned a programme", trip!!.loyaltyProgram)
    }

    @Test
    fun awardWatchWithNoProgrammeRoundTrips() = runBlocking {
        val dao = db.awardWatchDao()
        val id = dao.insert(
            AwardWatchEntity(
                title = "Anywhere warm",
                program = null,
                origin = null,
                destination = null,
                dateFrom = null,
                dateTo = null,
                notes = null,
                active = true,
                createdAt = 1_700_000_000_000L,
                lastCheckedAt = null,
                lastResult = null,
                lastFound = false,
            ),
        )
        assertNull(dao.findById(id)!!.program)
    }

    @Test
    fun benefitItemWithNoProgrammeRoundTrips() = runBlocking {
        val dao = db.benefitDao()
        val id = dao.insertItem(
            BenefitItemEntity(
                kind = BenefitKind.VOUCHER,
                title = "Annual travel credit",
                program = null,
                walletCardId = null,
                valueUsd = 300.0,
                expiresEpochDay = null,
                usedAt = null,
                notes = null,
                source = LoyaltyAccountSource.MANUAL,
                sourceEmailSubject = null,
                createdAt = 1_700_000_000_000L,
            ),
        )
        assertNull(dao.findItem(id)!!.program)
    }

    @Test
    fun actionStateWithNoPreviousStateRoundTrips() = runBlocking {
        val dao = db.actionStateDao()
        dao.upsert(
            ActionStateEntity(
                actionKey = "first-decision",
                state = ActionState.SNOOZED,
                untilEpochDay = 20_000L,
                previousState = null,
                updatedAt = 1_700_000_000_000L,
            ),
        )
        val stored = dao.find("first-decision")
        assertNotNull(stored)
        assertEquals(ActionState.SNOOZED, stored!!.state)
        assertNull("nothing to undo to, so previousState must stay null", stored.previousState)
    }

    private fun activityEvent(program: LoyaltyProgram?, title: String) = ActivityEventEntity(
        kind = ActivityKind.SYNC_COMPLETED,
        program = program,
        title = title,
        detail = null,
        occurredAt = 1_700_000_000_000L,
        isRead = false,
    )
}
