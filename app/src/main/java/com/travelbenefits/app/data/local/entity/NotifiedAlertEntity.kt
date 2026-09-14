package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/** Ledger of reminder keys already pushed as notifications, so each fires once. */
@Entity(tableName = "notified_alerts", primaryKeys = ["alertKey"])
data class NotifiedAlertEntity(
    val alertKey: String,
    val notifiedAt: Long,
)
