package com.travelbenefits.app.data

import com.travelbenefits.app.data.repository.SyncReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync summary is the only place the user learns whether the app has
 * actually seen their mailbox. A run that failed must never be able to
 * borrow the wording of a clean one.
 */
class SyncReportTest {

    @Test
    fun `a clean empty run says there was nothing new`() {
        val report = SyncReport(emailsRead = 0)
        assertFalse(report.isPartial)
        assertFalse(report.isBlind)
        assertTrue(report.summary().contains("No new travel or loyalty emails"))
    }

    @Test
    fun `a run that could not search never claims there was nothing new`() {
        val report = SyncReport(emailsRead = 0, searchFailures = 3, failureReason = "timeout")
        assertTrue(report.isPartial)
        assertTrue(report.isBlind)
        val summary = report.summary()
        assertFalse("this is the bug: silence read as all-clear", summary.contains("No new travel or loyalty emails"))
        assertTrue(summary.contains("Couldn't read any email"))
        assertTrue("the cause is actionable, so show it", summary.contains("timeout"))
    }

    @Test
    fun `a partial run reports both what it found and what it missed`() {
        val report = SyncReport(emailsRead = 4, balanceChanges = 1, unreadableEmails = 2, failureReason = "429 rate limited")
        val summary = report.summary()
        assertTrue(summary.contains("1 balance change(s)"))
        assertTrue(summary.contains("2 email(s) couldn't be read"))
        assertTrue(summary.contains("429 rate limited"))
        assertTrue(report.isPartial)
        assertFalse("it did read some mail, so it was not blind", report.isBlind)
    }

    @Test
    fun `a partial run promises the skipped mail will be retried`() {
        // Emails whose batch failed are deliberately kept out of the processed
        // ledger, so this promise has to stay true.
        val summary = SyncReport(emailsRead = 1, fetchFailures = 1).summary()
        assertTrue(summary.contains("nothing was skipped permanently"))
        assertTrue(summary.contains("next sync retries them"))
    }

    @Test
    fun `read some, found nothing, no errors is still a clean result`() {
        val report = SyncReport(emailsRead = 9)
        assertFalse(report.isPartial)
        assertTrue(report.summary().contains("nothing new to record"))
        assertFalse(report.summary().contains("couldn't"))
    }

    @Test
    fun `notable counts drive the found-things wording`() {
        val report = SyncReport(emailsRead = 3, newTrips = 2, certificatesFound = 1)
        assertTrue(report.notableCount > 0)
        val summary = report.summary()
        assertTrue(summary.contains("2 new trip(s)"))
        assertTrue(summary.contains("1 certificate(s)"))
        assertTrue(summary.contains("from 3 email(s)"))
    }
}
