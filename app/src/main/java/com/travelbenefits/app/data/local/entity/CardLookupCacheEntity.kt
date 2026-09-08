package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/**
 * Cached result of a live (Anthropic web-search) benefit lookup for a card
 * name not found in the built-in catalog, keyed by a normalized version of
 * the name the user typed so repeat lookups are free.
 */
@Entity(tableName = "card_lookup_cache", primaryKeys = ["normalizedName"])
data class CardLookupCacheEntity(
    val normalizedName: String,
    val resultJson: String,
    val fetchedAtEpochMillis: Long,
)
