package com.consensus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.consensus.app.data.local.entity.ExchangeEntity
import com.consensus.app.data.local.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE id = :id")
    fun observe(id: Long): Flow<ThreadEntity?>

    @Insert
    suspend fun insert(thread: ThreadEntity): Long

    @Query("UPDATE threads SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExchangeDao {
    @Query("SELECT * FROM exchanges WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeForThread(threadId: Long): Flow<List<ExchangeEntity>>

    @Query("SELECT * FROM exchanges WHERE threadId = :threadId AND status = 'DONE' ORDER BY createdAt ASC")
    suspend fun completedForThread(threadId: Long): List<ExchangeEntity>

    @Query("SELECT * FROM exchanges WHERE id = :id")
    suspend fun get(id: Long): ExchangeEntity?

    @Insert
    suspend fun insert(exchange: ExchangeEntity): Long

    @Update
    suspend fun update(exchange: ExchangeEntity)

    @Query("UPDATE exchanges SET status = 'ERROR', error = :error WHERE status = 'RUNNING'")
    suspend fun failAllRunning(error: String)

    @Query("DELETE FROM exchanges WHERE id = :id")
    suspend fun delete(id: Long)
}
