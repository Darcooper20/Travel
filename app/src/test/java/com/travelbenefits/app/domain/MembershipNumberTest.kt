package com.travelbenefits.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule under test: a masked number is not a short membership number, it is
 * a different thing, and storing one would be worse than storing nothing. If
 * the app saves "****4821" it stops asking for the real number, shows the
 * useless one on the Loyalty screen, and writes it into exports.
 */
class MembershipNumberTest {

    @Test
    fun `a plain number is kept as-is`() {
        assertEquals("1234567890", MembershipNumber.clean("1234567890"))
        assertEquals("XY123456", MembershipNumber.clean("XY123456"))
        assertEquals("123-456-789", MembershipNumber.clean("123-456-789"))
    }

    @Test
    fun `spacing programmes print for readability is not part of the number`() {
        assertEquals("1234567890", MembershipNumber.clean("  1234 5678 90 "))
        assertEquals("BA12345678", MembershipNumber.clean("BA 1234 5678"))
    }

    @Test
    fun `a label the model left attached is stripped`() {
        assertEquals("1234567890", MembershipNumber.clean("Member #: 1234567890"))
        assertEquals("1234567890", MembershipNumber.clean("Membership number 1234567890"))
        assertEquals("1234567890", MembershipNumber.clean("#1234567890"))
        assertEquals("998877665", MembershipNumber.clean("Account No. 998877665"))
    }

    @Test
    fun `a masked number is refused, in every disguise`() {
        listOf(
            "****4821",
            "•••• 4821",
            "●●●●1234",
            "xxxx1234",
            "XXXXXX7788",
            "...4821",
            "…4821",
            "Member #: ****4821",
            "1234****",
            "12****89",
        ).forEach { assertNull("stored a masked number: $it", MembershipNumber.clean(it)) }
    }

    @Test
    fun `text where a number should be is refused`() {
        listOf(null, "", "   ", "n/a", "N/A", "none", "None", "unknown", "-", "not provided")
            .forEach { assertNull("accepted '$it' as a number", MembershipNumber.clean(it)) }
    }

    @Test
    fun `something too short to be a membership number is refused`() {
        // Four characters is the length of a masked tail, not of a real number.
        assertNull(MembershipNumber.clean("4821"))
        assertNull(MembershipNumber.clean("12"))
        assertEquals("48213", MembershipNumber.clean("48213"))
    }

    @Test
    fun `prose and absurd lengths are refused`() {
        assertNull(MembershipNumber.clean("see your account online"))
        assertNull(MembershipNumber.clean("ABCDEFGH"))
        assertNull(MembershipNumber.clean("1".repeat(25)))
        assertEquals("1".repeat(24), MembershipNumber.clean("1".repeat(24)))
    }

    @Test
    fun `a cleaned number never still contains a mask`() {
        // Belt and braces: whatever survives clean() is what gets stored and
        // shown, so it must never be something that came in masked.
        listOf("****4821", "•••• 4821", "xxxx1234", "Member: ...4821").forEach {
            val cleaned = MembershipNumber.clean(it)
            assertNull("$it survived cleaning as $cleaned", cleaned)
        }
    }
}
