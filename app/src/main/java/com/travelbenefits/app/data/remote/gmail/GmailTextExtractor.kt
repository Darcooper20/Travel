package com.travelbenefits.app.data.remote.gmail

import android.util.Base64

/** Pulls a plain-text body out of a Gmail message's MIME part tree. */
object GmailTextExtractor {

    fun extractBodyText(detail: GmailMessageDetail, maxChars: Int = 4000): String {
        val payload = detail.payload ?: return detail.snippet.orEmpty()

        findPartByMimeType(payload, "text/plain")?.let { part ->
            val decoded = decode(part.body?.data)
            if (decoded.isNotBlank()) return decoded.take(maxChars)
        }

        findPartByMimeType(payload, "text/html")?.let { part ->
            val decoded = decode(part.body?.data)
            if (decoded.isNotBlank()) return stripHtml(decoded).take(maxChars)
        }

        return detail.snippet.orEmpty().take(maxChars)
    }

    private fun findPartByMimeType(part: GmailMessagePart, mimeType: String): GmailMessagePart? {
        if (part.mimeType == mimeType && part.body?.data != null) return part
        for (child in part.parts) {
            findPartByMimeType(child, mimeType)?.let { return it }
        }
        return null
    }

    private fun decode(base64UrlData: String?): String {
        if (base64UrlData.isNullOrBlank()) return ""
        return try {
            String(Base64.decode(base64UrlData, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            ""
        }
    }

    private val TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private fun stripHtml(html: String): String {
        val withoutTags = html
            .replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
            .replace(TAG_REGEX, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return withoutTags.replace(WHITESPACE_REGEX, " ").trim()
    }
}
