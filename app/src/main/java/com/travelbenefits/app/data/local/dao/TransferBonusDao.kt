package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.TransferBonusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferBonusDao {
    @Query("SELECT * FROM transfer_bonuses ORDER BY bonusPercent DESC")
    fun observeAll(): Flow<List<TransferBonusEntity>>

    @Query("SELECT * FROM transfer_bonuses")
    suspend fun getAll(): List<TransferBonusEntity>

    @Query("SELECT MAX(checkedAt) FROM transfer_bonuses")
    suspend fun lastCheckedAt(): Long?

    @Insert
    suspend fun insertAll(bonuses: List<TransferBonusEntity>)

    @Query("DELETE FROM transfer_bonuses")
    suspend fun clear()
}
