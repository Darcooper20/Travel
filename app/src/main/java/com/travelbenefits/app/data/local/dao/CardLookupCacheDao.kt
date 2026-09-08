package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.CardLookupCacheEntity

@Dao
interface CardLookupCacheDao {
    @Query("SELECT * FROM card_lookup_cache WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun find(normalizedName: String): CardLookupCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CardLookupCacheEntity)
}
