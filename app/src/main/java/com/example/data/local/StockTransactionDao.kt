package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StockTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: StockTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txs: List<StockTransaction>)

    @Update
    suspend fun updateTransaction(tx: StockTransaction)

    @Delete
    suspend fun deleteTransaction(tx: StockTransaction)

    @Query("DELETE FROM stock_transactions WHERE id = :id AND accountId = :accountId")
    suspend fun deleteById(id: String, accountId: String)

    @Query("DELETE FROM stock_transactions WHERE linkedBillId = :billId AND accountId = :accountId")
    suspend fun deleteByLinkedBillId(billId: String, accountId: String)

    @Query("SELECT * FROM stock_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StockTransaction?

    @Query("SELECT * FROM stock_transactions WHERE accountId = :accountId AND category = :category ORDER BY dateTimestamp DESC")
    fun getTransactionsByCategoryFlow(accountId: String, category: String): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE accountId = :accountId ORDER BY dateTimestamp DESC")
    fun getAllTransactionsFlow(accountId: String): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE accountId = :accountId")
    suspend fun getAllTransactionsDirect(accountId: String): List<StockTransaction>

    @Query("SELECT COALESCE(SUM(quantityOrAmount), 0.0) FROM stock_transactions WHERE accountId = :accountId AND category = :category AND type = 'CREDIT'")
    suspend fun getTotalCredit(accountId: String, category: String): Double

    @Query("SELECT COALESCE(SUM(quantityOrAmount), 0.0) FROM stock_transactions WHERE accountId = :accountId AND category = :category AND type = 'DEBIT'")
    suspend fun getTotalDebit(accountId: String, category: String): Double

    @Query("DELETE FROM stock_transactions WHERE accountId = :accountId")
    suspend fun deleteAllTransactionsForAccount(accountId: String)
}
