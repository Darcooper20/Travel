package com.travelbenefits.app.data.remote.gmail

import kotlinx.serialization.Serializable

@Serializable
data class GmailListResponse(
    val messages: List<GmailMessageRef> = emptyList(),
    val resultSizeEstimate: Int = 0,
)

@Serializable
data class GmailMessageRef(
    val id: String,
    val threadId: String,
)

@Serializable
data class GmailMessageDetail(
    val id: String,
    val snippet: String? = null,
    val payload: GmailMessagePart? = null,
)

@Serializable
data class GmailMessagePart(
    val mimeType: String? = null,
    val filename: String? = null,
    val headers: List<GmailHeader> = emptyList(),
    val body: GmailMessageBody? = null,
    val parts: List<GmailMessagePart> = emptyList(),
)

@Serializable
data class GmailHeader(
    val name: String,
    val value: String,
)

@Serializable
data class GmailMessageBody(
    val size: Int = 0,
    val data: String? = null,
)

fun GmailMessagePart.headerValue(name: String): String? =
    headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
