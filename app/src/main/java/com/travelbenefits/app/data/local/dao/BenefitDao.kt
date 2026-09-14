package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.CreditUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenefitDao {
    @Query("SELECT * FROM benefit_items ORDER BY usedAt IS NOT NULL, expiresEpochDay IS NULL, expiresEpochDay ASC")
    fun observeItems(): Flow<List<BenefitItemEntity>>

    @Query("SELECT * FROM benefit_items")
    suspend fun getItems(): List<BenefitItemEntity>

    @Query("SELECT * FROM benefit_items WHERE id = :id LIMIT 1")
    suspend fun findItem(id: Long): BenefitItemEntity?

    @Query("SELECT * FROM benefit_items WHERE title = :title AND (program = :program OR (:program IS NULL AND program IS NULL)) LIMIT 1")
    suspend fun findItemByTitle(title: String, program: String?): BenefitItemEntity?

    @Insert
    suspend fun insertItem(item: BenefitItemEntity): Long

    @Update
    suspend fun updateItem(item: BenefitItemEntity)

    @Query("DELETE FROM benefit_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM credit_usage")
    fun observeCreditUsage(): Flow<List<CreditUsageEntity>>

    @Query("SELECT * FROM credit_usage")
    suspend fun getCreditUsage(): List<CreditUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCreditUsage(usage: CreditUsageEntity)

    @Query("DELETE FROM credit_usage WHERE walletCardId = :walletCardId AND creditLabel = :creditLabel")
    suspend fun clearCreditUsage(walletCardId: Long, creditLabel: String)
}
