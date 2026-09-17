package com.travelbenefits.app.domain

/**
 * Decides whether a string pulled out of an email is a membership number the
 * app can actually save.
 *
 * The distinction that matters is masked versus whole. Loyalty emails very
 * often print something like "Member #: ****4821" or "•••• 1234", and a
 * masked number is not a shortened membership number - it is a different
 * thing entirely. You cannot read it out at a check-in desk, you cannot put
 * it on a booking, and two accounts can easily share the same last four. If
 * one were stored as though it were real, the app would stop asking for the
 * number, show it on the Loyalty screen, and write it into exports, all
 * while holding something unusable. Unknown has to stay unknown.
 *
 * Everything here is pure text handling, deliberately: it is the one part of
 * this path that can be tested without a mailbox or a model.
 */
object MembershipNumber {

    /** Labels an email puts in front of the number, which the model sometimes keeps. */
    private val LABELS = Regex("""^\s*(member(ship)?\s*(number|no\.?|#|id)?|account\s*(number|no\.?|#)?|ff\s*(number|no\.?|#)?|number|no\.?|#)\s*[:#-]?\s*""", RegexOption.IGNORE_CASE)

    /** Three or more masking characters in a row. Real numbers do not contain these runs. */
    private val MASK_RUN = Regex("""[*•●·×✕x_]{3,}|\.{3,}|…""", RegexOption.IGNORE_CASE)

    /** Words an email uses where a number would go when there is no number. */
    private val NOT_A_NUMBER = setOf("n/a", "na", "none", "null", "unknown", "not available", "notprovided", "not provided", "-", "--")

    private const val MIN_SIGNIFICANT = 5
    private const val MAX_LENGTH = 24

    /**
     * The number as it should be stored, or null when the text is missing,
     * masked, or too short to be a real membership number.
     *
     * [MIN_SIGNIFICANT] is set at five alphanumeric characters: the shortest
     * real programme numbers run to six or more, while a masked tail is
     * almost always four. Erring towards rejection is the right bias, because
     * a rejected number leaves the app asking the user for it, and a wrongly
     * accepted one leaves them with a number that silently does not work.
     */
    fun clean(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isBlank()) return null
        if (trimmed.lowercase() in NOT_A_NUMBER) return null
        if (MASK_RUN.containsMatchIn(trimmed)) return null

        val withoutLabel = LABELS.replace(trimmed, "").trim()
        // Internal spacing is how programmes print long numbers; it is not part of the number.
        // No regex here on purpose: the character to strip alongside ordinary
        // whitespace is a non-breaking space, and written literally it is an
        // invisible character sitting inside a pattern. Escaped, it is plain.
        val compact = withoutLabel.filterNot { it.isWhitespace() || it == '\u00A0' }
        if (compact.isEmpty() || compact.length > MAX_LENGTH) return null
        if (compact.lowercase() in NOT_A_NUMBER) return null
        // Letters are fine (several programmes prefix them), but the thing has
        // to be mostly identifier, not prose.
        if (!compact.all { it.isLetterOrDigit() || it == '-' }) return null
        if (compact.count { it.isLetterOrDigit() } < MIN_SIGNIFICANT) return null
        if (compact.none { it.isDigit() }) return null
        return compact
    }
}
