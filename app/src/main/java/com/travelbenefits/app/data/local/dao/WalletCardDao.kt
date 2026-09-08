package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletCardDao {
    @Query("SELECT * FROM wallet_cards ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<WalletCardEntity>>

    @Query("SELECT * FROM wallet_cards ORDER BY dateAdded DESC")
    suspend fun getAll(): List<WalletCardEntity>

    @Insert
    suspend fun insert(card: WalletCardEntity): Long

    @Delete
    suspend fun delete(card: WalletCardEntity)

    @Query("DELETE FROM wallet_cards WHERE id = :id")
    suspend fun deleteById(id: Long)
}
