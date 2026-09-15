package com.travelbenefits.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Google returns the sign-in result to the reversed client ID. The app has to
 * compute that exactly, because a build listening on any other scheme silently
 * cannot finish sign-in.
 */
class OAuthSetupInfoTest {

    @Test
    fun `a client ID reverses into the scheme Google will call back on`() {
        assertEquals(
            "com.googleusercontent.apps.123456789-abcdef",
            OAuthSetupInfo.schemeForClientId("123456789-abcdef.apps.googleusercontent.com"),
        )
    }

    @Test
    fun `surrounding whitespace from a paste is tolerated`() {
        assertEquals(
            "com.googleusercontent.apps.42-xyz",
            OAuthSetupInfo.schemeForClientId("  42-xyz.apps.googleusercontent.com\n"),
        )
    }

    @Test
    fun `anything that is not a Google client ID yields no scheme`() {
        // Better to say nothing than to invent a scheme from a truncated paste.
        assertNull(OAuthSetupInfo.schemeForClientId(null))
        assertNull(OAuthSetupInfo.schemeForClientId(""))
        assertNull(OAuthSetupInfo.schemeForClientId("123456789-abcdef"))
        assertNull(OAuthSetupInfo.schemeForClientId(".apps.googleusercontent.com"))
        assertNull(OAuthSetupInfo.schemeForClientId("https://example.com"))
    }
}
