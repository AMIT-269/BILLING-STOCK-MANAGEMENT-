package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jeweller_accounts")
data class JewellerAccount(
    @PrimaryKey
    val accountId: String,
    val jewellerName: String,
    val mobileNumber: String,
    val code4Digit: String,
    val gstNumber: String = "",
    val isLicensed: Boolean = true,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)

