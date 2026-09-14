package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.BenefitLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenefitLedgerDao {
    @Query("SELECT * FROM benefit_ledger ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<BenefitLedgerEntity>>

    @Query("SELECT * FROM benefit_ledger")
    suspend fun getAll(): List<BenefitLedgerEntity>

    @Query("SELECT * FROM benefit_ledger WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BenefitLedgerEntity?

    @Query("SELECT * FROM benefit_ledger WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findByTransaction(transactionId: String): BenefitLedgerEntity?

    @Insert
    suspend fun insert(entry: BenefitLedgerEntity): Long

    @Update
    suspend fun update(entry: BenefitLedgerEntity)

    @Query("DELETE FROM benefit_ledger WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM benefit_ledger WHERE walletCardId = :walletCardId AND creditLabel = :creditLabel AND kind = 'USED_UNKNOWN_AMOUNT' AND epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun clearUnknownUsage(walletCardId: Long, creditLabel: String, fromEpochDay: Long, toEpochDay: Long)
}
