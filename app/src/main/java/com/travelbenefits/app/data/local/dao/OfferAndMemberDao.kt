package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.MemberEntity
import com.travelbenefits.app.data.local.entity.MerchantOfferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantOfferDao {
    @Query("SELECT * FROM merchant_offers ORDER BY expiresEpochDay IS NULL, expiresEpochDay")
    fun observeAll(): Flow<List<MerchantOfferEntity>>

    @Query("SELECT * FROM merchant_offers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MerchantOfferEntity?

    @Insert
    suspend fun insert(offer: MerchantOfferEntity): Long

    @Update
    suspend fun update(offer: MerchantOfferEntity)

    @Query("DELETE FROM merchant_offers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY isOwner DESC, name")
    fun observeAll(): Flow<List<MemberEntity>>

    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: Long)
}
