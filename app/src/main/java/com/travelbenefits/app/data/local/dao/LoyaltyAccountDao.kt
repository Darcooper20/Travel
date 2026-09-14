package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.LoyaltyAccountEntity
import com.travelbenefits.app.domain.model.LoyaltyProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface LoyaltyAccountDao {
    @Query("SELECT * FROM loyalty_accounts ORDER BY program ASC")
    fun observeAll(): Flow<List<LoyaltyAccountEntity>>

    /** The app owner's account for a program (household members' accounts are looked up with [findByProgramAndMember]). */
    @Query("SELECT * FROM loyalty_accounts WHERE program = :program AND memberName IS NULL LIMIT 1")
    suspend fun findByProgram(program: LoyaltyProgram): LoyaltyAccountEntity?

    @Query("SELECT * FROM loyalty_accounts WHERE program = :program AND memberName = :memberName LIMIT 1")
    suspend fun findByProgramAndMember(program: LoyaltyProgram, memberName: String): LoyaltyAccountEntity?

    @Query("SELECT * FROM loyalty_accounts")
    suspend fun getAll(): List<LoyaltyAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: LoyaltyAccountEntity): Long

    @Update
    suspend fun update(account: LoyaltyAccountEntity)

    @Delete
    suspend fun delete(account: LoyaltyAccountEntity)

    @Query("DELETE FROM loyalty_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
