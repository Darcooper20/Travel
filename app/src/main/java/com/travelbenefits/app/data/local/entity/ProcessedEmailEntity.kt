package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/**
 * Gmail message IDs the monitor has already read, so incremental scans never
 * pay to re-extract (or double-record) the same email. Only the opaque ID
 * is stored - no subject or body.
 */
@Entity(tableName = "processed_emails", primaryKeys = ["messageId"])
data class ProcessedEmailEntity(
    val messageId: String,
    val processedAt: Long,
)
