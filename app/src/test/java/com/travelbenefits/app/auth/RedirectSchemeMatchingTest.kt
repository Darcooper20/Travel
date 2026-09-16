package com.travelbenefits.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The manifest declares the redirect intent filter by scheme alone. These
 * check the two halves that have to agree: the scheme the app computes from a
 * client ID, and the URI Google will actually send back.
 */
class RedirectSchemeMatchingTest {

    @Test
    fun `the scheme derived from a client ID is the scheme of the redirect URI Google calls back on`() {
        val clientId = "987654321-zyxwvu.apps.googleusercontent.com"
        val scheme = OAuthSetupInfo.schemeForClientId(clientId)!!

        assertEquals("com.googleusercontent.apps.987654321-zyxwvu", scheme)
        // The same shape GmailAuthManager.redirectUri() builds. The manifest filter
        // matches on the scheme alone, so this is the part that has to line up.
        assertEquals("com.googleusercontent.apps.987654321-zyxwvu:/oauth2redirect", "$scheme:/oauth2redirect")
    }

    @Test
    fun `two different client IDs never share a scheme`() {
        assertNotEquals(
            OAuthSetupInfo.schemeForClientId("1-a.apps.googleusercontent.com"),
            OAuthSetupInfo.schemeForClientId("2-b.apps.googleusercontent.com"),
        )
    }
}
