package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.RotatingCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RotatingCategoryDao {
    @Query("SELECT * FROM rotating_categories")
    fun observeAll(): Flow<List<RotatingCategoryEntity>>

    @Query("SELECT * FROM rotating_categories")
    suspend fun getAll(): List<RotatingCategoryEntity>

    @Query("SELECT * FROM rotating_categories WHERE walletCardId = :walletCardId AND quarterKey = :quarterKey LIMIT 1")
    suspend fun find(walletCardId: Long, quarterKey: String): RotatingCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RotatingCategoryEntity)

    @Query("DELETE FROM rotating_categories WHERE walletCardId = :walletCardId")
    suspend fun deleteForCard(walletCardId: Long)
}
