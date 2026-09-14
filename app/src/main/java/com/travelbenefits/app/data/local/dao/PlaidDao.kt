package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.PlaidAccountEntity
import com.travelbenefits.app.data.local.entity.PlaidItemEntity
import com.travelbenefits.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaidDao {
    @Query("SELECT * FROM plaid_items ORDER BY createdAt")
    fun observeItems(): Flow<List<PlaidItemEntity>>

    @Query("SELECT * FROM plaid_items ORDER BY createdAt")
    suspend fun getItems(): List<PlaidItemEntity>

    @Query("SELECT * FROM plaid_items WHERE itemId = :itemId LIMIT 1")
    suspend fun findItem(itemId: String): PlaidItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: PlaidItemEntity)

    @Update
    suspend fun updateItem(item: PlaidItemEntity)

    @Query("DELETE FROM plaid_items WHERE itemId = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("SELECT * FROM plaid_accounts ORDER BY itemId, name")
    fun observeAccounts(): Flow<List<PlaidAccountEntity>>

    @Query("SELECT * FROM plaid_accounts")
    suspend fun getAccounts(): List<PlaidAccountEntity>

    @Query("SELECT * FROM plaid_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun findAccount(accountId: String): PlaidAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<PlaidAccountEntity>)

    @Query("UPDATE plaid_accounts SET walletCardId = :walletCardId WHERE accountId = :accountId")
    suspend fun mapAccount(accountId: String, walletCardId: Long?)

    @Query("DELETE FROM plaid_accounts WHERE itemId = :itemId")
    suspend fun deleteAccountsForItem(itemId: String)

    @Query("SELECT * FROM transactions WHERE epochDay >= :fromEpochDay ORDER BY epochDay DESC")
    fun observeTransactionsSince(fromEpochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE epochDay >= :fromEpochDay")
    suspend fun getTransactionsSince(fromEpochDay: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTransactionCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE transactionId IN (:ids)")
    suspend fun deleteTransactions(ids: List<String>)

    @Query("DELETE FROM transactions WHERE accountId IN (SELECT accountId FROM plaid_accounts WHERE itemId = :itemId)")
    suspend fun deleteTransactionsForItem(itemId: String)

    @Query("DELETE FROM transactions WHERE epochDay < :olderThanEpochDay")
    suspend fun pruneTransactions(olderThanEpochDay: Long)
}
