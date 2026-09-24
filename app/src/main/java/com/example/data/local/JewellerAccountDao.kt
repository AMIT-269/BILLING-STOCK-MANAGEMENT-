package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.JewellerAccount

@Dao
interface JewellerAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: JewellerAccount)

    @Query("SELECT * FROM jeweller_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): JewellerAccount?

    @Query("SELECT * FROM jeweller_accounts WHERE LOWER(TRIM(jewellerName)) = LOWER(TRIM(:name)) AND (TRIM(mobileNumber) = TRIM(:mobile) OR mobileNumber = :mobile OR mobileNumber LIKE '%' || :mobile) LIMIT 1")
    suspend fun findAccount(name: String, mobile: String): JewellerAccount?

    @Query("SELECT * FROM jeweller_accounts WHERE TRIM(mobileNumber) = TRIM(:mobile) OR mobileNumber = :mobile OR mobileNumber LIKE '%' || :mobile LIMIT 1")
    suspend fun findAccountByMobile(mobile: String): JewellerAccount?

    @Query("SELECT * FROM jeweller_accounts WHERE LOWER(TRIM(jewellerName)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findAccountByName(name: String): JewellerAccount?

    @Query("UPDATE jeweller_accounts SET code4Digit = :newCode WHERE accountId = :accountId")
    suspend fun updateCode(accountId: String, newCode: String)

    @Query("UPDATE jeweller_accounts SET gstNumber = :gst WHERE accountId = :accountId")
    suspend fun updateGst(accountId: String, gst: String)

    @Query("SELECT * FROM jeweller_accounts WHERE UPPER(TRIM(gstNumber)) = UPPER(TRIM(:gst)) LIMIT 1")
    suspend fun findAccountByGst(gst: String): JewellerAccount?

    @Query("SELECT * FROM jeweller_accounts WHERE TRIM(mobileNumber) = TRIM(:mobile) AND UPPER(TRIM(gstNumber)) = UPPER(TRIM(:gst)) LIMIT 1")
    suspend fun findAccountByMobileAndGst(mobile: String, gst: String): JewellerAccount?

    @Query("SELECT * FROM jeweller_accounts")
    suspend fun getAllAccounts(): List<JewellerAccount>

    @Query("DELETE FROM jeweller_accounts WHERE accountId = :accountId")
    suspend fun deleteAccountById(accountId: String)
}
