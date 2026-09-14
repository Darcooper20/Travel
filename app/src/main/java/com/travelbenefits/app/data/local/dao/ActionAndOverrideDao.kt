package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.ActionStateEntity
import com.travelbenefits.app.data.local.entity.UserOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionStateDao {
    @Query("SELECT * FROM action_states")
    fun observeAll(): Flow<List<ActionStateEntity>>

    @Query("SELECT * FROM action_states WHERE actionKey = :key LIMIT 1")
    suspend fun find(key: String): ActionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ActionStateEntity)

    @Query("DELETE FROM action_states WHERE actionKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM action_states WHERE updatedAt < :olderThan")
    suspend fun prune(olderThan: Long)
}

@Dao
interface UserOverrideDao {
    @Query("SELECT * FROM user_overrides")
    fun observeAll(): Flow<List<UserOverrideEntity>>

    @Query("SELECT * FROM user_overrides")
    suspend fun getAll(): List<UserOverrideEntity>

    @Query("SELECT * FROM user_overrides WHERE scope = :scope")
    suspend fun getForScope(scope: String): List<UserOverrideEntity>

    @Query("SELECT * FROM user_overrides WHERE scope = :scope AND overrideKey = :key LIMIT 1")
    suspend fun find(scope: String, key: String): UserOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: UserOverrideEntity)

    @Query("DELETE FROM user_overrides WHERE scope = :scope AND overrideKey = :key")
    suspend fun delete(scope: String, key: String)
}
