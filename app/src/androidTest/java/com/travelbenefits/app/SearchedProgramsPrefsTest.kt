package com.travelbenefits.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.domain.model.LoyaltyProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The set of programmes already searched over the full lookback window is what
 * lets a programme added by an app update be back-filled: its mail all
 * predates the sync watermark, so searching "since last sync" would find
 * nothing and the programme would look absent from the mailbox.
 *
 * Instrumented because it is about what actually survives in
 * SharedPreferences, which is the only part of this that can go wrong.
 */
@RunWith(AndroidJUnit4::class)
class SearchedProgramsPrefsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun nothingIsSearchedUntilASyncSaysSo() {
        assertEquals(emptySet<String>(), AppPrefs(context).searchedPrograms.value)
    }

    @Test
    fun theSearchedSetSurvivesARestart() {
        val names = setOf(LoyaltyProgram.QANTAS_FREQUENT_FLYER.name, LoyaltyProgram.BA_EXECUTIVE_CLUB.name)
        AppPrefs(context).recordSearchedPrograms(names)
        // A fresh instance stands in for the next process.
        assertEquals(names, AppPrefs(context).searchedPrograms.value)
    }

    @Test
    fun aFullRescanForgetsWhatWasSearched() {
        val prefs = AppPrefs(context)
        prefs.recordSearchedPrograms(setOf(LoyaltyProgram.QANTAS_FREQUENT_FLYER.name))
        prefs.recordSync(completedAt = 1_700_000_000_000L, summary = "done")
        assertTrue(prefs.searchedPrograms.value.isNotEmpty())

        prefs.resetSyncWatermark()

        // Both have to go: the searched set only means anything relative to a
        // watermark, so keeping it would make a "full re-scan" skip exactly the
        // programmes it was asked to re-scan.
        assertEquals(0L, prefs.lastSyncAt.value)
        assertEquals(emptySet<String>(), prefs.searchedPrograms.value)
        assertEquals(emptySet<String>(), AppPrefs(context).searchedPrograms.value)
    }
}
