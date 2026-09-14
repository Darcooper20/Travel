package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import com.travelbenefits.app.domain.model.ActionState

/** User decision on an action item (snooze/dismiss/complete), keyed by the item's stable key. */
@Entity(tableName = "action_states", primaryKeys = ["actionKey"])
data class ActionStateEntity(
    val actionKey: String,
    val state: ActionState,
    /** Epoch day the snooze ends; null for dismiss/complete. */
    val untilEpochDay: Long?,
    /** Previous state, for undo. */
    val previousState: ActionState?,
    val updatedAt: Long,
)
