package com.travelbenefits.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.travelbenefits.app.ui.onboarding.OnboardingText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch smoke test on a real emulator: first launch shows the guided setup,
 * finishing it lands in the app, and every bottom tab plus Settings renders
 * without crashing on an empty database. It deliberately makes no network
 * call and needs no key: an empty wallet must be a fully working state.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun resetOnboarding() {
        // The app process (and its AppPrefs singleton) survives between tests; AppPrefs listens for
        // this change, so clearing the file resets the onboarding flag regardless of test order.
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() { scenario?.close() }

    @Test
    fun firstLaunchShowsSetupGuideThenAppNavigates() {
        waitForText(OnboardingText.TITLE_1)
        // Buttons sit under scrollable content that is taller than a phone screen, so scroll before tapping.
        compose.onNodeWithText("Next").performScrollTo().performClick()
        waitForText(OnboardingText.TITLE_2)
        compose.onNodeWithText("Next").performScrollTo().performClick()
        waitForText(OnboardingText.TITLE_3)
        compose.onNodeWithText(OnboardingText.BUTTON_FINISH).performScrollTo().performClick()

        // Default choice is manual entry, which opens the Cards tab (a deferred navigation, so wait
        // for the tab to be *selected*, not merely present); then walk every tab.
        waitForTabSelected("Cards")
        waitForText("Your wallet") // the screen itself, not just the tab state, so the transition has settled
        walkTabs("Home", "Loyalty", "Trips", "Maximize", "Cards", "Home")
        waitForText("Data sources")
        compose.onNodeWithText("Data sources").assertIsDisplayed()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Set up connections in Settings (optional)"))
        compose.onAllNodes(hasText("Set up connections in Settings (optional)") and hasClickAction()).onFirst().performClick()
        waitForText("Connections")
        // A detail screen sits above Home; the Home tab must still return there.
        walkTabs("Home")
        waitForText("Data sources")
    }

    @Test
    fun survivesRecreationWhileSettingsIsOpen() {
        // Backgrounding the app during Gmail sign-in hands the foreground to the
        // browser, and Android is free to tear the activity down and rebuild it.
        // This covers the rebuild half of that. It cannot simulate the process
        // being killed outright, which is the other half.
        waitForText(OnboardingText.TITLE_1)
        compose.onNodeWithText(OnboardingText.BUTTON_SKIP).performScrollTo().performClick()
        waitForText("Data sources")

        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Set up connections in Settings (optional)"))
        compose.onAllNodes(hasText("Set up connections in Settings (optional)") and hasClickAction()).onFirst().performClick()
        waitForText("Connections")

        // Opens the section holding the OAuth values, which reads this build's
        // signing certificate through PackageManager on first composition.
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Advanced configuration (keys, OAuth client, Plaid backend)"))
        compose.onNodeWithText("Advanced configuration (keys, OAuth client, Plaid backend)").performClick()
        waitForText("Advanced configuration")

        scenario!!.recreate()
        compose.waitForIdle()
        waitForText("Connections")

        // A round trip through the stopped state, as backgrounding does.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        waitForText("Connections")
    }

    @Test
    fun skipSetupGoesStraightToHome() {
        waitForText(OnboardingText.TITLE_1)
        compose.onNodeWithText(OnboardingText.BUTTON_SKIP).performScrollTo().performClick()
        waitForText("Data sources")
        waitForTabSelected("Home")
        walkTabs("Loyalty", "Home")
    }

    private fun walkTabs(vararg tabs: String) {
        tabs.forEach { tab ->
            compose.onAllNodes(hasText(tab) and isSelectable()).onFirst().performClick()
            compose.waitForIdle()
            val selectedFirstTime = runCatching { compose.waitUntil(5_000) { isTabSelected(tab) } }.isSuccess
            if (!selectedFirstTime) {
                // One retry, as a person would; the final assertion still fails if the bar is really broken.
                compose.onAllNodes(hasText(tab) and isSelectable()).onFirst().performClick()
                compose.waitForIdle()
                waitFor("tab '$tab' to be selected after a second tap (first tap was ignored)") { isTabSelected(tab) }
            }
        }
    }

    private fun isTabSelected(label: String) = compose.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = waitFor("text '$text'") { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTabSelected(label: String) = waitFor("tab '$label' to be selected") { isTabSelected(label) }

    /** waitUntil with the semantics tree in the failure message, so a CI log says what was on screen. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis = 15_000, condition = condition)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $what. On screen:\n" + compose.onRoot(useUnmergedTree = false).printToString(maxDepth = 12), e)
        }
    }
}
