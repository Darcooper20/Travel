package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.domain.model.HotelProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface LoyaltyAccountDao {
    @Query("SELECT * FROM loyalty_accounts ORDER BY program ASC")
    fun observeAll(): Flow<List<LoyaltyAccountEntity>>

    @Query("SELECT * FROM loyalty_accounts WHERE program = :program LIMIT 1")
    suspend fun findByProgram(program: HotelProgram): LoyaltyAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: LoyaltyAccountEntity): Long

    @Update
    suspend fun update(account: LoyaltyAccountEntity)

    @Delete
    suspend fun delete(account: LoyaltyAccountEntity)

    @Query("DELETE FROM loyalty_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
