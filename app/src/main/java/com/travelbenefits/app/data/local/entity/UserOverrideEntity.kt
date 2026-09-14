package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/**
 * Account-specific facts the user corrected or supplied (cap spend to date,
 * authorized-user count, perk values, point valuations, per-account
 * preferences). Keyed by scope + key so syncs never silently overwrite
 * them: sync reads these as the source of truth.
 */
@Entity(tableName = "user_overrides", primaryKeys = ["scope", "overrideKey"])
data class UserOverrideEntity(
    /** e.g. "card:12", "currency:CHASE_UR", "global". */
    val scope: String,
    val overrideKey: String,
    val value: String,
    val updatedAt: Long,
)
