package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<Bill>)

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :billId AND accountId = :accountId")
    suspend fun deleteBillById(billId: String, accountId: String)

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillById(id: String): Bill?

    @Query("SELECT * FROM bills WHERE accountId = :accountId ORDER BY dateTimestamp DESC")
    fun getAllBillsFlow(accountId: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE accountId = :accountId AND billType = :billType ORDER BY dateTimestamp DESC")
    fun getBillsByTypeFlow(accountId: String, billType: String): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE accountId = :accountId")
    suspend fun getBillCount(accountId: String): Int

    @Query("SELECT * FROM bills WHERE accountId = :accountId")
    suspend fun getAllBillsDirect(accountId: String): List<Bill>

    @Query("DELETE FROM bills WHERE accountId = :accountId")
    suspend fun deleteAllBillsForAccount(accountId: String)
}
