package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/** The user's record of a card's rotating/chosen 5% categories for one quarter. */
@Entity(tableName = "rotating_categories", primaryKeys = ["walletCardId", "quarterKey"])
data class RotatingCategoryEntity(
    val walletCardId: Long,
    /** e.g. "2026-Q4". */
    val quarterKey: String,
    /** Comma-separated SpendingCategory names. */
    val categoriesCsv: String,
    val activated: Boolean,
)
