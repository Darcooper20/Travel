package com.travelbenefits.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.openid.appauth.RedirectUriReceiverActivity
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Returning from Google sign-in crashed the app on a real device with
 * "You need to use a Theme.AppCompat theme (or descendant) with this activity".
 *
 * AppAuth's [RedirectUriReceiverActivity] is an AppCompatActivity. The app's own
 * theme is the platform Material theme, which is not an AppCompat descendant, and
 * the manifest replaces AppAuth's declaration of that activity, so it inherited
 * the wrong theme. Nothing caught it because no test had ever started that
 * activity: the whole emulator suite exercised the app's own screens only.
 */
@RunWith(AndroidJUnit4::class)
class RedirectActivityThemeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val component get() = ComponentName(context, RedirectUriReceiverActivity::class.java)

    @Test
    fun redirectActivityDeclaresAnAppCompatTheme() {
        val info = context.packageManager.getActivityInfo(component, 0)
        assertNotEquals(
            "the redirect activity must not fall back to the application theme",
            0,
            info.themeResource,
        )
        val theme = context.resources.newTheme().apply { applyStyle(info.themeResource, true) }
        // windowActionBar is declared by AppCompat, so it only resolves under a
        // Theme.AppCompat descendant. This is the exact condition AppCompat checks.
        assertTrue(
            "the redirect activity's theme must descend from Theme.AppCompat",
            theme.resolveAttribute(androidx.appcompat.R.attr.windowActionBar, TypedValue(), true),
        )
    }

    @Test
    fun redirectActivityStartsWithoutCrashing() {
        // The real reproduction: the crash happened in onCreate's super call,
        // before any AppAuth logic ran, so simply starting it is enough.
        val intent = Intent(context, RedirectUriReceiverActivity::class.java).apply {
            data = Uri.parse("${BuildConfig.APPAUTH_REDIRECT_SCHEME}:/oauth2redirect?state=test&code=test")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        // It finishes itself immediately after forwarding the redirect; reaching
        // this line at all means it started, which is what used to fail.
        instrumentation.runOnMainSync { if (!activity.isFinishing) activity.finish() }
    }
}
